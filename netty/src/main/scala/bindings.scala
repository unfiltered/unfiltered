package unfiltered.netty

import unfiltered.Async
import unfiltered.response.{ 
  BaseResponseFunction, ResponseFunction, ResponseHeader, ContentLength, 
  ContentType, BaseHttpResponse, HttpResponse, Pass }
import unfiltered.request.{ Charset, HttpRequest, POST, PUT, RequestContentType, & }

import io.netty.buffer.{ ByteBufInputStream, ByteBufOutputStream, Unpooled }
import io.netty.channel.{ ChannelFuture, ChannelFutureListener, ChannelHandlerContext,
  DefaultFileRegion }
import io.netty.handler.codec.http.{
  DefaultFullHttpResponse, FullHttpRequest, FullHttpResponse, HttpContent,
  HttpResponse => NettyHttpResponse, DefaultHttpResponse, HttpHeaders, 
  HttpRequest => NettyHttpRequest, HttpResponseStatus, HttpVersion,
  LastHttpContent }
import io.netty.handler.ssl.SslHandler
import io.netty.handler.stream.ChunkedFile
import io.netty.util.ReferenceCountUtil
import java.io.{ BufferedReader, ByteArrayOutputStream, InputStreamReader, 
  File, RandomAccessFile }
import java.net.{ InetSocketAddress, URLDecoder }
import java.nio.charset.{ Charset => JNIOCharset }
import javax.activation.MimetypesFileTypeMap

import scala.collection.JavaConverters._

object Mimes {
  private lazy val types =
    new MimetypesFileTypeMap(getClass.getResourceAsStream("/mime.types"))
  def apply(path: String) = types.getContentType(path)
}

object HttpConfig {
   val DEFAULT_CHARSET = "UTF-8"
}

class RequestBinding(msg: ReceivedMessage)
  extends HttpRequest(msg) with Async.Responder[FullHttpResponse] {

  private val req = msg.request

  private val content = msg.content

  private lazy val params = queryParams ++ bodyParams

  private def queryParams = req.getUri.split("\\?", 2) match {
    case Array(_, qs) => URLParser.urldecode(qs)
    case _ => Map.empty[String,Seq[String]]
  }

  private def bodyParams = (this, content) match {
    case ((POST(_) | PUT(_)) & RequestContentType(ct), Some(content))
      if ct.contains("application/x-www-form-urlencoded") =>
      URLParser.urldecode(content.content.toString(JNIOCharset.forName(charset)))
    case _ =>
      Map.empty[String,Seq[String]]
  }

  private def charset = Charset(this).getOrElse {
    HttpConfig.DEFAULT_CHARSET
  }

  lazy val inputStream =
    new ByteBufInputStream(content.map(_.content).getOrElse(Unpooled.EMPTY_BUFFER))

  lazy val reader =
    new BufferedReader(new InputStreamReader(inputStream, charset))

  def protocol = req.getProtocolVersion match {
    case HttpVersion.HTTP_1_0 => "HTTP/1.0"
    case HttpVersion.HTTP_1_1 => "HTTP/1.1"
    case _ => "???"
  }

  def method = req.getMethod.toString.toUpperCase

  // todo should we call URLDecoder.decode(uri, charset) on this here?
  def uri = req.getUri

  def parameterNames = params.keySet.iterator

  def parameterValues(param: String) = params.getOrElse(param, Seq.empty)

  def headerNames = req.headers.names.iterator.asScala

  def headers(name: String) = req.headers.getAll(name).iterator.asScala

  def isSecure = msg.isSecure

  def remoteAddr = msg.context.channel.remoteAddress.asInstanceOf[InetSocketAddress].getAddress.getHostAddress

  def respond(rf: ResponseFunction[FullHttpResponse]) =
    underlying.respond(rf)
}

