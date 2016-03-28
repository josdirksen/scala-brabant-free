package org.smartjava.scalabrabant.free.typeclass

object SimpleTypeClass extends App {

  // define the interface
  trait CanSayWhatAmI[A] {
    def whatAmI(x: A): String
  }

  // provide implementations for the interface
  implicit object MyObjectCanSayWhatAmI extends CanSayWhatAmI[String] {
    def whatAmI(x: String) = s"I'm a String, with value: $x"
  }

  // require the evidence, either with a context bound
  def functionWhatAreYou[A : CanSayWhatAmI](x: A) = {
    println(implicitly[CanSayWhatAmI[A]].whatAmI(x))
  }

  // or with an implicit value
  def functionWhatAreYou2[A](x: A)(implicit ev: CanSayWhatAmI[A]) = {
    println(ev.whatAmI(x))
  }

  functionWhatAreYou("hello")
  functionWhatAreYou2("hello")
}
