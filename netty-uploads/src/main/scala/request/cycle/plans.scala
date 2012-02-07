package unfiltered.netty.cycle

import unfiltered.netty
import unfiltered.netty._
import unfiltered.netty.request._
import unfiltered.response._
import unfiltered.response.{ResponseFunction, Pass}
import unfiltered.request.HttpRequest
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.{HttpRequest=>NHttpRequest,
                                           HttpResponse=>NHttpResponse,
                                           HttpChunk => NHttpChunk}

import org.clapper.avsl.Logger

/** Enriches an async netty plan with multipart decoding capabilities. */
trait MultiPartDecoder extends cycle.Plan with AbstractMultiPartDecoder {

    private val logger = Logger(this.getClass.getCanonicalName)

    protected def handleOrPass(ctx: ChannelHandlerContext, e: MessageEvent, binding: RequestBinding)(body: => Unit) = {
      intent.orElse({ case _ => Pass }: Plan.Intent)(binding) match {
        case Pass => 
          logger.debug("Passing...")
          pass(ctx, e)
        case intent =>
          logger.debug("Handling...")
          body
      }
    }

    protected def complete(ctx: ChannelHandlerContext, e: MessageEvent) = {
      val channelState = ctx getAttachment match {
        case s: MultiPartChannelState => s
        case _ => MultiPartChannelState()
      }
      catching(ctx) {
        executeIntent { 
          guardedIntent {
            new MultiPartBinding(channelState.decoder, ReceivedMessage(channelState.originalReq.get, ctx, e))
            //new RequestBinding(ReceivedMessage(channelState.originalReq.get, ctx, e))
          }
        }
      }
    }

    private lazy val guardedIntent = intent.fold(
      (req: HttpRequest[ReceivedMessage]) =>
        req.underlying.context.sendUpstream(req.underlying.event),
      (req: HttpRequest[ReceivedMessage],
       rf: ResponseFunction[NHttpResponse]) =>
        executeResponse {
          catching(req.underlying.context) {
            //decode(req.underlying.context, req.underlying.event)
            req.underlying.respond(rf)
          }
        }
    )

    override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) = {
      handle(ctx, e)
    }
}

class MultiPartPlanifier(val intent: cycle.Plan.Intent)
extends MultiPartDecoder with ThreadPool with ServerErrorResponse

/** Provides a MultiPart decoding plan that may buffer to disk while parsing the request */
object MultiPartDecoder {
  def apply(intent: cycle.Plan.Intent) = new MultiPartPlanifier(intent)
}

/** Provides a MultiPart decoding plan that won't buffer to disk while parsing the request */
object MemoryMultiPartDecoder {
  def apply(intent: cycle.Plan.Intent) = new MultiPartPlanifier(intent) {
      override protected val useDisk = false
  }
}