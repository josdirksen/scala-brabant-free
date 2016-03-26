package org.smartjava.scalabrabant.free.gitserver

import org.slf4j.LoggerFactory
import org.smartjava.scalabrabant.free.gitserver.GitServiceApp.GitServiceOp
import org.smartjava.scalabrabant.free.gitserver.interpreters.NoOpInterpreter

import scala.concurrent.Future
import scala.language.higherKinds
import scalaz.{Free, Id, ~>, _}

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
}

/**
  * Simple sample app, which uses free monads to separate the structure
  * from the interpretation. As an example we'll simulate a minimal
  * github like service, where users can login, create projects and such.
  */
object GitServiceApp extends App {

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

  // Run with the sample interpreter, returns Id monad
  interpreters.runInNoIp(findRepositories("repo1"))
  interpreters.runInNoIp(getRepoAndProfile("repo1"))
}

/**
  * Very simple interpreter, which just logs the calls
  */
object interpreters {

  val Log = LoggerFactory.getLogger(interpreters.getClass)

  import domain._
  import AST._

  def runInNoIp[A](program: GitServiceOp[A]): A = {
    Log.info("We can do something before the program is run")
    val result = program.foldMap(NoOpInterpreter)
    Log.info("We can do something after the program is run")
    result
  }


  // an interpreter is a natural transformation from the Gitservice[A] to
  // a specific Monad. This interpreter does nothing special, so just uses the
  // Id monad.
  object NoOpInterpreter extends (GitService ~> Id.Id) {

    override def apply[A](fa: GitService[A]): Id.Id[A] = fa match {
      case GetProfile() => println("GetProfile called") ; Profile("", "", "")
      case GetProjects() => println("GetProjects called") ; List.empty[Project]
      case GetRepositories(project) => println("GetRepositores called") ; List.empty[Repository]
      case GetProject(projectName) => println("GetProjects called") ; Project("", "")
    }
  }
}