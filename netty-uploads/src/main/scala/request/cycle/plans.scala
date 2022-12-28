package unfiltered.netty.cycle

import unfiltered.netty.{ ReceivedMessage, RequestBinding, ServerErrorResponse }
import unfiltered.netty.request.{ AbstractMultiPartDecoder, Decode, Helpers, MultiPartBinding, MultiPartCallback, MultiPartPass, TidyExceptionHandler }
import unfiltered.request.HttpRequest
import unfiltered.response.{ ResponseFunction, Pass => UPass }
import scala.util.control.NonFatal

import io.netty.channel.{ ChannelHandlerContext, ChannelInboundHandlerAdapter }
import io.netty.channel.ChannelHandler.Sharable
import io.netty.handler.codec.http.HttpResponse

/** Provides useful defaults for Passing
 *  note(*): perhaps this could be reimplemented in terms of a kit */
object MultipartPlan {
  type Intent = PartialFunction[HttpRequest[ReceivedMessage], MultiPartIntent] //unfiltered.Cycle.Intent[ReceivedMessage, MultiPartIntent]
  type MultiPartIntent = PartialFunction[MultiPartCallback, ResponseFunction[HttpResponse]]
  val Pass: MultiPartIntent = { case _ => UPass }
  val PassAlong: Intent = { case _ => Pass }
}

/** Enriches an async netty plan with multipart decoding capabilities. */
@Sharable
trait MultiPartDecoder extends ChannelInboundHandlerAdapter
  with AbstractMultiPartDecoder
  with TidyExceptionHandler {

  def intent: MultipartPlan.Intent

  def executeIntent(thunk: => Unit): Unit

  def executeResponse(thunk: => Unit): Unit

  def shutdown(): Unit

  def catching(ctx: ChannelHandlerContext)(thunk: => Unit): Unit = {
    try { thunk } catch {
      case NonFatal(e) =>
        onException(ctx, e)
    }
  }

  /** Decide if the intent could handle the request */
  override protected def handleOrPass(
    ctx: ChannelHandlerContext, msg: java.lang.Object, binding: RequestBinding)(thunk: => Unit) = {
    intent.orElse(MultipartPlan.PassAlong)(binding) match {
      case MultipartPlan.Pass => pass(ctx, msg)
      case _ => thunk
    }
  }

  /** Called when the chunked request has been fully received. Executes the intent */
  override protected def complete(ctx: ChannelHandlerContext, nmsg: java.lang.Object)(cleanUp: => Unit) = {
    val channelState = Helpers.channelStateOrCreate(ctx)
    channelState.originalReq match {
      case Some(req) =>
        val msg = ReceivedMessage(req, ctx, nmsg)
        val multiBinding = new MultiPartBinding(channelState.decoder, msg)
        val binding = new RequestBinding(msg)
        catching(ctx) {
          executeIntent {
            intent.orElse(MultipartPlan.PassAlong)(binding) match {
              case MultipartPlan.Pass =>
                // fixme(doug): this isn't really responding here?
                MultipartPlan.Pass
              case multipartIntent =>
                executeResponse {
                  multiBinding.respond(multipartIntent(Decode(multiBinding)))
                }
            }
            cleanUp            
          }
        }
      case _ =>
        sys.error(s"Original request missing from channel state ${ctx}")
    }
  }

  final override def channelRead(
    ctx: ChannelHandlerContext, obj: java.lang.Object) =
      upgrade(ctx, obj)

  final override def channelInactive(ctx: ChannelHandlerContext) = {
    cleanFiles(ctx)
    ctx.fireChannelInactive()
  }
}

/** Handles MultiPart form-encoded requests within the context
 *  of a request/response cycle on an unbounged CachedThreadPool executor */
@Sharable
class MultiPartPlanifier(
  val intent: MultipartPlan.Intent,
  val pass: MultiPartPass.PassHandler)
  extends MultiPartDecoder with ThreadPool with ServerErrorResponse

/** Provides a MultiPart decoding plan that may buffer to disk while parsing the request */
object MultiPartDecoder {
  def apply(intent: MultipartPlan.Intent): MultiPartDecoder =
    MultiPartDecoder(intent, MultiPartPass.DefaultPassHandler)
  def apply(intent: MultipartPlan.Intent, pass: MultiPartPass.PassHandler) =
    new MultiPartPlanifier(intent, pass)
}

/** Provides a MultiPart decoding plan that won't buffer to disk while parsing the request */
object MemoryMultiPartDecoder {
  def apply(intent: MultipartPlan.Intent): MultiPartDecoder =
    MemoryMultiPartDecoder(intent, MultiPartPass.DefaultPassHandler)
  def apply(intent: MultipartPlan.Intent, pass: MultiPartPass.PassHandler) =
    new MultiPartPlanifier(intent, pass) {
      override protected val useDisk = false
    }
}
