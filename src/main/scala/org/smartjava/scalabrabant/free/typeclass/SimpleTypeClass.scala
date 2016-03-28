package org.smartjava.scalabrabant.free.typeclass

object SimpleTypeClass extends App {

  // define the interface
  trait CanSayWhatAmI[A] {
    def whatAmI(x: A): String
  }

  // provide implementations for the interface
  implicit object CanSayWhatString extends CanSayWhatAmI[String] {
    def whatAmI(x: String) = s"I'm a String, with value: $x"
  }

  implicit object CanSayWhatLong extends CanSayWhatAmI[Long] {
    def whatAmI(x: Long) = s"I'm a Long, with value: $x"
  }

  // Define an implicit conversion to make it seem like the
  // function is defined on the object
  implicit class CanFooOps[A:CanSayWhatAmI](x: A) {
    def whatAmI = implicitly[CanSayWhatAmI[A]].whatAmI(x)
  }

  // require the evidence, either with a context bound
  def functionWhatAreYou[A : CanSayWhatAmI](x: A) = {
    println(implicitly[CanSayWhatAmI[A]].whatAmI(x))
  }

  // or with an implicit value
  def functionWhatAreYou2[A](x: A)(implicit ev: CanSayWhatAmI[A]) = {
    println(ev.whatAmI(x))
  }

  println("Hello".whatAmI)
  println(10L.whatAmI)


}
