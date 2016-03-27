package org.smartjava.scalabrabant.free.gitserver

import org.slf4j.LoggerFactory
import org.smartjava.scalabrabant.free.gitserver.interpreters.FutureInterpret

import scala.concurrent.{Await, Future}
import scala.language.higherKinds

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import scalaz._
import Scalaz._


object domain {
  // simple domain model, which described the entities in our domain.
  case class Profile(username: String, fullName: String, email: String)
  case class Project(name: String, description: String)
  case class Repository(name: String, location: String)

  // additional domain, for handling database actions
  case class Transaction()
  case class Result()

  // the actions that can be executed, easily extendable by others
  trait DBAction[A]
  case class StartTransaction() extends DBAction[Transaction]
  case class StopTransaction(trans: Transaction) extends DBAction[Unit]
  case class Rollback() extends DBAction[Unit]
  case class Commit() extends DBAction[Unit]
  case class ExecuteQuery(query: String) extends DBAction[Try[List[Result]]]
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

  // the AST is very basic for this one, we just specify an execute method
  sealed trait DBService[A]
  case class Execute[A](action: DBAction[A]) extends DBService[A]

  // and the function to lift actions
  def execute[A](action: DBAction[A]) = Free.liftF(Execute(action))
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

  // now define a program for our database
  val aQueryProgram = for {
    transaction <- execute(StartTransaction())
    updateResult <- execute(ExecuteQuery("UPDATE * FROM users"))
    selectResult <- execute(ExecuteQuery("SELECT * FROM users"))
    _ <- if (selectResult.isSuccess && updateResult.isSuccess) execute(Commit())
        else execute(Rollback())
    _ <- execute(StopTransaction(transaction))
  } yield selectResult


  val result = aQueryProgram.foldMap(FutureInterpret)
}

/**
  * Very simple interpreter, which just logs the calls
  */
object interpreters {

  val Log = LoggerFactory.getLogger(interpreters.getClass)

  import domain._
  import AST._

  object FutureInterpret extends (DBService ~> Future) {

    override def apply[A](fa: DBService[A]): Future[A] = {
      Log.info(s"Running step: $fa")

      fa match {
        case Execute(action) => action match {
          case StartTransaction() => Log.info("Starting transaction")       ; Future(Transaction())
          case StopTransaction(trans) => Log.info("Stop transaction")       ; Future()
          case Commit() => Log.info("Commit transaction")                   ; Future()
          case Rollback() => Log.info("Rollback transaction")               ; Future()
          case ExecuteQuery(query) => Log.info(s"Execture query: $query")   ; Future(Try(List.empty))
        }
      }
    }
  }
}