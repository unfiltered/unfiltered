package unfiltered.netty.cycle

import unfiltered.netty
import unfiltered.netty._
import unfiltered.netty.request._
import unfiltered.netty.request.MultipartPlan
import unfiltered.response._
import unfiltered.response.{ResponseFunction, Pass}
import unfiltered.request.HttpRequest
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.{HttpRequest=>NHttpRequest,
                                           HttpResponse=>NHttpResponse,
                                           HttpChunk => NHttpChunk}

/** Enriches an async netty plan with multipart decoding capabilities. */
trait MultiPartDecoder extends cycle.Plan with AbstractMultiPartDecoder with TidyExceptionHandler {

  /** Decide if the intent could handle the request */
  protected def handleOrPass(ctx: ChannelHandlerContext, e: MessageEvent, binding: RequestBinding)(body: => Unit) = {
    if(intent.isDefinedAt(binding))
      body
    else
      pass(ctx, e)
  }

  /** Called when the chunked request has been fully received. Executes the intent */
  protected def complete(ctx: ChannelHandlerContext, e: MessageEvent) = {
    ctx getAttachment match {
      case state: MultiPartChannelState => catching(ctx) {
        executeIntent { 
          guardedIntent {
            state.originalReq match {
              case Some(req) => new MultiPartBinding(state.decoder, ReceivedMessage(req, ctx, e))
              case _ => error("Original request missing from channel state %s".format(ctx))
            }
          }
        }
      }
      case _ => error("Could not retrieve channel state from context %s".format(ctx))
    }
  }

  private lazy val guardedIntent = intent.fold(
    (req: HttpRequest[ReceivedMessage]) =>
      req.underlying.context.sendUpstream(req.underlying.event),
    (req: HttpRequest[ReceivedMessage],
     rf: ResponseFunction[NHttpResponse]) =>
      executeResponse {
        catching(req.underlying.context) {
          req.underlying.respond(rf)
        }
      }
  )

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) = handle(ctx, e)
}

class MultiPartPlanifier(val intent: cycle.Plan.Intent, val pass: MultipartPlan.PassHandler)
extends MultiPartDecoder with ThreadPool with ServerErrorResponse

/** Provides a MultiPart decoding plan that may buffer to disk while parsing the request */
object MultiPartDecoder {
  def apply(intent: cycle.Plan.Intent): cycle.Plan = MultiPartDecoder(intent, MultipartPlan.DefaultPassHandler)
  def apply(intent: cycle.Plan.Intent, pass: MultipartPlan.PassHandler) = new MultiPartPlanifier(intent, pass)
}

/** Provides a MultiPart decoding plan that won't buffer to disk while parsing the request */
object MemoryMultiPartDecoder {
  def apply(intent: cycle.Plan.Intent): cycle.Plan = MemoryMultiPartDecoder(intent, MultipartPlan.DefaultPassHandler)
  def apply(intent: cycle.Plan.Intent, pass: MultipartPlan.PassHandler) = new MultiPartPlanifier(intent, pass) {
      override protected val useDisk = false
  }
}