package unfiltered.directives

import unfiltered.request._
import unfiltered.response._

trait Syntax extends Directives {
  class Ops[X](x:X){
    import Directive.{Eq, Gt, Lt}

    def ===[V, T, R, A](v:V)(implicit eq:Eq[X, V, T, R, A]) = eq.directive(x, v)
    def in[V, T, R, A](vs:Seq[V])(implicit eq:Eq[X, V, T, R, A]) = vs.map(v => eq.directive(x,v)).reduce(_ | _)

    def gt[V, T, R, A](v:V)(implicit gtd:Gt[X, V, T, R, A]) = gtd.directive(x, v)
    def > [V, T, R, A](v:V)(implicit gtd:Gt[X, V, T, R, A]) = gt(v)

    def lt[V, T, R, A](v:V)(implicit ltd:Lt[X, V, T, R, A]) = ltd.directive(x, v)
    def <[V, T, R, A](v:V)(implicit ltd:Lt[X, V, T, R, A])  = lt(v)

    def lte[V, T, R, A](v:V)(implicit lted:Lt[X, V, T, R, A], eqd:Eq[X, V, T, R, A]) =
      lted.directive(x, v) orElse eqd.directive(x, v)
    def <=[V, T, R, A](v:V)(implicit ltd:Lt[X, V, T, R, A], eqd:Eq[X, V, T, R, A]) =
      lte(v)

    def gte[V, T, R, A](v:V)(implicit gtd:Gt[X, V, T, R, A], eq:Eq[X, V, T, R, A]) =
      gtd.directive(x, v) orElse eq.directive(x, v)
    def >=[V, T, R, A](v:V)(implicit gtd:Gt[X, V, T, R, A], eq:Eq[X, V, T, R, A]) = gte(v)
  }

  implicit def ops[X](x:X) = new Ops[X](x)

  implicit def defMethod(M:Method) =
    when{ case M(_) => } orElse MethodNotAllowed

  implicit def accepting(A:Accepts.Accepting) =
    when{ case A(_) => } orElse NotAcceptable

  implicit def defQueryParams(q:QueryParams.type) =
    queryParams

  implicit def defExtract[A](Ex:Params.Extract[Nothing, A]) =
    when{ case Params(Ex(a)) => a } orElse BadRequest

  implicit def defPathIntent(p:Path.type) = PathIntentions

  object PathIntentions {
    def Intent = Directive.Intent(Path[Any])
  }
}
