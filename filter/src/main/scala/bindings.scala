package unfiltered.filter

import unfiltered.JEnumerationIterator
import unfiltered.response.HttpResponse
import unfiltered.request.HttpRequest
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

private [filter] class RequestBinding(req: HttpServletRequest) extends HttpRequest(req) {
  def inputStream = req.getInputStream
  def reader = req.getReader
  def protocol = req.getProtocol
  def method = req.getMethod
  def requestURI = req.getRequestURI
  def contextPath = req.getContextPath
  lazy val parameterNames = new JEnumerationIterator(
    req.getParameterNames.asInstanceOf[java.util.Enumeration[String]]
  )
  def parameterValues(param: String) = req.getParameterValues(param)
  def headers(name: String) = new JEnumerationIterator(
    req.getHeaders(name).asInstanceOf[java.util.Enumeration[String]]
  )
}

private [filter] class ResponseBinding(res: HttpServletResponse) extends HttpResponse(res) {
  def setContentType(contentType: String) = res.setContentType(contentType)
  def setStatus(statusCode: Int) = res.setStatus(statusCode)
  def getWriter() = res.getWriter
  def getOutputStream() = res.getOutputStream
  def sendRedirect(url: String) = res.sendRedirect(url)
  def addHeader(name: String, value: String) = res.addHeader(name, value)
}
