package unfiltered.request

object InStream {
  def unapply[T](req: HttpRequest[T]) = Some(req.inputStream, req)
}

object Read {
  def unapply[T](req: HttpRequest[T]) = Some(req.reader, req)
}

object Bytes {
  def unapply[T](req: HttpRequest[T]) = {
    val InStream(in, _) = req
    val bos = new java.io.ByteArrayOutputStream
    val ba = new Array[Byte](4096)
    /* @scala.annotation.tailrec */ def read {
      val len = in.read(ba)
      if (len > 0) bos.write(ba, 0, len)
      if (len >= 0) read
    }
    read
    in.close
    bos.toByteArray match {
      case Array() => None
      case ba => Some(ba, req)
    }
  }
}
