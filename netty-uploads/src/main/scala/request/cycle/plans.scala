package unfiltered.netty.cycle

import unfiltered.netty
import unfiltered.netty._
import unfiltered.netty.request._
import unfiltered.response._
import unfiltered.response.{ResponseFunction, Pass => UPass}
import unfiltered.request.{HttpRequest, POST}
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.{HttpRequest=>NHttpRequest,
                                           HttpResponse=>NHttpResponse,
                                           HttpChunk => NHttpChunk}

/** Provides useful defaults for Passing */
object MultipartPlan {
  val Pass  = ({
    case _ => UPass
  }: MultiPartIntent)

  type Intent = PartialFunction[HttpRequest[ReceivedMessage], MultiPartIntent] //unfiltered.Cycle.Intent[ReceivedMessage, MultiPartIntent]
  type MultiPartIntent = PartialFunction[MultiPartCallback, ResponseFunction[NHttpResponse]]
}

/** Enriches an async netty plan with multipart decoding capabilities. */
trait MultiPartDecoder extends SimpleChannelUpstreamHandler with AbstractMultiPartDecoder with TidyExceptionHandler {

  def intent: MultipartPlan.Intent

  def catching(ctx: ChannelHandlerContext)(thunk: => Unit) {
    try { thunk } catch {
      case e => onException(ctx, e)
    }
  }

  /** Decide if the intent could handle the request */
  protected def handleOrPass(ctx: ChannelHandlerContext, e: MessageEvent, binding: RequestBinding)(thunk: => Unit) = {
    intent.orElse({ case _ => MultipartPlan.Pass }: MultipartPlan.Intent)(binding) match {
      case MultipartPlan.Pass => pass(ctx, e)
      case _ => thunk
    }
  }

  /** Called when the chunked request has been fully received. Executes the intent */
  protected def complete(ctx: ChannelHandlerContext, e: MessageEvent) = {
    val channelState = Helpers.channelState(ctx)
    channelState.originalReq match {
      case Some(req) =>
        val msg = ReceivedMessage(req, ctx, e)
        val multiBinding = new MultiPartBinding(channelState.decoder, msg)
        val binding = new RequestBinding(msg)

        catching(ctx) {
          executeIntent {
            intent.orElse({ case _ => MultipartPlan.Pass }: MultipartPlan.Intent)(binding) match {
              case MultipartPlan.Pass => MultipartPlan.Pass
              case multipartIntent => executeResponse {
                multiBinding.respond(multipartIntent(Decode(multiBinding)))
              }
            }
          }
        }
      case _ => sys.error("Original request missing from channel state %s".format(ctx))
    }
  }

  def executeIntent(thunk: => Unit)
  def executeResponse(thunk: => Unit)
  def shutdown()

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) = upgrade(ctx, e)
  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) = cleanFiles(ctx)
}

class MultiPartPlanifier(val intent: MultipartPlan.Intent, val pass: MultiPartPass.PassHandler)
extends MultiPartDecoder with ThreadPool with ServerErrorResponse

/** Provides a MultiPart decoding plan that may buffer to disk while parsing the request */
object MultiPartDecoder {
  def apply(intent: MultipartPlan.Intent): MultiPartDecoder = MultiPartDecoder(intent, MultiPartPass.DefaultPassHandler)
  def apply(intent: MultipartPlan.Intent, pass: MultiPartPass.PassHandler) = new MultiPartPlanifier(intent, pass)
}

/** Provides a MultiPart decoding plan that won't buffer to disk while parsing the request */
object MemoryMultiPartDecoder {
  def apply(intent: MultipartPlan.Intent): MultiPartDecoder = MemoryMultiPartDecoder(intent, MultiPartPass.DefaultPassHandler)
  def apply(intent: MultipartPlan.Intent, pass: MultiPartPass.PassHandler) = new MultiPartPlanifier(intent, pass) {
      override protected val useDisk = false
  }
}
