package unfiltered.request

import scala.util.control.Exception.allCatch

import java.io.{File => JFile}

trait MultiPartMatcher[T] {
  def unapply(req: T): Option[T]
}

/** Multipart file upload utilities should extract data
 *  using this common format */
case class MultipartData[W](
  params: String => Seq[String], files: String => W)

/** Describes an uploaded file, its content type, and
 *  a means of copying its content to another file */
trait FileWrapper {
  val name: String
  val contentType: String
  def write(out: JFile): Option[JFile]
}

/** Describes some abstract file which exists on
 *  disk or in memory */
trait AbstractDiskFile extends FileWrapper {
  def inMemory: Boolean
  def bytes: Array[Byte]
  def size: Long
  val name: String
  val contentType: String
}

/** Describes a file whose content may be written to a stream */
trait AbstractStreamedFile extends FileWrapper {
  def stream[T]: (java.io.InputStream => T) => T
}

/** Base trait for disk-based multi part form data extraction */
trait AbstractDiskExtractor[R] {

  /** @return the number of bytes to load a file into memory
   *  before writing to disk */
  def memLimit: Int

  /** @return the directory to write temp files to */
  def tempDir: JFile

  /**
    * Given a req, extract the multipart form params into a
    * (Map[String, Seq[String]], Map[String, Seq[FileItem]], request).
    * The Map is assigned a default value of Nil, so param("p") would
    * return Nil if there is no such parameter, or (as normal for
    * servlets) a single empty string if the parameter was
    * supplied without a value. */
  def apply(req: R): MultipartData[Seq[AbstractDiskFile]]
}

trait DiskExtractor {
  val memLimit = Int.MaxValue
  val tempDir = new JFile(".")
}

/** Stream-based multi-part form data extractor */
trait StreamedExtractor[R] {
  import java.io.{InputStream => JInputStream}

  /**
    * Provides extraction similar to MultiPartParams.Disk, except
    * the second map will contain Map[String, Seq[StreamedFileWrapper]] rather
    * than  Map[String, Seq[DiskFileWrapper]].
    * @note the seq returned by keys will only return the `first`
    * named value. This is based on a limitation on apache commons file upload
    * streaming interface. To read from the stream iterator,
    * you must read before #next is called or the stream read will fail. */
  def apply(req: R): MultipartData[Seq[AbstractStreamedFile]]

  def withStreamedFile[T](istm: JInputStream)(f: java.io.InputStream => T): T = {
    try { f(istm) } finally { istm.close }
  }

}

trait TupleGenerator {
  /** generates a tuple of (Map[String, List[A]], Map[String, List[B]]) */
  protected def genTuple[A, B, C](iter: Iterator[C])(f: ((Map[String, List[A]], Map[String, List[B]]), C) => (Map[String, List[A]], Map[String, List[B]])) =
   ((Map.empty[String, List[A]].withDefaultValue(Nil), Map.empty[String, List[B]].withDefaultValue(Nil)) /: iter)(f(_,_))
}

