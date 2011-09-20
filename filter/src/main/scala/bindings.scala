package unfiltered.filter

import unfiltered.JEnumerationIterator
import unfiltered.response.HttpResponse
import unfiltered.request.HttpRequest
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import unfiltered.Cookie
import unfiltered.util.Optional

class RequestBinding(req: HttpServletRequest) extends HttpRequest(req) {
  def inputStream = req.getInputStream
  def reader = req.getReader
  def protocol = req.getProtocol
  def method = req.getMethod.toUpperCase
  def uri = req.getRequestURI :: Nil ++ Optional(req.getQueryString).map("?%s".format(_)) mkString("")
  def parameterNames = new JEnumerationIterator(
    req.getParameterNames.asInstanceOf[java.util.Enumeration[String]]
  )
  def parameterValues(param: String) = req.getParameterValues(param)
  def headers(name: String) = new JEnumerationIterator(
    req.getHeaders(name).asInstanceOf[java.util.Enumeration[String]]
  )
  lazy val cookies = req.getCookies match {
    case null => Nil
    case jcookies =>
      (List[Cookie]() /: jcookies)((l, c) =>
        Cookie(c.getName, c.getValue, Optional(c.getDomain), Optional(c.getPath), Optional(c.getMaxAge), Optional(c.getSecure)) :: l)
  }

  def isSecure = req.isSecure
  def remoteAddr = req.getRemoteAddr
}

class ResponseBinding(res: HttpServletResponse) extends HttpResponse(res) {
  def status(statusCode: Int) = res.setStatus(statusCode)
  def outputStream() = res.getOutputStream
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
