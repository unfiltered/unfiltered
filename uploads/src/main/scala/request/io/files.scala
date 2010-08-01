package unfiltered.request.io

trait IO {
  def use[T <: { def close(): Unit }](closable: T)(block: T => Unit) {
    try { block(closable) }
    finally { closable.close() }
  }
}

trait FileIO extends IO {
  import java.io.{File => JFile, InputStream, FileOutputStream}
  
  def toFile(src: InputStream)(to: JFile) {
    use(src) { in =>
      use(new FileOutputStream(to)) { out =>
        val buffer = new Array[Byte](1024)
        Iterator.continually(in.read(buffer))
            .takeWhile(_ != -1)
            .foreach { out.write(buffer, 0 , _) }
      }
    }
  }
}