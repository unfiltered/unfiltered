package unfiltered.request.uploads.netty

import org.jboss.{netty => jnetty}  // 3.x
import jnetty.handler.codec.http.{HttpRequest => NHttpRequest}

import io.{netty => ionetty}        // 4.x
import ionetty.handler.codec.http.{HttpPostRequestDecoder => IOHttpPostRequestDecoder}
import ionetty.handler.codec.http.{DefaultHttpDataFactory => IODefaultHttpDataFactory}
import ionetty.handler.codec.http.{InterfaceHttpData => IOInterfaceHttpData}
import ionetty.handler.codec.http.{Attribute => IOAttribute}
import ionetty.handler.codec.http.{FileUpload => IOFileUpload}

/** A PostDecoder wraps a HttpPostRequestDecoder which is available in netty 4 onwards. 
    We implicitly convert a netty 3 HttpRequest to a netty 4 HttpRequest to enable us to use 
    the new multi-part decoding features (until such time as netty 4 is officially released 
    and unfiltered uses it by default). Decoding chunked messages, while supported by netty 4 
    is not implemented here, so use of a HttpChunkAggregator in the handler pipeline is 
    mandatory for now. */
class PostDecoder(req: NHttpRequest) {

  /** Enable implicit conversion between netty 3.x and 4.x. One day this won't be needed any more :) */
  import Implicits._

  import scala.collection.JavaConversions._

  /** Build a post decoder and parse the request. This only works with POST requests. */
  private lazy val decoder = try {
    val factory = new IODefaultHttpDataFactory(IODefaultHttpDataFactory.MINSIZE)
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
}

object PostDecoder{
  def apply(req: NHttpRequest) = new PostDecoder(req)
}