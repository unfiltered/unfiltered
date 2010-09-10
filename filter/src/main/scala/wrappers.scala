package unfiltered.servlet

import unfiltered.response.HttpResponse
import unfiltered.request.HttpRequest
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

class ServletResponseWrapper(val res: HttpServletResponse) extends HttpResponse {
  def setContentType(contentType: String) = res.setContentType(contentType)
  def setStatus(statusCode: Int) = res.setStatus(statusCode)
  def getWriter() = res.getWriter
  def getOutputStream() = res.getOutputStream
  def sendRedirect(url: String) = res.sendRedirect(url)
  def addHeader(name: String, value: String) = res.addHeader(name, value)
}

class ServletRequestWrapper(val req: HttpServletRequest) extends HttpRequest {
  def getInputStream() = req.getInputStream
  def getReader() = req.getReader
  def getProtocol() = req.getProtocol
  def getMethod() = req.getMethod
  def getRequestURI() = req.getRequestURI
  def getContextPath() = req.getContextPath
  def getParameterNames() = req.getParameterNames.asInstanceOf[java.util.Enumeration[String]]
  def getParameterValues(param: String) = req.getParameterValues(param)
  def getHeaders(name: String) = req.getHeaders(name).asInstanceOf[java.util.Enumeration[String]]
}