/** Extension of basic request binding to expose Netty-specific attributes */
case class ReceivedMessage(
  request: NettyHttpRequest,
  context: ChannelHandlerContext
) {
  def content: Option[HttpContent] =
    request match {
      case has: HttpContent => Some(has)
      case not => None
    }
  
  private def closeOrKeepAlive = new unfiltered.response.Responder[FullHttpResponse] {
    def respond(res: HttpResponse[FullHttpResponse]) {
      res.outputStream.close()
      val hasContentLength = HttpHeaders.isContentLengthSet(res.underlying)
      val withKeepAlive = 
        if (isKeepAlive) unfiltered.response.Connection("Keep-Alive")
        else unfiltered.response.Connection("close")
      (
        if (hasContentLength) withKeepAlive
        else {
          withKeepAlive ~> 
          unfiltered.response.ContentLength(res.underlying.content().readableBytes().toString)
        }
      )(res)
    }
  }
 
  def finishResponse(future: ChannelFuture) {
    future.addListener(new ChannelFutureListener {
      def operationComplete(f: ChannelFuture) {
        content.map(ReferenceCountUtil.release)
      }
    })
    if (!isKeepAlive)
      future.addListener(ChannelFutureListener.CLOSE)
  }
  
  def isKeepAlive = HttpHeaders.isKeepAlive(request)

  def isSecure =
    Option(context.pipeline.get(classOf[SslHandler])).isDefined

  /** Binds a Netty HttpResponse res to Unfiltered's HttpResponse to apply any
   * response function to it. */
  def response[T <: FullHttpResponse](res: T)(rf: ResponseFunction[T]) =
    rf(new ResponseBinding(res)).underlying

  /** @return a new Netty FullHttpResponse bound to an Unfiltered HttpResponse */
  lazy val defaultResponse = response(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK))_
  // Required for sendFile, since writing a FullHttpResponse implies the request is done
  private lazy val baseResponse = new BaseResponseBinding(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK))

  /** Applies rf to a new `defaultResponse` and writes it out */
  def respond: (ResponseFunction[FullHttpResponse] => Unit) = {
    case Pass =>
      context.fireChannelRead(request)
    case rf =>
      val writeFuture = context.writeAndFlush(defaultResponse(rf ~> closeOrKeepAlive))
      finishResponse(writeFuture)
  }

  def sendFile(file: File)(headers: BaseResponseFunction[Any]) {
    val size = file.length
    val heads = ContentLength(size.toString) ~> ContentType(Mimes(file.getCanonicalPath))
    
    // apply user headers after default ones
    context.write((headers ~> heads)(baseResponse))
    
    val raf = new RandomAccessFile(file.getCanonicalPath, "r")
    // For standard Http this will use sendfile if available
    val payload =
      if (isSecure) new ChunkedFile(raf, 0, size, 8192) // ChunkedStream.DEFAULT_CHUNK_SIZE
      else new DefaultFileRegion(raf.getChannel, 0, size)

    context.write(payload)
    
    val lastContent = context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
   
    finishResponse(lastContent)
  }
}

class BaseResponseBinding[U <: NettyHttpResponse](val underlying: U) extends BaseHttpResponse[U] {
  def status(code: Int) =
    underlying.setStatus(HttpResponseStatus.valueOf(code))

  def header(name: String, value: String) =
    underlying.headers.add(name, value)

  def redirect(url: String) =
    underlying.setStatus(HttpResponseStatus.FOUND).headers.add(HttpHeaders.Names.LOCATION, url)
}
class ResponseBinding[U <: FullHttpResponse](res: U) extends BaseResponseBinding(res) with HttpResponse[U] {
  private lazy val outStream = new ByteBufOutputStream(res.content)

  def outputStream = outStream
}

private [netty] object URLParser {
  def urldecode(enc: String) : Map[String, Seq[String]] = {
    def decode(raw: String) = URLDecoder.decode(raw, HttpConfig.DEFAULT_CHARSET)
    val pairs = enc.split('&').flatMap {
      _.split('=') match {
        case Array(key, value) => List((decode(key), decode(value)))
        case Array(key) if key != "" => List((decode(key), ""))
        case _ => Nil
      }
    }.reverse
    (Map.empty[String, List[String]].withDefault {_ => Nil } /: pairs) {
      case (m, (k, v)) => m + (k -> (v :: m(k)))
    }
  }
}
