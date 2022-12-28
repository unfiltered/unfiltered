package unfiltered.netty.request

import unfiltered.netty.{ ExceptionHandler, ReceivedMessage, RequestBinding }
import io.netty.channel.{ ChannelHandlerContext, ChannelInboundHandler }
import io.netty.handler.codec.http.{
  HttpRequest,
  HttpContent,
  HttpUtil,
  LastHttpContent
}
import io.netty.handler.codec.http.multipart.{
  Attribute,
  DefaultHttpDataFactory,
  FileUpload,
  HttpPostRequestDecoder,
  InterfaceHttpData
}
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.NotEnoughDataDecoderException
import io.netty.util.{AttributeKey, AttributeMap}
import scala.jdk.CollectionConverters._
import scala.util.Try

/** A PostDecoder wraps a HttpPostRequestDecoder. */
class PostDecoder(req: HttpRequest, useDisk: Boolean = true) {

  /** Build a post decoder and parse the request. This only works with POST requests. */
  private lazy val decoder: Option[HttpPostRequestDecoder] =// [InterfaceHttpPostRequestDecoder] in 4.0.14
    try Some(new HttpPostRequestDecoder(new DefaultHttpDataFactory(useDisk), req)) catch {
      /** Would it be more useful to throw errors here? */
      case e: HttpPostRequestDecoder.ErrorDataDecoderException =>
        None
    }

  /** Whether the request is multi-part */
  lazy val isMultipart: Boolean = decoder.exists(_.isMultipart)

  /** Returns a collection containing all the parts of the parsed request */
  lazy val items: List[InterfaceHttpData] = {
    try decoder.map(_.getBodyHttpDatas.asScala.toList).getOrElse(Nil) catch {
      case e: NotEnoughDataDecoderException =>
        sys.error("Tried to decode a multipart request before it was fully received. Make sure there is either a HttpChunkAggregator in the handler pipeline e.g. _.chunked() or use a MultiPartDecoder plan.")
      case e: Exception /*potentially thrown if items is called after destroy is called*/ =>
        sys.error("unexpected error in items %s" format e)
    }
  }

  /** Returns a collection of uploaded files found in the parsed request */
  lazy val fileUploads = items collect { case file: FileUpload => file }

  /** Returns a collection of all the parts excluding file uploads from the parsed request */
  lazy val parameters = items collect { case param: Attribute => param }

  /** Add a received HttpContent to the decoder */
  def offer(chunk: HttpContent) = decoder.map(_.offer(chunk))

  /** Clean all HttpDatas (on Disk) for the current request */
  def cleanFiles() = decoder.map(_.cleanFiles())

  /** Release underlying resources. this should cleanFiles, undecodedChunk, release bodyListHttpData.
   *  Once called, this decoder's behavior is undefined. (should submit patch to netty project to check decoder.isDestoryed) */
  def destroy() = decoder.map(_.destroy())
}

object PostDecoder {

  // todo(doug): reduce scope
  val State = AttributeKey.valueOf[MultiPartChannelState]("PostDecoder.state")

  def apply(req: HttpRequest, useDisk: Boolean = true): Option[PostDecoder] = {
    val postDecoder = new PostDecoder(req, useDisk)
    // only return ref if we were able to successfully create an netty decoder
    postDecoder.decoder.map(_ => postDecoder)
  }
}

/** Provides storage for state when attached to a ChannelHandlerContext.
 *  todo(doug): this this be better represented as cases of a sealed trait? */
private [netty] case class MultiPartChannelState(
  readingChunks: Boolean = false,
  originalReq: Option[HttpRequest] = None,
  decoder: Option[PostDecoder] = None)

object MultiPartPass {
  /** A pass handler serves as a means to forward a request upstream for
   *  unhandled patterns and protocol messages */
  type PassHandler = (ChannelHandlerContext, java.lang.Object) => Unit

  /** A default implementation of a PassHandler which sends the message upstream. todo(doug): does anything need released here? */
  val DefaultPassHandler = ({ _.fireChannelRead(_) }: PassHandler)
}

/** Enriches a netty plan with multipart decoding capabilities. */
trait AbstractMultiPartDecoder extends CleanUp {

  /** A pass handler that should be supplied when the plan is created. Default is to send upstream */
  def pass: MultiPartPass.PassHandler

  /** Called when there are no more chunks to process. Implementation differs between cycle and async plans */
  protected def complete(ctx: ChannelHandlerContext, msg: java.lang.Object)(cleanUp: => Unit): Unit

  /** Determine whether the intent could handle the request (without executing it). If so execute @param body, otherwise pass */
  // TODO: remove msg param as its part of the request binding
  protected def handleOrPass(ctx: ChannelHandlerContext, msg: java.lang.Object, binding: RequestBinding)(body: => Unit): Unit

  /** Whether the ChannelBuffer used in decoding is allowed to write to disk */
  protected val useDisk: Boolean = true

