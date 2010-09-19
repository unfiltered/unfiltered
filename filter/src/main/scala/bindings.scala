package unfiltered.filter

import unfiltered.JEnumerationIterator
import unfiltered.response.HttpResponse
import unfiltered.request.HttpRequest
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

private [filter] class RequestBinding(req: HttpServletRequest) extends HttpRequest(req) {
  def getInputStream() = req.getInputStream
  def getReader() = req.getReader
  def getProtocol() = req.getProtocol
  def getMethod() = req.getMethod
  def getRequestURI() = req.getRequestURI
  def getContextPath() = req.getContextPath
  lazy val getParameterNames = new JEnumerationIterator(
    req.getParameterNames.asInstanceOf[java.util.Enumeration[String]]
  )
  def getParameterValues(param: String) = req.getParameterValues(param)
  def getHeaders(name: String) = new JEnumerationIterator(
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
