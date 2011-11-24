package unfiltered

package object response {
  import Pass.RF
  implicit def partialToPassing[A,B >: RF](
    intent: PartialFunction[A,B]
  ) = new {
    def onPass[A1 <: A, B1 >: B](onPass: PartialFunction[A1, B1]) =
      Pass.onPass(intent, onPass)
    def onPass[A1 <: A, B1 >: B](onPass: Function[A1, B1]) =
      Pass.onPass(intent, onPass)
    def fold[C](onPass: A => C, andThen: (A, B) => C) =
      Pass.fold(intent, onPass, andThen)
  }
}
