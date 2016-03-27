package org.smartjava.scalabrabant.free.stackless

import scalaz.Free._
import scalaz.Free.Trampoline
import scalaz.Trampoline

/**
  * Stackless Scala: http://blog.higher-order.com/assets/trampolines.pdf
  *
  * Good sample at: http://espinhogr.github.io/scala/2015/07/12/trampolines-in-scala.html
  */
object TrampolineApp extends App {

  def dumbCopyChar(char: String, n: Integer): String = {
    if (n == 1) return char
    return dumbCopyChar(char, n -1) + char
  }

  def trampolineCopyChar(char: String, n: Integer): Trampoline[String] = {
    if (n == 1) Trampoline.done(char) // shortcircuit when n = 1, to avoid an extra letter.
    else {
      // Suspend should contain the call to the recursive function
      Trampoline.suspend(trampolineCopyChar(char, n - 1)).flatMap { p =>
        // you can use flatmap to do something with the result, before returning
        Trampoline.done(p + char)
      }
    }
  }

  // fails at 100000
  println(trampolineCopyChar("a", 100000).run)
}
