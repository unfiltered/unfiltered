package unfiltered.request.uploads

import unfiltered.util.IteratorConversions
import scala.util.control.Exception.allCatch

import javax.servlet.http.HttpServletRequest

import org.apache.commons.{fileupload => fu}
import fu.servlet.ServletFileUpload
import java.io.{File => JFile}

import IteratorConversions._

trait MultiPartMatcher[T] {
  def unapply(req: T): Option[T]
}

case class MultipartData[W](params: String => Seq[String], files: String => W)

trait FileWrapper {
  val name: String
  val contentType: String
  def write(out: JFile): Option[JFile]
}

trait AbstractDiskFile extends FileWrapper {
  def isInMemory: Boolean
  def bytes: Array[Byte]
  def size: Long
  val name: String
  val contentType: String
}

trait AbstractStreamedFile extends FileWrapper {
  def stream[T]: (java.io.InputStream => T) => T
}


/** Base trait for disk-based multi part form data extraction */
trait AbstractDiskExtractor[R] {
  import fu.{FileItemFactory, FileItem => ACFileItem}

  /** @return the number of bytes to load a file into memory before writing to disk */
  def memLimit: Int
  /** @return the directory to write temp files to */
  def tempDir: JFile
  /** @return a configured FileItemFactory to parse a request */
  def factory(writeAfter: Int, writeDir: JFile): FileItemFactory

  /**
    Given a req, extract the multipart form params into a (Map[String, Seq[String]], Map[String, Seq[FileItem]], request).
    The Map is assigned a default value of Nil, so param("p") would return Nil if there
    is no such parameter, or (as normal for servlets) a single empty string if the
    parameter was supplied without a value. */
  def apply(req: R): MultipartData[Seq[AbstractDiskFile]]
}

trait DiskExtractor {
  import fu.disk.{DiskFileItemFactory}

  // TODO come up with sensible default
  val memLimit = Int.MaxValue
  val tempDir = new java.io.File(".")
  def factory(writeAfter: Int, writeDir: JFile) = new DiskFileItemFactory(writeAfter, writeDir)
}

/** Stream-based multi-part form data extractor */
trait StreamedExtractor[R] {
  import fu.{FileItemIterator, FileItemStream}
  import java.io.{InputStream => JInputStream}
  import fu.util.Streams

  /**
    Provides extraction similar to MultiPartParams.Disk, except the second map will
    contain Map[String, Seq[StreamedFileWrapper]] rather than  Map[String, Seq[DiskFileWrapper]].
    @note the seq returned by keys will only return the `first` named value. This is a limitation
    on apache commons file upload streaming interface. To read from the stream iterator,
    you must read before #next is called or the stream read will fail. */
  def apply(req: R): MultipartData[Seq[AbstractStreamedFile]]

  def withStreamedFile[T](istm: JInputStream)(f: java.io.InputStream => T): T = {
    try { f(istm) } finally { istm.close }
  }

  def withStreamedFile[T](fstm: FileItemStream)(f: java.io.InputStream => T): T = {
    val stm = fstm.openStream
    try { f(stm) } finally { stm.close }
  }

  protected def extractStr(fstm: FileItemStream) = withStreamedFile[String](fstm) { stm =>
    Streams.asString(stm)
  }
}

trait TupleGenerator {
  /** generates a tuple of (Map[String, List[A]], Map[String, List[B]]) */
  protected def genTuple[A, B, C](iter: Iterator[C])(f: ((Map[String, List[A]], Map[String, List[B]]), C) => (Map[String, List[A]], Map[String, List[B]])) =
   ((Map.empty[String, List[A]].withDefaultValue(Nil), Map.empty[String, List[B]].withDefaultValue(Nil)) /: iter)(f(_,_))
}

