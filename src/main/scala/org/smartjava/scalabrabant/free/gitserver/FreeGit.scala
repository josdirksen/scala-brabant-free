package org.smartjava.scalabrabant.free.gitserver

import java.util.Date

import net.caoticode.buhtig.Buhtig
import net.caoticode.buhtig.Converters.JSON
import net.caoticode.buhtig.Converters._
import org.json4s.DefaultFormats
import org.slf4j.LoggerFactory
import org.smartjava.scalabrabant.free.gitserver.AST.{Metrics, GitService}
import org.smartjava.scalabrabant.free.gitserver.interpreters.MetricsInterpreter
import org.smartjava.scalabrabant.free.gitserver.interpreters.GitHubInterpret

import scala.concurrent.{Await, Future}
import scala.language.higherKinds
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global
import scalaz._
import Scalaz._


object CoProductUtils {

  def liftI[F[_], G[_], A](fa: F[A])(implicit I: Inject[F, G]) : Free[G, A] = Free.liftF(I.inj(fa))

  def or[F[_], G[_], H[_]](f: F ~> H, g: G ~> H): ({type cp[α]=Coproduct[F,G,α]})#cp ~> H =

    new NaturalTransformation[({type cp[α]=Coproduct[F,G,α]})#cp,H] {
      def apply[A](fa: Coproduct[F,G,A]): H[A] = fa.run match {
        case -\/(ff) ⇒ f(ff)
        case \/-(gg) ⇒ g(gg)
      }
    }
}

object domain {
  // simple domain model, which described the entities in our domain.
  case class Profile(username: String, fullName: String, email: String)
  case class Project(name: String, description: String)
  case class Repository(name: String, location: String)
}


object AST {

  import domain._
  import CoProductUtils._

  // the AST which describes the operation we want to support. These should
  // be small composable steps. Note we don't do error handling, session
  // management, transaction managent or anything here. Just the core
  // components needed to build our program
  sealed trait GitService[A]
  case class GetProfile() extends GitService[Profile]
  case class GetProjects() extends GitService[List[Project]]
  case class GetRepositories(project: Project) extends GitService[List[Repository]]
  case class GetProject(projectName: String) extends GitService[Project]

  // Create some helper functions to lift our case classes into a Free monad. This avoids dirtying our
  // for loops with lifT codes. We could also create an implicit function for this, which does this automatically,
  // but that requires a lot of Scalaz and type magic.
  class GitServices[F[_]](implicit I: Inject[GitService,F]) {
    def getProfile() = liftI(GetProfile())
    def getProject(projectName: String) = liftI(GetProject(projectName))
    def getProjects() = liftI(GetProjects())
    def getRepositories(project: Project) = liftI(GetRepositories(project))
  }

  object GitServices {
    implicit def gitservices[F[_]](implicit I: Inject[GitService,F]): GitServices[F] = new GitServices[F]
  }

  sealed trait Metrics[A]
  case class Start() extends Metrics[Unit]
  case class End() extends Metrics[Unit]

  class MetricsC[F[_]](implicit I: Inject[Metrics,F]) {
    def start() = liftI(Start())
    def end() = liftI(End())
  }

  object MetricsC {
    implicit def metricsc[F[_]](implicit I: Inject[Metrics,F]): MetricsC[F] = new MetricsC[F]
  }

}

/**
  * Simple sample app, which uses free monads to separate the structure
  * from the interpretation. As an example we'll simulate a minimal
  * github like service, where users can login, create projects and such.
  */
object GitServiceApp extends App {

  val Log = LoggerFactory.getLogger(GitServiceApp.getClass)

  // define the coproduct type
  type MetricsGit[A] = Coproduct[Metrics, GitService, A]

  import domain._
  import AST._

  def combined(projectName: String)(implicit M: MetricsC[MetricsGit], G: GitServices[MetricsGit]) = {
    import M._
    import G._

    for {
      _ <- start()
      project <- getProject(projectName)
      repositories <- getRepositories(project)
      projects <- getProfile()
      _ <- end()
    } yield (repositories, projects)
  }

  val interpreters: MetricsGit ~> Future = CoProductUtils.or(MetricsInterpreter, new GitHubInterpret(Config.token, Config.user))
  val resF = combined("project").foldMap(interpreters)

  val res = Await.result(resF, 5 seconds)
  Log.info("Final result of combined: " + res)
}

/**
  * Very simple interpreter, which just logs the calls
  */
object interpreters {

  val Log = LoggerFactory.getLogger(interpreters.getClass)

  import domain._
  import AST._

  object MetricsInterpreter extends (Metrics ~> Future) {

    override def apply[A](fa: Metrics[A]): Future[A] = fa match {
      case Start() => Log.info("Program started at: " + new Date()) ; Future()
      case End() => Log.info("Program ended at:" + new Date()) ; Future()
    }
  }

  class GitHubInterpret(githubToken: String, user: String) extends (GitService ~> Future) {

    implicit val formats = DefaultFormats

    val buhtig = new Buhtig(githubToken)
    val client = buhtig.asyncClient

    override def apply[A](fa: GitService[A]): Future[A] = fa match {
      case GetProfile() =>
        client.users(user).get[JSON].map { res => Profile(
          (res \ "login").extract[String],
          (res \ "name").extract[String],
          (res \ "email").extract[String])
        }

      case GetProjects() => Future { List.empty[Project] } // not implemented

      case GetRepositories(project) =>
        client.users(user).repos.get[JSON].map { repos => {
          repos.extract[List[JSON]].map { repo =>
            Repository((repo \ "name").extract[String],(repo \ "svn_url").extract[String] )
          }
        }
        }

      case GetProject(projectName) => Future { Project("", "") } // not implemented
    }
  }
}