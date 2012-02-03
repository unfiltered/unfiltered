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
trait MultiPartDecoder extends async.Plan with AbstractMultiPartDecoder {
    private lazy val guardedIntent = intent.onPass(
      { req: HttpRequest[ReceivedMessage] =>
        req.underlying.context.sendUpstream(req.underlying.event) }
    )

    protected def complete(ctx: ChannelHandlerContext, e: MessageEvent) = {
      val channelState = ctx getAttachment match {
        case s: MultiPartChannelState => s
        case _ => MultiPartChannelState()
      }
      guardedIntent(new MultiPartBinding(channelState.decoder, ReceivedMessage(channelState.originalReq.get, ctx, e)))
    }

    override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
      handle(ctx: ChannelHandlerContext, e: MessageEvent)
    }
}

class MultiPartPlanifier(val intent: async.Plan.Intent)
extends MultiPartDecoder with ServerErrorResponse

/** Provides a MultiPart decoding plan that may buffer to disk while parsing the request */
object MultiPartDecoder {
  def apply(intent: async.Plan.Intent) = new MultiPartPlanifier(intent)
}

/** Provides a MultiPart decoding plan that won't buffer to disk while parsing the request */
object MemoryMultiPartDecoder {
  def apply(intent: async.Plan.Intent) = new MultiPartPlanifier(intent) {
      override protected val useDisk = false
  }
}