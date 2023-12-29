package unfiltered.request.io

trait FileIO extends unfiltered.util.IO {
  import java.io.{File => JFile}
  import java.io.InputStream
  import java.io.FileOutputStream

  def toFile(from: InputStream)(to: JFile): Unit = {
    use(from) { in =>
      use(new FileOutputStream(to)) { out =>
        val buffer = new Array[Byte](1024)
        def stm: LazyList[Int] = LazyList.cons(in.read(buffer), stm)
        stm.takeWhile(_ != -1).foreach { out.write(buffer, 0, _) }
      }
    }
  }
}
