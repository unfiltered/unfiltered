package unfiltered.netty.resources

import java.io.{ File, FileInputStream, FilterInputStream, InputStream }
import java.net.{ JarURLConnection, URL, URLDecoder }
import java.util.jar.{ JarEntry, JarFile }

object Resolve {
  val JarPathDelimiter = "!/"
  type Resolver = PartialFunction[String, URL => Option[Resource]]
  val JarResolver: Resolver = {
    case jf if (jf.startsWith("jar:file:")) =>
      { u => Some(JarResource(u)) }
  }
  val FsResolver: Resolver = {
    case fs if (fs.startsWith("file:")) =>
      { u => Some(FileSystemResource(new File(u.toURI))) }
  }
  val DefaultResolver = FsResolver orElse JarResolver orElse ({
    case _ => { u => None }
  }: Resolver)
  def apply(url: URL, resolver: Resolver = DefaultResolver): Option[Resource] =
    resolver(url.toExternalForm)(url)
}

trait Resource {
  def lastModified: Long
  def directory: Boolean
  def hidden: Boolean
  def path: String
  def size: Long
  def exists: Boolean
  def in: InputStream
}

case class FileSystemResource(f: File) extends Resource {
  def lastModified = f.lastModified
  def directory = f.isDirectory
  def hidden = f.isHidden
  def exists = f.exists
  def path = f.getCanonicalPath
  def size = f.length
  def in = new FileInputStream(f)
}

case class JarResource(url: URL) extends Resource {
  import scala.util.control.Exception.catching
  import java.io.FileNotFoundException

  val urlstr = url.toString
  val sep = urlstr.indexOf(Resolve.JarPathDelimiter)
  val jarurl = urlstr.substring(0, sep + 2)
  val path = urlstr.substring(sep + 2)
  val directory = path.endsWith("/")
  val hidden = false
  lazy val entry = jarfile.flatMap { jar =>
    import scala.collection.JavaConversions.enumerationAsScalaIterator
    enumerationAsScalaIterator(jar.entries)
      .filter(_.getName.replace("\\","/") == path).toSeq.headOption match {
        case Some(e) =>
          Some(e)
        case _ =>
          None
      }
  }
  lazy val exists = entry.isDefined
  lazy val lastModified = entry match  {
    case Some(e) => e.getTime
    case _ => jarfile.map(jar => new File(jar.getName).lastModified)
                     .getOrElse(-1L)
  }
  
  lazy val size =
    if (exists && !directory) entry.get.getSize
    else -1

  def jarfile = catching(classOf[FileNotFoundException]).opt {
    url.openConnection().asInstanceOf[JarURLConnection].getJarFile
  }
  /** users are expected to call close(). Netty's ChunkedStream writer does */
  def in = url.openStream()  
}
