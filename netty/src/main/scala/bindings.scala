package unfiltered.netty

import unfiltered.Async
import unfiltered.response.{ ResponseFunction, HttpResponse, Pass }
import unfiltered.request.{ Charset, HttpRequest, POST, PUT, RequestContentType, & }

import io.netty.buffer.{ ByteBufInputStream, Unpooled }
import io.netty.channel.{ ChannelFuture, ChannelFutureListener, ChannelHandlerContext }
import io.netty.handler.codec.http.{
  DefaultHttpResponse, DefaultFullHttpResponse, FullHttpRequest, FullHttpResponse, HttpContent,
  HttpHeaders, HttpRequest => NettyHttpRequest, HttpResponse => NettyHttpResponse,
  HttpResponseStatus, HttpVersion }
import io.netty.handler.ssl.SslHandler
import io.netty.util.ReferenceCountUtil
import java.io.{ BufferedReader, ByteArrayOutputStream, InputStreamReader }
import java.net.{ InetSocketAddress, URLDecoder }
import java.nio.charset.{ Charset => JNIOCharset }

import scala.collection.JavaConverters._

object HttpConfig {
   val DEFAULT_CHARSET = "UTF-8"
}

class RequestBinding(msg: ReceivedMessage)
  extends HttpRequest(msg) with Async.Responder[NettyHttpResponse] {

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
    request match {
      case has: HttpContent => Some(has)
      case not => None
    }

  /** Binds a Netty HttpResponse res to Unfiltered's HttpResponse to apply any
   * response function to it. */
  def response[T <: NettyHttpResponse](res: T)(rf: ResponseFunction[T]) =
    rf(new ResponseBinding(res)).underlying

  /** @return a new Netty FullHttpResponse bound to an Unfiltered HttpResponse */
  lazy val defaultResponse = response(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK))_

  lazy val defaultPartialResponse = response(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK))_

  /** Applies rf to a new `defaultResponse` and writes it out */
  def respond: (ResponseFunction[NettyHttpResponse] => Unit) = {
    case Pass =>
      context.fireChannelRead(request)
    case rf =>
      val keepAlive = HttpHeaders.isKeepAlive(request)
      val closer = new unfiltered.response.Responder[NettyHttpResponse] {
        def respond(res: HttpResponse[NettyHttpResponse]) {
          res.outputStream.close()
          (
            if (keepAlive) {
              val defaults = unfiltered.response.Connection("Keep-Alive")
              content match {
                case Some(has) =>
                  defaults ~> unfiltered.response.ContentLength(
                    has.content().readableBytes().toString)
                case _ => defaults
              }
            } else unfiltered.response.Connection("close")
          )(res)
        }
      }
      val future = context.channel.writeAndFlush(
        defaultResponse(rf ~> closer)
      ).addListener(new ChannelFutureListener {
        def operationComplete(f: ChannelFuture) {
          content.map(ReferenceCountUtil.release)
        }
      })
      if (!keepAlive)
        future.addListener(ChannelFutureListener.CLOSE)
  }
}

class ResponseBinding[U <: NettyHttpResponse](res: U)
  extends HttpResponse(res) {

  def content: Option[HttpContent] =
    res match {
      case has: HttpContent => Some(has)
      case not => None
    }

  private lazy val byteOutputStream = new ByteArrayOutputStream {
    // fixme: the docs state http://docs.oracle.com/javase/6/docs/api/java/io/ByteArrayOutputStream.html#close()
    //  should have no effect. we are breaking that rule here if close is called more than once
    override def close = {
      // only FullHttpResponses have content
      content.foreach(_.content.clear().writeBytes(Unpooled.copiedBuffer(this.toByteArray)))
    }
  }

  def status(code: Int) =
    res.setStatus(HttpResponseStatus.valueOf(code))

  def header(name: String, value: String) =
    res.headers.add(name, value)

  def redirect(url: String) =
    res.setStatus(HttpResponseStatus.FOUND).headers.add(HttpHeaders.Names.LOCATION, url)

  def outputStream = byteOutputStream
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
