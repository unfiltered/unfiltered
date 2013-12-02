package unfiltered.netty

import unfiltered.{Async, Cookie}
import unfiltered.response.{ResponseFunction, HttpResponse, Pass}
import unfiltered.request.{HttpRequest,POST,PUT,&,RequestContentType,Charset}

import io.netty.buffer.{ Unpooled, ByteBufInputStream }
import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.{ HttpResponse => NHttpResponse,                                    
                                     HttpRequest  => NHttpRequest }
import io.netty.handler.ssl.SslHandler
import java.io.{BufferedReader, ByteArrayOutputStream, InputStreamReader }
import java.net.URLDecoder
import java.nio.charset.{Charset => JNIOCharset}

import scala.collection.JavaConverters._

object HttpConfig {
   val DEFAULT_CHARSET = "UTF-8"
}

class RequestBinding(msg: ReceivedMessage)
extends HttpRequest(msg) with Async.Responder[FullHttpResponse] {
  private val req = msg.request
  lazy val params = queryParams ++ bodyParams
  def queryParams = req.getUri.split("\\?", 2) match {
    case Array(_, qs) => URLParser.urldecode(qs)
    case _ => Map.empty[String,Seq[String]]
  }
  def bodyParams = this match {
    case (POST(_) | PUT(_)) & RequestContentType(ct) if ct.contains("application/x-www-form-urlencoded") =>
      URLParser.urldecode(req.content.toString(JNIOCharset.forName(charset)))
    case _ => Map.empty[String,Seq[String]]
  }

  private def charset = Charset(this).getOrElse {
    HttpConfig.DEFAULT_CHARSET
  }
  lazy val inputStream = new ByteBufInputStream(req.content)
  lazy val reader = {
    new BufferedReader(new InputStreamReader(inputStream, charset))
  }

  def protocol = req.getProtocolVersion match {
    case HttpVersion.HTTP_1_0 => "HTTP/1.0"
    case HttpVersion.HTTP_1_1 => "HTTP/1.1"
  }
  def method = req.getMethod.toString.toUpperCase

  // todo should we call URLDecoder.decode(uri, charset) on this here?
  def uri = req.getUri

  def parameterNames = params.keySet.iterator
  def parameterValues(param: String) = params.getOrElse(param, Seq.empty)
  def headerNames = req.headers.names.iterator.asScala
  def headers(name: String) = req.headers.getAll(name).iterator.asScala

  def isSecure = msg.context.pipeline.get(classOf[SslHandler]) match {
    case null => false
    case _ => true
  }
  def remoteAddr = msg.context.channel.remoteAddress.asInstanceOf[java.net.InetSocketAddress].getAddress.getHostAddress

  def respond(rf: ResponseFunction[FullHttpResponse]) =
    underlying.respond(rf)
}
/** Extension of basic request binding to expose Netty-specific attributes */
case class ReceivedMessage(
  request: FullHttpRequest,
  context: ChannelHandlerContext,
  message: java.lang.Object) {

  /** Binds a Netty HttpResponse res to Unfiltered's HttpResponse to apply any
   * response function to it. */
  def response[T <: FullHttpResponse](res: T)(rf: ResponseFunction[T]) =
    rf(new ResponseBinding(res)).underlying

  /** @return a new Netty FullHttpResponse bound to an Unfiltered HttpResponse */
  val defaultResponse = response(new DefaultFullHttpResponse(HTTP_1_1, OK))_

  /** Applies rf to a new `defaultResponse` and writes it out */
  def respond: (ResponseFunction[FullHttpResponse] => Unit) = {
    case Pass => context.fireChannelRead(message)
    case rf =>
      val keepAlive = HttpHeaders.isKeepAlive(request)
      val closer = new unfiltered.response.Responder[FullHttpResponse] {
        def respond(res: HttpResponse[FullHttpResponse]) {
          res.outputStream.close()
          (
            if (keepAlive)
              unfiltered.response.Connection("Keep-Alive") ~>
              unfiltered.response.ContentLength(
                res.underlying.content().readableBytes().toString)
            else unfiltered.response.Connection("close")
          )(res)
        }
      }
      val future = context.channel.write(
        defaultResponse(rf ~> closer)
      )
      if (!keepAlive)
        future.addListener(ChannelFutureListener.CLOSE)
  }
}

class ResponseBinding[U <: FullHttpResponse](res: U)
    extends HttpResponse(res) {
  private lazy val byteOutputStream = new ByteArrayOutputStream {
    override def close =
      res.content.writeBytes(Unpooled.copiedBuffer(this.toByteArray))
  }

  def status(statusCode: Int) =
    res.setStatus(HttpResponseStatus.valueOf(statusCode))

  def header(name: String, value: String) = res.headers.add(name, value)

  def redirect(url: String) = {
    res.setStatus(HttpResponseStatus.FOUND)
    res.headers.add(HttpHeaders.Names.LOCATION, url)
  }

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
