package org.smartjava.scalabrabant.free.base

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object Definitions {

  // Transforms F[A] into F[B] by applying function f
  trait Functor[F[_]] {
    def map[A, B](fa: F[A])(f: A => B): F[B]
  }

  trait Monad[F[_]] {

    // Create a new monad instance
    def point[A](a: A): F[A]

    // Also known as flatmap, used to sequence Monads
    def bind[A,B](fa: F[A])(f: A => F[B]): F[B]

    // Used to flatten nested monads
    def join[A](ffa: F[F[A]]): F[A] = bind(ffa)(a => a)

    // And map
    def map[A, B](fa: F[A])(f: A => B) : F[B] = bind(fa)(a => point(f(a)))
  }

  // syntactic sugar
  for {
    a <- Future(10 * 10)
    b <- Future(a * 2)
    c <- Future(b + 5)
  } yield a + b + c


  // is actually this
  Future(10 * 20).flatMap( a =>
    Future(a * 2).flatMap ( b =>
      Future(b +5).map { c=>
        a + b + c
      }
    )
  )


}

