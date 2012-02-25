package unfiltered.kit

import unfiltered.request._
import unfiltered.response._

import scala.util.matching.Regex

/** Routing kits for directing requests to handlers based on paths  */
object Routes {

  /** Matches request paths that start with the given string to functions
   *  that take the request and the remaining path string as parameters */
  def startsWith[A,B](
    route: (String, (HttpRequest[A], String) => ResponseFunction[B])*
  ) =
    toIntent(route) { (req: HttpRequest[A], path, k, rf) =>
      if (path.startsWith(k))
        Some(rf(req, path.substring(k.length)))
      else None
    }

  /** Matches requests that fully match the given regular expression string
   *  to functions that take the request and the list of matching groups
   *  as parameters. */
  def regex[A, B](
    route: (String, (HttpRequest[A], List[String]) => ResponseFunction[B])*
  ) =
    toIntent(
      route.map { case (k, v) => k.r -> v }
    ) { (req: HttpRequest[A], path, regex, rf) =>
      regex.unapplySeq(path).map { groups =>
        rf(req, groups)
      }
    }

  /**
   * Matches requests that match the given rails-style path specification
   * to functions that take the request and a Map of path-keys to their
   * values. e.g. "/thing/:thing_id" for the path "/thing/1" would call
   * the corresponding function with a `Map("thing_id" -> "1")`.
   */
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

  def toIntent[A,B,K,F](route: Traversable[(K,F)])(
    f: (HttpRequest[A], String, K, F) => Option[ResponseFunction[B]]
  ): unfiltered.Cycle.Intent[A, B] = {
    case req @ Path(path) =>
      route.view.flatMap { case (key, handler) =>
        f(req, path, key, handler)
      }.filter { _ != Pass }.headOption.getOrElse { Pass }
  }
}
