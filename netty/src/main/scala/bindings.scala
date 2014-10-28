package unfiltered.netty

import unfiltered.Async
import unfiltered.response.{ ResponseFunction, HttpResponse, Pass }
import unfiltered.request.{ Charset, HttpRequest, POST, PUT, RequestContentType, & }

import io.netty.buffer.{ ByteBufInputStream, ByteBufOutputStream, Unpooled }
import io.netty.channel.{ ChannelFuture, ChannelFutureListener, ChannelHandlerContext }
import io.netty.handler.codec.http.{
  DefaultHttpResponse, DefaultFullHttpResponse, HttpContent,
  HttpHeaders, HttpMessage, HttpRequest => NettyHttpRequest, HttpResponse => NettyHttpResponse,
  HttpResponseStatus, HttpVersion }
import io.netty.handler.ssl.SslHandler
import io.netty.util.{ CharsetUtil, ReferenceCountUtil }
import java.io.{ BufferedReader, ByteArrayOutputStream, InputStreamReader }
import java.net.{ InetSocketAddress, URLDecoder }
import java.nio.charset.{ Charset => JNIOCharset }

import scala.collection.JavaConverters._

object HttpConfig {
   val DEFAULT_CHARSET = CharsetUtil.UTF_8.name()
}

object Content {
  def unapply(msg: HttpMessage) =
    msg match {
      case has: HttpContent => Some(has)
      case _ => None
    }
}

class RequestBinding(msg: ReceivedMessage)
  extends HttpRequest(msg) with Async.Responder[NettyHttpResponse] {

  private[this] val req = msg.request

  private[this] val content = msg.content

  private[this] lazy val params = queryParams ++ bodyParams

  private def queryParams = req.uri.split("\\?", 2) match {
    case Array(_, qs) => URLParser.urldecode(qs)
    case _ => Map.empty[String,Seq[String]]
  }

  private def bodyParams = (this, content) match {
    case ((POST(_) | PUT(_)) & RequestContentType(ct), Some(content))
      if ct.contains(HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED) =>
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

  def protocol = req.protocolVersion.text()

  def method = req.method.toString.toUpperCase

  // todo should we call URLDecoder.decode(uri, charset) on this here?
  def uri = req.uri

  def parameterNames = params.keySet.iterator

  def parameterValues(param: String) = params.getOrElse(param, Seq.empty)

  def headerNames = req.headers.names.iterator.asScala

  def headers(name: String) = req.headers.getAll(name).iterator.asScala

  def isSecure =
    Option(msg.context.pipeline.get(classOf[SslHandler])).isDefined

  def remoteAddr = msg.context.channel.remoteAddress.asInstanceOf[InetSocketAddress].getAddress.getHostAddress

  def respond(rf: ResponseFunction[NettyHttpResponse]) =
    underlying.respond(rf)
}

/** Extension of basic request binding to expose Netty-specific attributes */
case class ReceivedMessage(
  request: NettyHttpRequest,
  context: ChannelHandlerContext,
  message: java.lang.Object) { // todo: remove this. its the same as request?

  def content: Option[HttpContent] =
    Content.unapply(request)

  /** Binds a Netty HttpResponse res to Unfiltered's HttpResponse to apply any
   * response function to it. */
  def response[T <: NettyHttpResponse](res: T)(rf: ResponseFunction[T]) =
    rf(new ResponseBinding(res)).underlying

  /** @return a new Netty FullHttpResponse bound to an Unfiltered HttpResponse */
  lazy val defaultResponse = response(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK))_

  /** @return a new partial Netty HttpResonse bound to an Unfiltered HttpResponse. */
  lazy val partialResponse = response(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK))_

  /** @return a ChannelFutureListener which releases the NettyHttpRequest of this message */
  lazy val releaser = new ChannelFutureListener {
    def operationComplete(f: ChannelFuture): Unit =
      ReferenceCountUtil.release(request)
  }

  /** Applies rf to a new `defaultResponse` and writes it out */
  def respond: (ResponseFunction[NettyHttpResponse] => Unit) = {
    case Pass =>
      context.fireChannelRead(request)
    case rf =>
      val keepAlive = HttpHeaders.isKeepAlive(request)
      lazy val closer = new unfiltered.response.Responder[NettyHttpResponse] {
        def respond(res: HttpResponse[NettyHttpResponse]) {
          res.outputStream.close() // close() triggers writing content to response body
          (
            if (keepAlive) {
              val defaults = unfiltered.response.Connection(HttpHeaders.Values.KEEP_ALIVE)
              res.underlying match {
                case Content(has) =>
                  defaults ~> unfiltered.response.ContentLength(
                    has.content.readableBytes.toString)
                case _ =>
                  defaults
              }
            } else unfiltered.response.Connection(HttpHeaders.Values.CLOSE)
          )(res)
        }
      }
      val future = context.channel.writeAndFlush(
        defaultResponse(rf ~> closer)
      ).addListener(releaser)
      if (!keepAlive)
        future.addListener(ChannelFutureListener.CLOSE)
  }
}

/** An unfiltered response implementation backed by a netty http response.
 *  Note the type of netty HttpResponse determines whether or not the unfiltered
 *  response combinators can write to it. As a general rule of thumb, only netty
 *  FullHttpResponses may be writen to by calling respond with a response writer */
class ResponseBinding[U <: NettyHttpResponse](res: U)
  extends HttpResponse(res) {
  /** available when serving non-chunked responses */
  private[netty] lazy val content: Option[HttpContent] =
    Content.unapply(res)

  /** Relays to httpContent, if defined. Otherwise this stream goes nowhere */
  private lazy val outStream =
    content.map(httpContent =>
      new ByteBufOutputStream(httpContent.content)
    ).getOrElse(new ByteArrayOutputStream)

  def status(code: Int) =
    res.setStatus(HttpResponseStatus.valueOf(code))

  def status: Int =
    res.status.code()

  def header(name: String, value: String) =
    res.headers.add(name, value)

  def redirect(url: String) =
    res.setStatus(HttpResponseStatus.FOUND).headers.add(HttpHeaders.Names.LOCATION, url)

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
