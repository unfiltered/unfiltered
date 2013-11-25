package unfiltered.netty.future

import unfiltered.netty.{ReceivedMessage,ServerErrorResponse}
import unfiltered.request.HttpRequest
import unfiltered.response.{Pass,InternalServerError, ResponseFunction}
import unfiltered.netty.async

import org.jboss.netty.handler.codec.http.{HttpResponse => NHttpResponse}

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Failure, Success}

object Plan {
  type Intent = PartialFunction[HttpRequest[ReceivedMessage], Future[ResponseFunction[NHttpResponse]]]
}

object Planify {
  def apply(intentIn: Plan.Intent)(implicit executionContextIn: ExecutionContext): Plan =
    new Plan with ServerErrorResponse {
      val intent = intentIn
      val executionContext = executionContextIn
    }
}

trait Plan extends async.RequestPlan {
  def intent: Plan.Intent

  implicit def executionContext: ExecutionContext

  val requestIntent: async.Plan.Intent = unfiltered.kit.Futured(intent)
}
