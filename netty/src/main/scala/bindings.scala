
package unfiltered.netty

import unfiltered.{JIteratorIterator,Async}
import unfiltered.response.{ResponseFunction, HttpResponse, Pass}
import unfiltered.request.{HttpRequest,POST,RequestContentType,Charset}
import java.net.URLDecoder
import org.jboss.netty.handler.codec.http._
import java.io._
import org.jboss.netty.buffer.{ChannelBuffers, ChannelBufferOutputStream,
  ChannelBufferInputStream}
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.HttpVersion._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.{HttpResponse=>NHttpResponse,
                                           HttpRequest=>NHttpRequest}
import java.nio.charset.{Charset => JNIOCharset}
import unfiltered.Cookie

object HttpConfig {
   val DEFAULT_CHARSET = "UTF-8"
}

class RequestBinding(msg: ReceivedMessage)
extends HttpRequest(msg) with Async.Responder[NHttpResponse] {
  private val req = msg.request
  lazy val params = queryParams ++ postParams
  def queryParams = req.getUri.split("\\?", 2) match {
    case Array(_, qs) => URLParser.urldecode(qs)
    case _ => Map.empty[String,Seq[String]]
  }
  def postParams = this match {
    case POST(RequestContentType(ct)) if ct.contains("application/x-www-form-urlencoded") =>
      URLParser.urldecode(req.getContent.toString(JNIOCharset.forName(charset)))
    case _ => Map.empty[String,Seq[String]]
  }

  private def charset = this match {
    case Charset(cs, _) => cs
    case _ => HttpConfig.DEFAULT_CHARSET
  }
  lazy val inputStream = new ChannelBufferInputStream(req.getContent)
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
  def parameterValues(param: String) = params(param)
  def headers(name: String) = new JIteratorIterator(req.getHeaders(name).iterator)

  @deprecated("use the header extractor request.Cookies instead")
  lazy val cookies = {
    import org.jboss.netty.handler.codec.http.{Cookie => NCookie, CookieDecoder}
    import unfiltered.Cookie
    val cookieString = req.getHeader(HttpHeaders.Names.COOKIE);
    if (cookieString != null) {
      val cookieDecoder = new CookieDecoder
      val decCookies = Set(cookieDecoder.decode(cookieString).toArray(new Array[NCookie](0)): _*)
      (List[Cookie]() /: decCookies)((l, c) =>
        Cookie(c.getName, c.getValue, Option(c.getDomain), Option(c.getPath), Option(c.getMaxAge), Option(c.isSecure)) :: l)
    } else {
      Nil
    }
  }
  def isSecure = msg.context.getPipeline.get(classOf[org.jboss.netty.handler.ssl.SslHandler]) match {
    case null => false
    case _ => true
  }
  def remoteAddr =msg.context.getChannel.getRemoteAddress.asInstanceOf[java.net.InetSocketAddress].getAddress.getHostAddress

  def respond(rf: ResponseFunction[NHttpResponse]) =
    underlying.respond(rf)
}
/** Extension of basic request binding to expose Netty-specific attributes */
case class ReceivedMessage(
  request: NHttpRequest,
  context: ChannelHandlerContext,
  event: MessageEvent) {

  /** Binds a Netty HttpResponse res to Unfiltered's HttpResponse to apply any
   * response function to it. */
  def response[T <: NHttpResponse](res: T)(rf: ResponseFunction[T]) =
    rf(new ResponseBinding(res)).underlying

  /** @return a new Netty DefaultHttpResponse bound to an Unfiltered HttpResponse */
  val defaultResponse = response(new DefaultHttpResponse(HTTP_1_1, OK))_
  /** Applies rf to a new `defaultResponse` and writes it out */
  def respond: (ResponseFunction[NHttpResponse] => Unit) = {
    case Pass => context.sendUpstream(event)
    case rf =>
      val keepAlive = HttpHeaders.isKeepAlive(request)
      val closer = new unfiltered.response.Responder[NHttpResponse] {
        def respond(res: HttpResponse[NHttpResponse]) {
          res.outputStream.close()
          (
            if (keepAlive)
              unfiltered.response.Connection("Keep-Alive") ~>
              unfiltered.response.ContentLength(
                res.underlying.getContent().readableBytes().toString)
            else unfiltered.response.Connection("close")
          )(res)
        }
      }
      val future = event.getChannel.write(
        defaultResponse(
          unfiltered.response.Server("Scala Netty Unfiltered Server") ~> 
            rf ~> closer
        )
      )
      if (!keepAlive)
        future.addListener(ChannelFutureListener.CLOSE)
  }
}

class ResponseBinding[U <: NHttpResponse](res: U)
    extends HttpResponse(res) {
  private lazy val byteOutputStream = new ByteArrayOutputStream {
    override def close = {
      res.setContent(ChannelBuffers.copiedBuffer(this.toByteArray))
    }
  }

  def status(statusCode: Int) =
    res.setStatus(HttpResponseStatus.valueOf(statusCode))
  def header(name: String, value: String) = res.addHeader(name, value)

  def redirect(url: String) = {
    res.setStatus(HttpResponseStatus.FOUND)
    res.setHeader(HttpHeaders.Names.LOCATION, url)
  }

  def outputStream = byteOutputStream

  @deprecated("use the response combinator response.ResponseCookies(cookies) instead")
  def cookies(resCookies: Seq[Cookie]) = {
    import org.jboss.netty.handler.codec.http.{DefaultCookie, CookieEncoder}
    if(!resCookies.isEmpty) {
      val cookieEncoder = new CookieEncoder(true)
      resCookies.foreach { c =>
        val nc = new DefaultCookie(c.name, c.value)
        if(c.domain.isDefined) nc.setDomain(c.domain.get)
        if(c.path.isDefined) nc.setPath(c.path.get)
        if(c.maxAge.isDefined) nc.setMaxAge(c.maxAge.get)
        if(c.secure.isDefined) nc.setSecure(c.secure.get)
        cookieEncoder.addCookie(nc)
      }
      res.addHeader(HttpHeaders.Names.SET_COOKIE, cookieEncoder.encode)
    }
  }
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