  /** Provides multipart request handling common to both cycle and async plans.
      Should be called by onMessageReceived. */
  protected def upgrade(ctx: ChannelHandlerContext, msg: java.lang.Object) =
    msg match {
      case request: HttpRequest =>
        val binding = new RequestBinding(ReceivedMessage(request, ctx, request))
        binding match {
          // Should match the initial multipart request
          case MultiPart(_) =>
            // Determine whether the request is destined for this plan's intent and if not, pass
            handleOrPass(ctx, request, binding) {
              // The request is destined for this intent, so start to handle it
              start(request, Helpers.channelStateOrCreate(ctx), ctx, request)
            }
          // The request is not valid for this plan so send sendUpstream
          case _ =>
            pass(ctx, request)
        }

      // Should match subsequent chunks of the same request
      case chunk: HttpContent =>
        val channelState = Helpers.channelStateOrCreate(ctx)
        channelState.originalReq match {
          // Ensure that multipart handling was started for the request
          case Some(request) =>
            val binding = new RequestBinding(ReceivedMessage(request, ctx, chunk))
            // Determine whether the chunk is destined for this plan's intent and if not, pass
            handleOrPass(ctx, chunk, binding) {
              // The chunk is destined for this intent, so handle it
              continue(chunk, channelState, ctx, chunk)
            }
          case _ =>
            pass(ctx, chunk)
        }
    }

  /** Sets up for handling a multipart request */
  protected def start(
    request: HttpRequest,
    channelState: MultiPartChannelState,
    ctx: ChannelHandlerContext,
    msg: java.lang.Object) = { // TODO: remove msg param. its probably not needed
    if (!channelState.readingChunks) {
      // Initialise the decoder
      val decoder = PostDecoder(request, useDisk)
      // Store the initial state
      ctx.asInstanceOf[AttributeMap].attr(PostDecoder.State).set(MultiPartChannelState(channelState.readingChunks, Some(request), decoder))
      if (HttpUtil.isTransferEncodingChunked(request)) {
        // Update the state to readingChunks = true
        ctx.asInstanceOf[AttributeMap].attr(PostDecoder.State).set(MultiPartChannelState(true, Some(request), decoder))
      } else {
        // This is not a chunked request (could be an aggregated multipart request),
        // so we should have received it all. Behave like a regular netty plan.
        complete(ctx, msg)(cleanUp(ctx))
      }
    } else {
      // Shouldn't get here
      sys.error(s"HttpRequest received while reading chunks: ${request}")
    }
  }

  /** Handles incoming chunks belonging to the original request */
  protected def continue(
    chunk: HttpContent,
    channelState: MultiPartChannelState,
    ctx: ChannelHandlerContext,
    msg: java.lang.Object) = { // todo: remove msg param, its probably no longer needed
    // Should be reading chunks here
    if (channelState.readingChunks) {
      // Give the chunk to the decoder
      // fixme(doug): should probably catch ErrorDataDecoderException here
      channelState.decoder.map(_.offer(chunk))
      if (chunk.isInstanceOf[LastHttpContent]) {
        // This was the last chunk so we can complete
        ctx.asInstanceOf[AttributeMap].attr(PostDecoder.State).set(channelState.copy(readingChunks=false))
        complete(ctx, msg)(cleanUp(ctx))
      }
    } else {
      // It shouldn't be possible to get here
      sys.error("HttpChunk received when not expected.")
    }
  }
}

// todo(doug): factor so we don't need this
object Helpers {
  /** Retrieve the channel state */
  def channelState(ctx: ChannelHandlerContext): Option[MultiPartChannelState] =
    Option(ctx.asInstanceOf[AttributeMap].attr(PostDecoder.State).get)
  /** Retrieve the channel state or create a new one */
  def channelStateOrCreate(ctx: ChannelHandlerContext): MultiPartChannelState =
    channelState(ctx).getOrElse(MultiPartChannelState())
}

// todo(doug): reduce scope
trait CleanUp {
  import Helpers._

  /** Remove the channel state and destroy the request's post decoder */
  def cleanUp(ctx: ChannelHandlerContext): Unit = {
    // release resources
    channelState(ctx).foreach(_.decoder.foreach(_.destroy()))
    // remove attr from context state
    ctx.asInstanceOf[AttributeMap].attr(PostDecoder.State).remove()
  }

  /** Erase any temporary data that may be on disk */
  def cleanFiles(ctx: ChannelHandlerContext): Unit =
    channelState(ctx).foreach(_.decoder.foreach(_.cleanFiles()))
}

/** Ensure any state cleanup is done when an exception is caught */
trait TidyExceptionHandler extends ExceptionHandler with CleanUp { self: ChannelInboundHandler =>
  override def exceptionCaught(ctx: ChannelHandlerContext, thrown: Throwable) = {
    Try {
      cleanUp(ctx)
    }
    super.exceptionCaught(ctx, thrown)
  }
}
