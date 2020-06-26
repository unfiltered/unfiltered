package unfiltered.filter

import unfiltered.response.HttpResponse
import unfiltered.request.HttpRequest
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import unfiltered.Cookie
import scala.collection.JavaConverters._

class RequestBinding(req: HttpServletRequest) extends HttpRequest(req) {
  def inputStream = req.getInputStream
  def reader = req.getReader
  def protocol = req.getProtocol
  def method = req.getMethod.toUpperCase
  def uri = Option(req.getRequestURI) ++ Option(req.getQueryString).map("?%s".format(_)) mkString("")
  def parameterNames: Iterator[String] =
    req.getParameterNames.asScala
  def parameterValues(param: String) = Option[Seq[String]](req.getParameterValues(param)).getOrElse(Nil)
  def headerNames: Iterator[String] =
    req.getHeaderNames.asScala
  def headers(name: String): Iterator[String] =
    req.getHeaders(name).asScala
  lazy val cookies = req.getCookies match {
    case null => Nil
    case jcookies =>
      jcookies.foldLeft(List[Cookie]())((l, c) =>
        Cookie(c.getName, c.getValue, Option(c.getDomain), Option(c.getPath), Option(c.getMaxAge), Option(c.getSecure)) :: l)
  }

  def isSecure = req.isSecure
  def remoteAddr = req.getRemoteAddr
}

class ResponseBinding(res: HttpServletResponse) extends HttpResponse(res) {
  def status(statusCode: Int) = res.setStatus(statusCode)
  def status: Int = res.getStatus
  def outputStream = res.getOutputStream
  def redirect(url: String) = res.sendRedirect(url)
  def header(name: String, value: String) = res.addHeader(name, value)
  def cookies(resCookies: Seq[Cookie]) = {
    import javax.servlet.http.{Cookie => JCookie}
    resCookies.foreach { c =>
      val jc = new JCookie(c.name, c.value)
      if(c.domain.isDefined) jc.setDomain(c.domain.get)
      if(c.path.isDefined) jc.setPath(c.path.get)
      if(c.maxAge.isDefined) jc.setMaxAge(c.maxAge.get)
      if(c.secure.isDefined) jc.setSecure(c.secure.get)
      res.addCookie(jc)
    }
  }
}
