package unfiltered.request

/** Utility for working with the request body. */
object Body {
  def stream[T](req: HttpRequest[T]) = req.inputStream
  def reader[T](req: HttpRequest[T]) = req.reader
  def bytes[T](req: HttpRequest[T]) = {
    val in = stream(req)
    val bos = new java.io.ByteArrayOutputStream
    val ba = new Array[Byte](4096)
    /* @scala.annotation.tailrec */ def read {
      val len = in.read(ba)
      if (len > 0) bos.write(ba, 0, len)
      if (len >= 0) read
    }
    read
    in.close
    bos.toByteArray
  }
}

object InStream {
  @deprecated
  /** Use Body */
  def unapply[T](req: HttpRequest[T]) = Some(req.inputStream)
}

object Read {
  @deprecated
  /** Use Body */
  def unapply[T](req: HttpRequest[T]) = Some(req.reader)
}

object Bytes {
  @deprecated
  /** Use Body */
  def apply[T](req: HttpRequest[T]) = Body.bytes(req) match {
    case Array() => None
    case ba => Some(ba, req)
  }
}
