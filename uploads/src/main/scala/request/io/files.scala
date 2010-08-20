package unfiltered.request.io

trait IO {
  def use[A <: { def close(): Unit }, B](closable: A)(f: A => B): B =
    try { f(closable) } finally { closable.close() }
}

trait FileIO extends IO {
  import java.io.{File => JFile, InputStream, FileOutputStream}
  
  def toFile(from: InputStream)(to: JFile) {
    use(from) { in =>
      use(new FileOutputStream(to)) { out =>
        val buffer = new Array[Byte](1024)
        def stm: Stream[Int] = Stream.cons(in.read(buffer), stm)
        stm.takeWhile(_ != -1)
           .foreach { out.write(buffer, 0 , _) }
      }
    }
  }
}
