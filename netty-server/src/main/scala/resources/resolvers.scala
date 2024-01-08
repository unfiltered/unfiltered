package unfiltered.netty.resources

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.JarURLConnection
import java.net.URL
import scala.util.control.Exception.catching
import java.util.jar.JarEntry
import java.util.jar.JarFile

// todo(doug): none of this is specific to unfiltered. consider factoring this out into its own library
object Resolve {
  val JarPathDelimiter = "!/"
  type Resolver = PartialFunction[String, URL => Option[Resource]]
  val JarResolver: Resolver = {
    case jf if jf.startsWith("jar:file:") => { u => Some(JarResource(u)) }
  }
  val FsResolver: Resolver = {
    case fs if fs.startsWith("file:") => { u => Some(FileSystemResource(new File(u.toURI))) }
  }
  val DefaultResolver: PartialFunction[String, URL => Option[Resource]] = FsResolver orElse JarResolver orElse ({
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
  def in: InputStream = new FileInputStream(f)
}

case class JarResource(url: URL) extends Resource {

  val urlstr = url.toString
  val sep: Int = urlstr.indexOf(Resolve.JarPathDelimiter)
  val jarurl: String = urlstr.substring(0, sep + 2)
  val path: String = urlstr.substring(sep + 2)
  val directory: Boolean = path.endsWith("/")
  val hidden = false
  lazy val entry: Option[JarEntry] = jarfile.flatMap { jar =>
    import scala.jdk.CollectionConverters._
    jar.entries.asScala.find(_.getName.replace("\\", "/") == path)
  }
  lazy val exists = entry.isDefined
  lazy val lastModified: Long = entry match {
    case Some(e) => e.getTime
    case _ => jarfile.map(jar => new File(jar.getName).lastModified).getOrElse(-1L)
  }

  lazy val size: Long =
    if (exists && !directory) entry.get.getSize
    else -1

  def jarfile: Option[JarFile] = catching(classOf[FileNotFoundException]).opt {
    url.openConnection().asInstanceOf[JarURLConnection].getJarFile
  }

  /** users are expected to call close(). Netty's ChunkedStream writer does */
  def in: InputStream = url.openStream()
}
