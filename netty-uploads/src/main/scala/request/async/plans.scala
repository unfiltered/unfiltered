package unfiltered.netty.async

import unfiltered.netty
import unfiltered.netty._
import unfiltered.netty.request._
import unfiltered.request.HttpRequest
import unfiltered.response._
import unfiltered.Async
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.{HttpRequest=>NHttpRequest,
                                           HttpResponse=>NHttpResponse,
                                           HttpChunk => NHttpChunk}

/** Enriches an async netty plan with multipart decoding capabilities. */
trait MultiPartDecoder extends async.Plan with AbstractMultiPartDecoder with TidyExceptionHandler {

  /** Decide if the intent could handle the request */
  protected def handleOrPass(ctx: ChannelHandlerContext, e: MessageEvent, binding: RequestBinding)(body: => Unit) = {
    if(intent.isDefinedAt(binding))
      body
    else
      pass(ctx, e)
  }

  private lazy val guardedIntent = intent.onPass(
    { req: HttpRequest[ReceivedMessage] =>
      req.underlying.context.sendUpstream(req.underlying.event) }
  )

  /** Called when the chunked request has been fully received. Executes the intent */
  protected def complete(ctx: ChannelHandlerContext, e: MessageEvent) = {
    ctx getAttachment match {
      case state: MultiPartChannelState => guardedIntent {
        state.originalReq match {
          case Some(req) => new MultiPartBinding(state.decoder, ReceivedMessage(req, ctx, e))
          case _ => error("Original request missing from channel state %s".format(ctx))
        }
      }
      case _ => error("Could not retrieve channel state from context %s".format(ctx))
    }
  }

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) = handle(ctx, e)
}

class MultiPartPlanifier(val intent: async.Plan.Intent, val pass: MultipartPlan.PassHandler)
extends MultiPartDecoder with ServerErrorResponse

/** Provides a MultiPart decoding plan that may buffer to disk while parsing the request */
object MultiPartDecoder {
  def apply(intent: async.Plan.Intent): async.Plan = MultiPartDecoder(intent, MultipartPlan.DefaultPassHandler)
  def apply(intent: async.Plan.Intent, pass: MultipartPlan.PassHandler) = new MultiPartPlanifier(intent, pass)
}

/** Provides a MultiPart decoding plan that won't buffer to disk while parsing the request */
object MemoryMultiPartDecoder {
  def apply(intent: async.Plan.Intent): async.Plan = MemoryMultiPartDecoder(intent, MultipartPlan.DefaultPassHandler)
  def apply(intent: async.Plan.Intent, pass: MultipartPlan.PassHandler) = new MultiPartPlanifier(intent, pass) {
      override protected val useDisk = false
  }
}