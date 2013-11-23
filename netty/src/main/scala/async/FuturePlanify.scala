package unfiltered.netty.async

import unfiltered.netty.ReceivedMessage
import unfiltered.request.HttpRequest
import unfiltered.response.{InternalServerError, ResponseFunction}
import org.jboss.netty.handler.codec.http.{HttpResponse => NHttpResponse}
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Failure, Success}

object FuturePlan {
  type Intent = PartialFunction[HttpRequest[ReceivedMessage], Future[ResponseFunction[NHttpResponse]]]

  /**
   * Transform a Future intent into a regular async.Plan.Intent.
   */
  def toAsyncIntent(source: Intent)(implicit executionContext: ExecutionContext) : Plan.Intent = {
    case req if source.isDefinedAt(req) =>
      val rf : Future[ResponseFunction[NHttpResponse]] = source(req)
      rf.onComplete {
        case Success(r) => req.respond(r)
        case Failure(r) => req.respond(InternalServerError)
      }
  }
}

object FuturePlanify {
  def apply(intent: FuturePlan.Intent)(implicit executionContext: ExecutionContext) : Plan.Intent = FuturePlan.toAsyncIntent(intent)
}
