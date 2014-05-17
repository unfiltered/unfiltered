package unfiltered

import scala.language.implicitConversions

package object response {
  import Pass.RF
  /** Implicit methods on PartialFunction: onPass and onFold.
   *  See unfiltered.response.Pass the explicit versions. */
  implicit class partialToPassing[A,B >: RF](
    val intent: PartialFunction[A,B]
  ) extends AnyVal {
    def onPass[A1 <: A, B1 >: B](onPass: PartialFunction[A1, B1]) =
      Pass.onPass(intent, onPass)
    def onPass[A1 <: A, B1 >: B](onPass: Function[A1, B1]) =
      Pass.onPass(intent, onPass)
    def fold[C](onPass: A => C, andThen: (A, B) => C) =
      Pass.fold(intent, onPass, andThen)
  }
}
