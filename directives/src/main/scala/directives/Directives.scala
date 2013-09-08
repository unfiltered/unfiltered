package unfiltered.directives

import unfiltered.request._
import unfiltered.response._

object Directives extends Directives with Syntax

trait Directives {
  import Result.{Success, Failure, Error}

  def result[R, A](r:Result[R, A]) = Directive[Any, R, A](_ => r)

  def success[A](value:A) = result[Nothing, A](Success(value))

  def failure[R](r:ResponseFunction[R]) = result[ResponseFunction[R], Nothing](Failure(r))

  def error[R](r:ResponseFunction[R]) = result[ResponseFunction[R], Nothing](Error(r))

  object commit extends Directive[Any, Nothing, Unit](_ => Success(())){
    override def flatMap[T, R, A](f:Unit => Directive[T, R, A]):Directive[T, R, A] =
      commit(f())

    def apply[T, R, A](d:Directive[T, R, A]) = Directive[T, R, A]{ r => d(r) match {
      case Failure(response) => Error(response)
      case result            => result
    }}
  }

  @deprecated(
  """Use:
  for {
    x <- myDirective
    _ <- commit
    ...
  } instead of autocommit(myDirective)""", since="0.7.0")
  def autocommit[T, R, A](d:Directive[T, R, A]) =
    for {
      x <- d
      _ <- commit
    } yield x

  def getOrElse[R, A](opt:Option[A], orElse: => ResponseFunction[R]) = opt.map(success).getOrElse(failure(orElse))

  /* HttpRequest has to be of type Any because of type-inference (SLS 8.5) */
  case class when[A](f:PartialFunction[HttpRequest[Any], A]){
    def orElse[R](fail:ResponseFunction[R]) = Directive[Any, ResponseFunction[R], A](r => if(f.isDefinedAt(r)) Success(f(r)) else Failure(fail))
  }

  def request[T] = Directive[T, Nothing, HttpRequest[T]](Success(_))

  def underlying[T] = request[T] map { _.underlying }

  def inputStream = request[Any] map { _.inputStream }

  def reader = request[Any] map { _.reader }

  def protocol = request[Any] map { _.protocol }

  def method = request[Any] map { _.method }

  def uri = request[Any] map { _.uri }

  def parameterNames = request[Any] map { _.parameterNames }

  def parameterValues(param: String) = request[Any] map { _.parameterValues(param) }

  def headerNames = request[Any] map { _.headerNames }

  def headers(name: String) = request[Any] map { _.headers(name) }

  def cookies = request[Any] map { case Cookies(cookies) => cookies }

  def isSecure = request[Any] map { _.isSecure }

  def remoteAddr = request[Any] map { _.remoteAddr }

  def queryParams = request[Any].map{ case QueryParams(params) => params }

  def param[A,B](name: String)(f: Seq[String] => Option[B]) =
    parameterValues(name).map(f)
}
