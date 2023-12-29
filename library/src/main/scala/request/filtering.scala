package unfiltered.request

import java.io.InputStream
import java.io.InputStreamReader
import java.io.BufferedReader
import java.util.zip.{GZIPInputStream => GZIS}

/** Apply an input stream filter to a request input stream. */
object RequestFilter {
  abstract class Filtering[S <: InputStream, +T](req: HttpRequest[T]) extends DelegatingRequest(req) {
    private def charset = Charset(this).getOrElse { "iso-8859-1" }

    override lazy val inputStream: InputStream = filter(delegate.inputStream)

    override lazy val reader: java.io.Reader =
      new BufferedReader(new InputStreamReader(inputStream, charset))

    def filter(is: InputStream): S
  }

  /** Apply a gzip input stream filter to request. */
  object GZip {
    def apply[T](req: HttpRequest[T]): HttpRequest[T] = new Filtering[GZIS, T](req) {
      def filter(is: InputStream) = new GZIS(is)
    }
  }
}
