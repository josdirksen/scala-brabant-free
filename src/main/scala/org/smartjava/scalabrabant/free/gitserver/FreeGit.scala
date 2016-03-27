package org.smartjava.scalabrabant.free.gitserver

import java.util.Date

import net.caoticode.buhtig.Buhtig
import net.caoticode.buhtig.Converters.JSON
import net.caoticode.buhtig.Converters._
import org.json4s.DefaultFormats
import org.slf4j.LoggerFactory
import org.smartjava.scalabrabant.free.gitserver.GitServiceApp.GitServiceOp
import org.smartjava.scalabrabant.free.gitserver.interpreters.MetricsInterpreter

import scala.concurrent.{Await, Future}
import scala.language.higherKinds

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz._
import Scalaz._

import org.json4s.JsonDSL._
import org.json4s.DefaultFormats._


object domain {
  // simple domain model, which described the entities in our domain.
  case class Profile(username: String, fullName: String, email: String)
  case class Project(name: String, description: String)
  case class Repository(name: String, location: String)
}

object AST {

  import domain._

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
  def getProfile() = Free.liftF(GetProfile())
  def getProject(projectName: String) = Free.liftF(GetProject(projectName))
  def getProjects() = Free.liftF(GetProjects())
  def getRepositories(project: Project) = Free.liftF(GetRepositories(project))

  sealed trait Metrics[A]
  case class Start() extends Metrics[Unit]
  case class End() extends Metrics[Unit]

  def start() = Free.liftF(Start())
  def end() = Free.liftF(End())
}

/**
  * Simple sample app, which uses free monads to separate the structure
  * from the interpretation. As an example we'll simulate a minimal
  * github like service, where users can login, create projects and such.
  */
object GitServiceApp extends App {

  val Log = LoggerFactory.getLogger(GitServiceApp.getClass)

  import domain._
  import AST._

  // The monad we're working with.
  type GitServiceOp[A] = Free[GitService, A]

  // At this point we can define a simple program which uses our AST
  def findRepositories(projectName: String) : GitServiceOp[List[Repository]] = {
    for {
      project <- getProject(projectName)
      repositories <- getRepositories(project)
    } yield repositories
  }

  // we can also combine various programs
  def getRepoAndProfile(projectName: String) = {
    for {
      repositories <- findRepositories(projectName)
      projects <- getProfile()
    } yield (repositories, projects)
  }

  def singleLogProgram = {
    for {
      _ <- start()
      _ <- end()
    } yield ()
  }

  // Run with the sample interpreter, returns Id monad
  singleLogProgram.foldMap(MetricsInterpreter)
}

/**
  * Very simple interpreter, which just logs the calls
  */
object interpreters {

  val Log = LoggerFactory.getLogger(interpreters.getClass)

  import domain._
  import AST._

  def runInFuture[A](program: GitServiceOp[A]): Future[A] = {
    Log.info("Running in Future Interpreter")
    val result = program.foldMap(new GitHubInterpret(Config.token, Config.user))
    Log.info("Done Running in Future Interpreter")
    result
  }

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