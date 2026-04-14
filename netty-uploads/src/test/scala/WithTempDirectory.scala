package unfiltered.netty.request

import java.io.File
import java.nio.file.Files
import unfiltered.specs2.netty.Served

trait WithTempDirectory extends Served {
  protected final lazy val directory: File =
    Files.createTempDirectory(this.getClass.getSimpleName).toFile

  override def afterAll(): Unit = {
    super.afterAll()
    if (directory.exists()) {
      Files
        .walk(directory.toPath)
        .filter(_.toFile.isFile)
        .forEach(
          _.toFile.delete()
        )
      if (scala.util.Properties.isWin) {
        directory.delete()
      } else {
        assert(directory.delete())
      }
    }
  }
}
