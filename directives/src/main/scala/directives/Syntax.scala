package unfiltered.directives

import unfiltered.request._
import unfiltered.response._
import scala.language.implicitConversions

trait Syntax extends Directives {
  implicit class Ops[X](x: X){
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

  implicit def defMethod(M: Method): FilterDirective[Any, ResponseFunction[Any], Unit] =
    when{ case M(_) => } orElse MethodNotAllowed

  implicit def accepting(A: Accepts.Accepting): FilterDirective[Any, ResponseFunction[Any], Unit] =
    when{ case A(_) => } orElse NotAcceptable

  implicit def defQueryParams[A, B](q: QueryParams.type): Directive[A, B, Map[String,Seq[String]]] =
    queryParams

  implicit def defExtract[A](Ex: Params.Extract[A]): FilterDirective[Any, ResponseFunction[A], A] =
    when{ case Params(Ex(a)) => a } orElse BadRequest

  implicit def defInterpreterIdentity[T]: data.Interpreter[T, T, Nothing] = data.Interpreter.identity[T]
  implicit def defInterpreterString: data.as.String.type = data.as.String
}
