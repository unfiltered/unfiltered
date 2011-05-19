package unfiltered.netty

import unfiltered.JIteratorIterator
import unfiltered.response.{ResponseFunction, HttpResponse}
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
import unfiltered.util.Optional

object HttpConfig {
   val DEFAULT_CHARSET = "UTF-8"
}

private [netty] class RequestBinding(msg: ReceivedMessage) extends HttpRequest(msg) {
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

  def uri = req.getUri
  @deprecated def requestURI = req.getUri.split('?').toList.head
  @deprecated def contextPath = "" // No contexts here

  def parameterNames = params.keySet.elements
  def parameterValues(param: String) = params(param)

  def headers(name: String) = new JIteratorIterator(req.getHeaders(name).iterator)

  lazy val cookies = {
    import org.jboss.netty.handler.codec.http.{Cookie => NCookie, CookieDecoder}
    import unfiltered.Cookie
    val cookieString = req.getHeader(HttpHeaders.Names.COOKIE);
    if (cookieString != null) {
      val cookieDecoder = new CookieDecoder
      val decCookies = Set(cookieDecoder.decode(cookieString).toArray(new Array[NCookie](0)): _*)
      (List[Cookie]() /: decCookies)((l, c) =>
        Cookie(c.getName, c.getValue, Optional(c.getDomain), Optional(c.getPath), Optional(c.getMaxAge), Optional(c.isSecure)) :: l)
    } else {
      Nil
    }
  }
  def isSecure = msg.context.getPipeline.get(classOf[org.jboss.netty.handler.ssl.SslHandler]) match {
    case null => false
    case _ => true
  }
  def remoteAddr = msg.context.getChannel.getRemoteAddress.asInstanceOf[java.net.InetSocketAddress].getAddress.getHostAddress
}
/** Extension of basic request binding to expose Netty-specific attributes */
case class ReceivedMessage(
  request: NHttpRequest,
  context: ChannelHandlerContext,
  event: MessageEvent) {
  import org.jboss.netty.handler.codec.http.{HttpResponse => NHttpResponse}

  /** Binds a Netty HttpResponse res to Unfiltered's HttpResponse to apply any
   * response function to it. */
  def response[T <: NHttpResponse](res: T)(rf: ResponseFunction[T]) =
    rf(new ResponseBinding(res)).underlying

  /** @return a new Netty DefaultHttpResponse bound to an Unfiltered HttpResponse */
  val defaultResponse = response(new DefaultHttpResponse(HTTP_1_1, OK))_
  /** Applies rf to a new `defaultResponse` and writes it out */
  def respond(rf: ResponseFunction[NHttpResponse]) = {
    val keepAlive = HttpHeaders.isKeepAlive(request)
    val closer = new unfiltered.response.Responder[NHttpResponse] {
      def respond(res: HttpResponse[NHttpResponse]) {
        res.getOutputStream.close()
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
        unfiltered.response.Server("Scala Netty Unfiltered Server") ~> rf ~> closer
      )
    )
    if (!keepAlive)
      future.addListener(ChannelFutureListener.CLOSE)
  }
}

private [netty] class ResponseBinding[U <: NHttpResponse](res: U)
    extends HttpResponse(res) {
  private lazy val outputStream = new ByteArrayOutputStream {
    override def close = {
      res.setContent(ChannelBuffers.copiedBuffer(this.toByteArray))
    }
  }
  private lazy val writer = new PrintWriter(new OutputStreamWriter(outputStream, HttpConfig.DEFAULT_CHARSET))

  def setContentType(contentType: String) = res.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentType)
  def setStatus(statusCode: Int) = res.setStatus(HttpResponseStatus.valueOf(statusCode))
  def addHeader(name: String, value: String) = res.addHeader(name, value)

  def sendRedirect(url: String) = {
    res.setStatus(HttpResponseStatus.FOUND)
    res.setHeader(HttpHeaders.Names.LOCATION, url)
  }

  def getWriter() = writer
  def getOutputStream() = outputStream

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
