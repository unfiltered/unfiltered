package unfiltered.netty

import unfiltered.JIteratorIterator
import unfiltered.response.HttpResponse
import unfiltered.request.HttpRequest
import java.net.URLDecoder
import org.jboss.netty.handler.codec.http._
import java.io._
import org.jboss.netty.buffer.{ChannelBuffers, ChannelBufferOutputStream, ChannelBufferInputStream}

object HttpConfig {
   val DEFAULT_CHARSET = "UTF-8"
}

private [netty] class RequestBinding(req: DefaultHttpRequest) extends HttpRequest(req) {

  lazy val params = URLParser.parse(req.getUri)

  lazy val inputStream = new ChannelBufferInputStream(req.getContent)
  lazy val reader = {
    val encoding = HttpConfig.DEFAULT_CHARSET // TODO: Parse content-type
    new BufferedReader(new InputStreamReader(inputStream))
  }


  def protocol = req.getProtocolVersion match {
    case HttpVersion.HTTP_1_0 => "HTTP/1.0"
    case HttpVersion.HTTP_1_1 => "HTTP/1.1"
  }
  def method = req.getMethod.toString

  def requestURI = req.getUri.split('?').toList.head
  def contextPath = "" // No contexts here

  lazy val parameterNames = params.keySet.elements
  def parameterValues(param: String) = params(param).reverse

  def headers(name: String) = new JIteratorIterator(req.getHeaders(name).iterator)
}

private [netty] class ResponseBinding(res: DefaultHttpResponse) extends HttpResponse(res) {

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
}

private [netty] object URLParser {

  private final val URI_CHARSET = "UTF-8"
  
  def parse(uri: String) : Map[String, Seq[String]] = {
    val pairs : List[(String, String)] = uri.split(Array('?', '&')).toList.drop(1).flatMap {
      _.split("=") match {
        case Array(key, value) => List((key, URLDecoder.decode(value, URI_CHARSET)))
        case _ => Nil
      }
    }
    (Map.empty[String, List[String]].withDefault {_ => Nil } /: pairs) {
      case (m, (k, v)) => m + (k -> (v :: m(k)))
    }
  }

}
