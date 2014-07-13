
package unfiltered.netty.cycle

import io.netty.handler.codec.http.{
  FullHttpRequest,
  HttpContent,
  HttpRequest => NettyHttpRequest,
  HttpResponse }
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import io.netty.channel.{ ChannelHandlerContext, ChannelInboundHandlerAdapter }
import io.netty.channel.ChannelHandler.Sharable

import unfiltered.netty.{ ExceptionHandler, ReceivedMessage, RequestBinding, ServerErrorResponse }
import unfiltered.response.{ Pass, ResponseFunction }
import unfiltered.request.HttpRequest
import unfiltered.util.control.NonFatal

import scala.language.reflectiveCalls

object Plan {
  type Intent = unfiltered.Cycle.Intent[ReceivedMessage, HttpResponse]
}

/** Object to facilitate Plan.Intent definitions. Type annotations
 *  are another option. */
object Intent {
  def apply(intent: Plan.Intent) = intent
}

/** A Netty Plan for request cycle handling. */
@Sharable
trait Plan extends ChannelInboundHandlerAdapter
  with ExceptionHandler {

  def intent: Plan.Intent

  def executeIntent(thunk: => Unit)

  def executeResponse(thunk: => Unit)

  def shutdown()

  def catching(ctx: ChannelHandlerContext)(thunk: => Unit) {
    try { thunk } catch {
      case NonFatal(e) => onException(ctx, e)
    }
  }

  private lazy val guardedIntent = intent.fold(
    (req: HttpRequest[ReceivedMessage]) =>
      req.underlying.context.fireChannelRead(req.underlying.message),
    (req: HttpRequest[ReceivedMessage],
     rf: ResponseFunction[HttpResponse]) =>
      executeResponse {
        catching(req.underlying.context) {
          req.underlying.respond(rf)
        }
      }
  )

  final override def channelReadComplete(ctx: ChannelHandlerContext) =
    ctx.flush()
  
  override def channelRead(ctx: ChannelHandlerContext, msg: java.lang.Object ): Unit =
    msg match {
      case req: NettyHttpRequest =>
        catching(ctx) {
          executeIntent {
            catching(ctx) {
              guardedIntent(
                new RequestBinding(ReceivedMessage(req, ctx, msg))
              )
            }
          }
        }
      // fixme(doug): I don't think this will ever be the case as we are now always adding the aggregator to the pipeline
      case chunk: HttpContent => ctx.fireChannelRead(chunk)
      case frame: WebSocketFrame => ctx.fireChannelRead(frame)
      // fixme(doug): Should we define an explicit exception to catch for this
      case ue => sys.error("Received unexpected message type from upstream: %s"
                           .format(ue))
    }
}

object Planify {
  @Sharable
  class Planned(intentIn: Plan.Intent) extends Plan
    with ThreadPool with ServerErrorResponse {
    val intent = intentIn
  }
  def apply(intentIn: Plan.Intent) = new Planned(intentIn)
}
