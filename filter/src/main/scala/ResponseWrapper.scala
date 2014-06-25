package unfiltered.filter

import java.io.PrintWriter
import javax.servlet.http.{HttpServletResponse, HttpServletResponseWrapper}

/**
 * The servlet API states that, for a given response, either the getOutputStream or getWriter
 * method may be called, but not both:
 *
 * http://docs.oracle.com/javaee/6/api/javax/servlet/ServletResponse.html#getOutputStream()
 * http://docs.oracle.com/javaee/6/api/javax/servlet/ServletResponse.html#getWriter()
 *
 * If both are called then an IllegalStateException will be thrown. In the past the filter
 * plan implementation has optimistically closed the output stream. However, in the cases
 * where a response function has accessed the underlying writer, this results in
 * an IllegalStateException.
 *
 * This is solved with this subclass HttpServletResponse, which wraps and delegates all
 * calls to the underlying response, but adds a simple mechanism for safe closing.
 */
private [filter] final class ResponseWrapper(response: HttpServletResponse) extends HttpServletResponseWrapper(response) {
  private var writerTouched = false

  override def getWriter: PrintWriter = {
    writerTouched = true
    super.getWriter
  }

  def complete() {
    if(writerTouched)
      super.getWriter.close()
    else
      super.getOutputStream.close()
  }
}
