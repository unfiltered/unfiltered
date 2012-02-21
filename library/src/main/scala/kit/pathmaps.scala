package unfiltered.kit

import unfiltered.request._
import unfiltered.response._

import scala.util.matching.Regex

object PathMap {

  def toIntent[A,B,K,F](route: Traversable[(K,F)])(
    f: (HttpRequest[A], String, K, F) => Option[ResponseFunction[B]]
  ): unfiltered.Cycle.Intent[A, B] = {
    case req @ Path(path) =>
      route.view.flatMap { case (key, handler) =>
        f(req, path, key, handler)
      }.filter { _ != Pass }.headOption.getOrElse { Pass }
  }

  def startsWith[A,B](
    route: (String, (HttpRequest[A], String) => ResponseFunction[B])*
  ) =
    toIntent(route) { (req: HttpRequest[A], path, k, rf) =>
      if (path.startsWith(k))
        Some(rf(req, path.substring(k.length)))
      else None
    }

  def regex[A, B](
    route: (String, ((HttpRequest[A], Regex.Match) => ResponseFunction[B]))*
  ) =
    toIntent(
      route.map { case (k, v) => k.r -> v }
    ) { (req: HttpRequest[A], path, regex, rf) =>
      regex.findPrefixMatchOf(path).map { mtch =>
        rf(req, mtch)
      }
    }
}


object Test  {
  def stringIntent[A,B]: unfiltered.Cycle.Intent[A,B] =
    PathMap.startsWith(
      "/abc" -> Test.stringMethod,
      "/def" -> Test.stringMethod
    )

  def stringMethod[A,B](req: HttpRequest[A], remainder: String):
  ResponseFunction[B] =
    error("todo")

  def matchIntent[A,B]: unfiltered.Cycle.Intent[A,B] =
    PathMap.regex(
      """/name/(\d+)""" -> Test.regexMethod,
      "/def"            -> Test.regexMethod
    )

  def regexMethod[A,B](req: HttpRequest[A], mtch: Regex.Match):
  ResponseFunction[B] =
    error("todo")
}
