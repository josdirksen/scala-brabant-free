package org.smartjava.scalabrabant.free.stackless

import scalaz._
import Scalaz._
import Free._

object Trampoline extends App {

  def dumbCopyChar(char: String, n: Integer): String = {
    if (n == 1) return char
    return char + dumbCopyChar(char, n -1)
  }

  // fails at 100000
  println(dumbCopyChar("a", 100000))

}
