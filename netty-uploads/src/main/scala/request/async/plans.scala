package unfiltered.netty.async

import unfiltered.netty.async
import unfiltered.netty.ReceivedMessage
import unfiltered.netty.RequestBinding
import unfiltered.netty.ServerErrorResponse
import unfiltered.netty.request.AbstractMultiPartDecoder
import unfiltered.netty.request.Decode
import unfiltered.netty.request.Helpers
import unfiltered.netty.request.MultiPartBinding
import unfiltered.netty.request.MultiPartCallback
import unfiltered.netty.request.MultiPartPass
import unfiltered.netty.request.TidyExceptionHandler
import unfiltered.request.HttpRequest
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelHandler.Sharable

/** Provides useful defaults for Passing */
object MultipartPlan {
  type Intent = PartialFunction[HttpRequest[ReceivedMessage], MultiPartIntent]
  type MultiPartIntent = PartialFunction[MultiPartCallback, Unit]
  val Pass: MultiPartIntent = { case _ => () }
  val PassAlong: Intent = { case _ => Pass }
}

/** Enriches an async netty plan with multipart decoding capabilities. */
@Sharable
trait MultiPartDecoder extends async.Plan with AbstractMultiPartDecoder with TidyExceptionHandler {

  def intent: MultipartPlan.Intent

  /** Decide if the intent could handle the request */
  protected def handleOrPass(ctx: ChannelHandlerContext, e: java.lang.Object, binding: RequestBinding)(
    thunk: => Unit
  ): Unit = {
    intent.orElse(MultipartPlan.PassAlong)(binding) match {
      case MultipartPlan.Pass => pass(ctx, e)
      case _ => thunk
    }
  }

  /** Called when the chunked request has been fully received. Executes the intent */
  protected def complete(ctx: ChannelHandlerContext, nmsg: java.lang.Object)(cleanUp: => Unit): Unit = {
    val channelState = Helpers.channelStateOrCreate(ctx)
    val res = channelState.originalReq match {
      case Some(req) =>
        val msg = ReceivedMessage(req, ctx, nmsg)
        val multiBinding = new MultiPartBinding(channelState.decoder, msg)
        val binding = new RequestBinding(msg)
        intent.orElse(MultipartPlan.PassAlong)(binding) match {
          case MultipartPlan.Pass =>
            // fixme(doug): this isn't really responding here?
            MultipartPlan.Pass
          case multipartIntent =>
            multipartIntent(Decode(multiBinding))
        }
        cleanUp
      case _ =>
        sys.error(s"Original request missing from channel state ${ctx}")
    }
    cleanUp
    res
  }

  final override def channelRead(ctx: ChannelHandlerContext, obj: java.lang.Object): Unit =
    upgrade(ctx, obj)

  final override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    cleanFiles(ctx)
    ctx.fireChannelInactive()
  }
}

@Sharable
class MultiPartPlanifier(val intent: MultipartPlan.Intent, val pass: MultiPartPass.PassHandler)
    extends MultiPartDecoder
    with ServerErrorResponse

/** Provides a MultiPart decoding plan that may buffer to disk while parsing the request */
object MultiPartDecoder {
  def apply(intent: MultipartPlan.Intent): async.Plan =
    MultiPartDecoder(intent, MultiPartPass.DefaultPassHandler)
  def apply(intent: MultipartPlan.Intent, pass: MultiPartPass.PassHandler) =
    new MultiPartPlanifier(intent, pass)
}

/** Provides a MultiPart decoding plan that won't buffer to disk while parsing the request */
object MemoryMultiPartDecoder {
  def apply(intent: MultipartPlan.Intent): async.Plan =
    MemoryMultiPartDecoder(intent, MultiPartPass.DefaultPassHandler)
  def apply(intent: MultipartPlan.Intent, pass: MultiPartPass.PassHandler): MultiPartPlanifier =
    new MultiPartPlanifier(intent, pass) {
      override protected val useDisk = false
    }
}
