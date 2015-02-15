package unfiltered.request

import java.io.InputStreamReader
import unfiltered.util.IO

import scala.io.Codec

/** Utility for working with the request body. */
object Body {
  def stream[T](req: HttpRequest[T]) = req.inputStream
  def reader[T](req: HttpRequest[T]) = req.reader
  def bytes[T](req: HttpRequest[T]) = {
    val in = stream(req)
    val bos = new java.io.ByteArrayOutputStream
    val ba = new Array[Byte](4096)
    @scala.annotation.tailrec def read() {
      val len = in.read(ba)
      if (len > 0) bos.write(ba, 0, len)
      if (len >= 0) read
    }
    read()
    in.close
    bos.toByteArray
  }
  def string[T](req: HttpRequest[T])(implicit codec: Codec = Codec.UTF8) = {
    IO.use(new InputStreamReader(req.inputStream, codec.charSet)) { reader =>
      val writer = new java.io.StringWriter
      val ca = new Array[Char](4096)
      @scala.annotation.tailrec def read() {
        val len = reader.read(ca)
        if (len > 0) writer.write(ca, 0, len)
        if (len >= 0) read
      }
      read()
      writer.toString
    }
  }
}
