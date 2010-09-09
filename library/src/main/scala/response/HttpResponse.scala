package unfiltered.response

import java.io.{OutputStream, PrintWriter}

trait HttpResponse {
  def setContentType(contentType: String) : Unit
  def setStatus(statusCode: Int) : Unit
  def getWriter() : PrintWriter
  def getOutputStream() : OutputStream
  def sendRedirect(url: String) : Unit
  def addHeader(name: String, value: String) : Unit
}


