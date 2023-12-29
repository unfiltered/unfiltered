package unfiltered.filter

import java.io.PrintWriter
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import unfiltered.response.HttpResponse

/**
 * The servlet API states that, for a given response, either the
 * getOutputStream or getWriter method may be called, but not both:
 *
 * https://javadoc.io/static/jakarta.servlet/jakarta.servlet-api/5.0.0/jakarta/servlet/ServletResponse.html#getOutputStream--
 * https://javadoc.io/static/jakarta.servlet/jakarta.servlet-api/5.0.0/jakarta/servlet/ServletResponse.html#getWriter--
 *
 * Unfiltered response bindings are based on a single outputStream,
 * and support filtering it (unfiltered.response.ResponseFilter)
 * through response function composition. Writing to the underlying
 * response output stream directly would bypass any filters in place,
 * and writing to its writer will produce an InvalidStateException.
 *
 * If working with software that requires a HttpServletResponse and
 * uses its Writer interface, this wrapper supplies a writer that
 * works with any stream filters in the response function chain.
 */

case class WritableServletResponse(res: HttpResponse[HttpServletResponse])
    extends HttpServletResponseWrapper(res.underlying) {
  override lazy val getWriter = new PrintWriter(res.outputStream)
}
