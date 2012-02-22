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

  def specify[A, B](
    route: (String, ((HttpRequest[A], Map[String,String]) =>
                     ResponseFunction[B]))*) =
    toIntent(
      route.map {
        case (Seg(spec), f) => spec -> f
      }
    ) { (req: HttpRequest[A], path, spec, rf) =>
      val Seg(actual) = path
      if (spec.length != actual.length)
        None
      else {
        val start: Option[Map[String,String]] = Some(Map.empty[String,String])
        (start /: spec.zip(actual)) {
          case (None, _) => None
          case (Some(m), (sp, act)) if sp.startsWith(":") =>
            Some(m + (sp.substring(1) -> act))
          case (opt, (sp, act)) if sp == act =>
            opt
          case _ => None
        }.map { m =>
          rf(req, m)
        }
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
    ResponseString("hi")

  def regexIntent[A,B]: unfiltered.Cycle.Intent[A,B] =
    PathMap.regex(
      """/name/(\d+)""" -> Test.regexMethod,
      "/def"            -> Test.regexMethod
    )

  def regexMethod[A,B](req: HttpRequest[A], mtch: Regex.Match):
  ResponseFunction[B] =
    ResponseString("hi")

  def specIntent[A,B]: unfiltered.Cycle.Intent[A,B] =
    PathMap.specify(
      "/things/:id" -> Test.things,
      "/things"     -> Test.allTheThings
    )

  def things[A,B](req: HttpRequest[A], params: Map[String,String]) = {
    val id = params("id")
    ResponseString("hi")
  }

  def allTheThings[A,B](req: HttpRequest[A], params: Map[String,String]) =
    ResponseString("hi")
}
