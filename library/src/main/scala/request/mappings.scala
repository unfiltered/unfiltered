package unfiltered.kit

import unfiltered.request._
import unfiltered.response._

import scala.util.matching.Regex

object PathMap {
  def startsWith[A, B]
  (route: (String, ((HttpRequest[A], String) => ResponseFunction[B]))*)
  : unfiltered.Cycle.Intent[A, B] = { case req =>
    val path = Path(req)
    route.find {
      case (k, _) => path.startsWith(k)
    }.map {
      case (k, rf) => rf(req, path.substring(k.length))
    }.getOrElse { Pass }
  }

  def matching[A, B]
  (route: (String, ((HttpRequest[A], Regex.Match) => ResponseFunction[B]))*)
  : unfiltered.Cycle.Intent[A, B] = {
    val regexRoute = route.map { case (k, v) => k.r -> v }
    ({
      case req =>
        val path = Path(req)
        regexRoute.view.flatMap {
          case (regex, rf) =>
            regex.findPrefixMatchOf(path).map { mtch =>
              rf(req, mtch)
            }
        }.headOption.getOrElse { Pass }
    })
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
    PathMap.matching(
      """/name/(\d+)""" -> Test.matchMethod,
      "/def"            -> Test.matchMethod
    )

  def matchMethod[A,B](req: HttpRequest[A], mtch: Regex.Match):
  ResponseFunction[B] =
    error("todo")
}
