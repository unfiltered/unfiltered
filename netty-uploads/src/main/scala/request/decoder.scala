package unfiltered.netty.request

import org.jboss.{netty => jnetty}  // 3.x
import jnetty.handler.codec.http.{HttpRequest => NHttpRequest,HttpChunk => NHttpChunk,HttpMessage => NHttpMessage}
import org.jboss.netty.channel._

import io.{netty => ionetty}        // 4.x
import ionetty.handler.codec.http.{HttpPostRequestDecoder => IOHttpPostRequestDecoder}
import ionetty.handler.codec.http.{DefaultHttpDataFactory => IODefaultHttpDataFactory}
import ionetty.handler.codec.http.{InterfaceHttpData => IOInterfaceHttpData}
import ionetty.handler.codec.http.{Attribute => IOAttribute}
import ionetty.handler.codec.http.{FileUpload => IOFileUpload}

import org.clapper.avsl.Logger

/** A PostDecoder wraps a HttpPostRequestDecoder which is available in netty 4 onwards. 
    We implicitly convert a netty 3 HttpRequest to a netty 4 HttpRequest to enable us to use 
    the new multi-part decoding features (until such time as netty 4 is officially released 
    and unfiltered uses it by default). Decoding chunked messages, while supported by netty 4 
    is not implemented here, so use of a HttpChunkAggregator in the handler pipeline is 
    mandatory for now. */
class PostDecoder(req: NHttpRequest, useDisk: Boolean = true) {

  /** Enable implicit conversion between netty 3.x and 4.x. One day this won't be needed any more :) */
  import Implicits._

  import scala.collection.JavaConversions._

  /** Build a post decoder and parse the request. This only works with POST requests. */
  private lazy val decoder = try {
    val factory = new IODefaultHttpDataFactory(useDisk)
    Some(new IOHttpPostRequestDecoder(factory, req))
  } catch {
    /** Q Would it be more useful to throw errors here? */
    case e: IOHttpPostRequestDecoder.ErrorDataDecoderException => None
    /** GET method. Can't create a decoder. */
    case e: IOHttpPostRequestDecoder.IncompatibleDataDecoderException => None
  }

  /** Whether the request is multi-part */
  def isMultipart: Boolean = decoder.map(_.isMultipart).getOrElse(false)

  /** Returns a collection containing all the parts of the parsed request */
  def items: List[IOInterfaceHttpData] = decoder.map(_.getBodyHttpDatas.toList).getOrElse(List())

  /** Returns a collection of uploaded files found in the parsed request */
  def fileUploads = items collect { case file: IOFileUpload => file }

  /** Returns a collection of all the parts excluding file uploads from the parsed request */
  def parameters = items collect { case param: IOAttribute => param }

  def offer(chunk: NHttpChunk) = decoder.map(_.offer(chunk))
}

object PostDecoder{
  def apply(req: NHttpRequest, useDisk: Boolean = true): Option[PostDecoder] = {
    val postDecoder = new PostDecoder(req, useDisk)
    postDecoder.decoder match {
      case Some(dec) => Some(postDecoder)
      case _ => None
    }
  }
}

/** Provides storage for state when attached to a ChannelHandlerContext */
case class MultiPartChannelState(
  readingChunks: Boolean = false,
  originalReq: Option[NHttpRequest] = None,
  decoder: Option[PostDecoder] = None
)

/** Enriches a netty plan with multipart decoding capabilities. */
trait AbstractMultiPartDecoder {
    /** Whether the ChannelBuffer used in decoding is allowed to write to disk */
    protected val useDisk: Boolean = true
    /** Called when there are no more chunks to process. Implementation differs between cycle and async plans */
    protected def complete(ctx: ChannelHandlerContext, e: MessageEvent)

    /** For help debugging */
    private val logger = Logger(this.getClass.getCanonicalName)

    /** Provides multipart request handling common to both cycle and async plans */
    protected def handle(ctx: ChannelHandlerContext, e: MessageEvent) = {

      /** Some debugging stuff */
      val objId = System.identityHashCode(this).toHexString
      val className = this.getClass.getName

      /** Maintain state as exlained here:
          http://docs.jboss.org/netty/3.2/api/org/jboss/netty/channel/ChannelHandlerContext.html */
      val channelState = ctx getAttachment match {
        case s: MultiPartChannelState => s
        case _ => MultiPartChannelState()
      }

      e.getMessage() match {
        /** Should match the first chunk of multipart request */
        case request: NHttpRequest =>
          logger.debug("%s @%s Received HttpRequest: %s".format(className, objId, request))
          if(!channelState.readingChunks) {
            /** Initialise the decoder */
            logger.debug("%s @%s Start reading chunks...".format(className, objId))
            val decoder = PostDecoder(request, useDisk)
            /** Store the initial state */
            ctx.setAttachment(MultiPartChannelState(channelState.readingChunks, Some(request), decoder))
            if(request.isChunked) {
              /** Update the state to readingChunks = true */
              logger.debug("%s @%s Request is chunked...".format(className, objId))
              ctx.setAttachment(MultiPartChannelState(true, Some(request), decoder))
            } else {
              /** This is not a chunked request (could be an aggregated multipart request), 
              so we should have received it all. Behave like a regular netty plan. */
              logger.debug("Request is not chunked...")
              complete(ctx, e)
            }
          } else {
            /** Shouldn't get here */
            error("%s @%s HttpRequest received while reading chunks: %s".format(className, objId, request))
          }
        /** Should match subsequent chunks of the same request */
        case chunk: NHttpChunk =>
          logger.debug("%s @%s Received HttpChunk: %s".format(className, objId, chunk))
          /** Should be reading chunks here */
          if(channelState.readingChunks) {
            /** Give the chunk to the decoder */
            logger.debug("%s @%s Continue to read chunks...".format(className, objId))
            channelState.decoder.map(_.offer(chunk))
            if(chunk.isLast) {
              /** This was the last chunk so we can finish */
              logger.debug("%s @%s Reached last chunk.".format(className, objId))
              ctx.setAttachment(channelState.copy(readingChunks=false))
              complete(ctx, e)
            }
          } else {
              /** It shouldn't be possible to get here, but for some reason when both cycle.MultiPartDecoder plans
              and async.MultiPartDecoder plans are used in the same pipeline the first one handles the request even
              though the Path doesn't match, then a chunk gets passed upstream, so we end up here. @see MixedPlanSpec */
            logger.debug("%s @%s HttpChunk received when not expected in: %s \nState: %s \nChannelBuffer: %s\nStack trace:"
              .format(className, objId,this.getClass.getName, channelState, chunk.getContent))
            error("HttpChunk received when not expected.")
          }
        case msg => error("Unexpected message type from upstream: %s".format(msg))
      }
    }
}
