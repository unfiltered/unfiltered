package unfiltered.directives

import unfiltered.request._
import unfiltered.response._
import java.io.InputStream
import java.io.Reader
import unfiltered.Cookie

object Directives extends Directives with Syntax

trait Directives {
  import Result.Success
  import Result.Failure
  import Result.Error

  def result[R, A](r: Result[R, A]): Directive[Any, R, A] = Directive[Any, R, A](_ => r)

  /** Produces a Directive with the given Result.Success value, for when a directive
   *  is required to satisfy an intent's interface but all requests are acceptable */
  def success[A](value: A): Directive[Any, Nothing, A] = result[Nothing, A](Success(value))

  def failure[R](r: R): Directive[Any, R, Nothing] = result[R, Nothing](Failure(r))

  def error[R](r: R): Directive[Any, R, Nothing] = result[R, Nothing](Error(r))

  object commit extends Directive[Any, Nothing, Unit](_ => Success(())) {
    override def flatMap[T, R, A](f: Unit => Directive[T, R, A]): Directive[T, R, A] =
      commit(f(()))

    def apply[T, R, A](d: Directive[T, R, A]): Directive[T, R, A] = Directive[T, R, A] { r =>
      d(r) match {
        case Failure(response) => Error(response)
        case result => result
      }
    }
  }

  def getOrElse[R, A](opt: Option[A], orElse: => ResponseFunction[R]): Directive[Any, ResponseFunction[R], A] =
    opt.map(success).getOrElse(failure(orElse))

  /* HttpRequest has to be of type Any because of type-inference (SLS 8.5) */
  case class when[A](f: PartialFunction[HttpRequest[Any], A]) {
    def orElse[R](fail: ResponseFunction[R]) =
      new FilterDirective[Any, ResponseFunction[R], A](
        r => if (f.isDefinedAt(r)) Success(f(r)) else Failure(fail),
        _ => Failure(fail)
      )
  }

  def request[T]: Directive[T, Nothing, HttpRequest[T]] = Directive[T, Nothing, HttpRequest[T]](Success(_))

  def underlying[T]: Directive[T, Nothing, T] = request[T] map { _.underlying }

  def inputStream: Directive[Any, Nothing, InputStream] = request[Any] map { _.inputStream }

  def reader: Directive[Any, Nothing, Reader] = request[Any] map { _.reader }

  def protocol: Directive[Any, Nothing, String] = request[Any] map { _.protocol }

  def method: Directive[Any, Nothing, String] = request[Any] map { _.method }

  def uri: Directive[Any, Nothing, String] = request[Any] map { _.uri }

  def parameterNames: Directive[Any, Nothing, Iterator[String]] = request[Any] map { _.parameterNames }

  def parameterValues(param: String): Directive[Any, Nothing, Seq[String]] = request[Any] map {
    _.parameterValues(param)
  }

  def headerNames: Directive[Any, Nothing, Iterator[String]] = request[Any] map { _.headerNames }

  def headers(name: String): Directive[Any, Nothing, Iterator[String]] = request[Any] map { _.headers(name) }

  def cookies: Directive[Any, Nothing, Map[String, Option[Cookie]]] = request[Any] map { case Cookies(cookies) =>
    cookies
  }

  def isSecure: Directive[Any, Nothing, Boolean] = request[Any] map { _.isSecure }

  def remoteAddr: Directive[Any, Nothing, String] = request[Any] map { _.remoteAddr }

  def queryParams: Directive[Any, Nothing, Map[String, Seq[String]]] = request[Any].map { case QueryParams(params) =>
    params
  }

  def param[A, B](name: String)(f: Seq[String] => Option[B]): Directive[Any, Nothing, Option[B]] =
    parameterValues(name).map(f)
}
