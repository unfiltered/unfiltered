package unfiltered.netty.future

import unfiltered.netty.{ ReceivedMessage, ServerErrorResponse }
import unfiltered.request.HttpRequest
import unfiltered.response.{ Pass, InternalServerError, ResponseFunction }
import unfiltered.netty.async
import io.netty.channel.ChannelHandler.Sharable
import io.netty.handler.codec.http.{ HttpResponse => NettyHttpResponse }

import scala.concurrent.{ Future, ExecutionContext }
import scala.util.{ Failure, Success }

object Plan {
  type Intent =
    PartialFunction[
      HttpRequest[ReceivedMessage],
      Future[ResponseFunction[NettyHttpResponse]]]
}

object Planify {
  @Sharable
  class Planned(
    val intent: Plan.Intent,
    val executionContext: ExecutionContext) extends Plan
    with ServerErrorResponse

  def apply(intentIn: Plan.Intent)
   (implicit executionContextIn: ExecutionContext): Plan =
     new Planned(intentIn, executionContextIn)
}

@Sharable
trait Plan extends async.RequestPlan {
  def intent: Plan.Intent

  implicit def executionContext: ExecutionContext

  val requestIntent: async.Plan.Intent =
    unfiltered.kit.Futured(intent) { (req, exc) =>
      this.onException(req.underlying.context, exc)
    }
}
