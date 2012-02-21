package unfiltered.kit

import unfiltered.request._
import unfiltered.response._

object PathMap {
  def startsWith[A, B]
  (route: (String, (HttpRequest[A] => ResponseFunction[B]))*)
  : PartialFunction[HttpRequest[A], ResponseFunction[B]] = { case req =>
    val path = Path(req)
    route.find {
      case (k, _) => path.startsWith(k)
    }.map {
      case (_, rf) => rf(req)
    }.getOrElse { Pass }
  }
}


object Test extends  {
  def intent[A,B]: unfiltered.Cycle.Intent[A,B] =
    PathMap.startsWith(
      "/abc" -> Test.extractionMethod
    )

  def asyncIntent[A,B]: unfiltered.Async.Intent[A,B] =
    PathMap.startsWith(
      "/abc" -> Test.extractionMethod _
    )

  def extractionMethod[A,B](req: HttpRequest[A]): ResponseFunction[B] =
    error("todo")

  def asyncExtractionMethod[A,B](req: HttpRequest[A] with Responder[B]): Any =
    error("todo")

}
