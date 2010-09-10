package unfiltered.servlet

import unfiltered.response.HttpResponse
import unfiltered.request.HttpRequest
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

class ServletResponseWrapper(res: HttpServletResponse) extends HttpResponse(res) {
  def setContentType(contentType: String) = res.setContentType(contentType)
  def setStatus(statusCode: Int) = res.setStatus(statusCode)
  def getWriter() = res.getWriter
  def getOutputStream() = res.getOutputStream
  def sendRedirect(url: String) = res.sendRedirect(url)
  def addHeader(name: String, value: String) = res.addHeader(name, value)
}

class ServletRequestWrapper(val underlying: HttpServletRequest) extends HttpRequest {
  def getInputStream() = underlying.getInputStream
  def getReader() = underlying.getReader
  def getProtocol() = underlying.getProtocol
  def getMethod() = underlying.getMethod
  def getRequestURI() = underlying.getRequestURI
  def getContextPath() = underlying.getContextPath
  def getParameterNames() = underlying.getParameterNames.asInstanceOf[java.util.Enumeration[String]]
  def getParameterValues(param: String) = underlying.getParameterValues(param)
  def getHeaders(name: String) = underlying.getHeaders(name).asInstanceOf[java.util.Enumeration[String]]
}
