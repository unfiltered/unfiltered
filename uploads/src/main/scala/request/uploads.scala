package unfiltered.request

import scala.util.control.Exception.allCatch

import javax.servlet.http.HttpServletRequest

import org.apache.commons.{fileupload => fu}
import fu.servlet.ServletFileUpload
import java.io.{File => JFile}

/** Matches requests that have multipart content */
object MultiPart {
  def unapply(req: HttpRequest[HttpServletRequest]) =
    if (ServletFileUpload.isMultipartContent(req.underlying))
      Some(req)
    else None
}

case class MultipartData[W](params: String => Seq[String], files: String => W)

trait FileWrapper {
  val name: String
  val contentType: String
  def write(out: JFile): Option[JFile]
}

/** Represents an uploaded file loaded into memory (and possibly written to disk) */
class DiskFileWrapper(item: fu.FileItem) extends FileWrapper {
  def write(out: JFile): Option[JFile] = try {
    item.write(out)
    Some(out)
  } catch {
    case _ => None
  }

  def isInMemory = item.isInMemory
  def bytes = item.get
  def size = item.getSize
  val name = item.getName
  val contentType = item.getContentType
}

/** Represents an uploaded file exposing a stream to read its contents */
class StreamedFileWrapper(fstm: fu.FileItemStream) extends FileWrapper
  with unfiltered.request.io.FileIO {

  def write(out: JFile): Option[JFile] = allCatch.opt {
    stream { stm =>
      toFile(stm)(out)
      out
    }
  }

  def stream[T]: (java.io.InputStream => T) => T =
    MultiPartParams.Streamed.withStreamedFile[T](fstm)_
  val name = fstm.getName
  val contentType = fstm.getContentType
}

/** Extactors for multi-part form param processing
  * @note a request can go through one pass of this extractor
  * afterwards, the requests input stream will appear empty
  */
object MultiPartParams {

  /** Stream-based multi-part form data extractor */
  object Streamed {
    import fu.{FileItemIterator, FileItemStream}
    import fu.util.Streams

    case class FIIteratorWrapper(i: FileItemIterator) extends Iterator[FileItemStream] {
      def hasNext: Boolean = i.hasNext
      def next(): FileItemStream = i.next
    }

    /** convert apache commons file iterator to scala iterator */
    implicit def acfi2si(i : FileItemIterator) = new FIIteratorWrapper(i)

    /**
      Provides extraction similar to MultiPartParams.Disk, except the second map will
      contain Map[String, Seq[StreamedFileWrapper]] rather than  Map[String, Seq[DiskFileWrapper]].
      @note the seq returned by keys will only return the `first` named value. This is a limitation
      on apache commons file upload streaming interface. To read from the stream iterator,
      you must read before #next is called or the stream read will fail. */
    def apply(req: HttpRequest[HttpServletRequest]) = {
      def items = new ServletFileUpload().getItemIterator(req.underlying).asInstanceOf[FileItemIterator]
      /** attempt to extract the first named param from the stream */
      def extractParam(name: String): Seq[String] = {
         items.find(f => f.getFieldName == name && f.isFormField) match {
           case Some(p) => Seq(extractStr(p))
           case _ => Nil
         }
      }
      /** attempt to extract the first named file from the stream */
      def extractFile(name: String): Seq[StreamedFileWrapper] = {
         items.find(f => f.getFieldName == name && !f.isFormField) match {
           case Some(f) => Seq(new StreamedFileWrapper(f))
           case _ => Nil
         }
      }
      MultipartData(extractParam _,extractFile _)
    }

    def withStreamedFile[T](fstm: FileItemStream)(f: java.io.InputStream => T): T = {
      val stm = fstm.openStream
      try { f(stm) } finally { stm.close }
    }

    private def extractStr(fstm: FileItemStream) = withStreamedFile[String](fstm) { stm =>
      Streams.asString(stm)
    }
  }

  /** On-disk  multi-part form data extractor */
  object Disk extends AbstractDisk {
    import fu.disk.{DiskFileItemFactory}

    // TODO come up with sensible default
    val memLimit = Int.MaxValue
    val tempDir = new java.io.File(".")
    def factory(writeAfter: Int, writeDir: JFile) = new DiskFileItemFactory(writeAfter, writeDir)
  }

  /** All in memory multi-part form data extractor.
      This exposes a very specific class of file references
      intended for use in environments such as GAE where writing
      to disk is prohibited.
      */
  object Memory extends AbstractDisk {

    class ByteArrayFileItem(var fieldName: String,
        val contentType: String,
     	  var formField: Boolean,
        val name: String,
        val sizeThreshold: Int) extends fu.FileItem {

        import java.io.{InputStream, ByteArrayInputStream,
          OutputStream, ByteArrayOutputStream}

        var cache: Option[Array[Byte]] = None
        val out = new ByteArrayOutputStream()
        override def delete {}
        override def get = cache getOrElse {
          val content = out.toByteArray
          cache = Some(content)
          content
        }

        override def getContentType = contentType
        override def getFieldName = fieldName
        override def getInputStream: InputStream = new ByteArrayInputStream(get)
        override def getName = name
        override def getOutputStream = out
        override def getSize = get.size
        override def getString(charset: String) = new String(get, charset)
        override def getString = getString("UTF-8")
        override def isFormField = formField
        override def isInMemory = true
        override def setFieldName(value: String) { fieldName = value }
        override def setFormField(state: Boolean) { formField = state }
        override def write(file: JFile) { error("File writing is not permitted") }
    }

    class ByteArrayFileItemFactory extends fu.FileItemFactory {
       override def createItem(fieldName: String , contentType: String ,
    	                      isFormField: Boolean , fileName: String ) = new ByteArrayFileItem(
    	                        fieldName, contentType, isFormField, fileName, Int.MaxValue
    	                      )
    }

    val memLimit = Int.MaxValue
    val tempDir = new java.io.File(".")
    def factory(writeAfter: Int, writeDir: JFile) = new ByteArrayFileItemFactory
  }

  /** Base trait for disk-based multi part form data extraction */
  trait AbstractDisk {
    import fu.{FileItemFactory, FileItem => ACFileItem}
    import java.util.{Iterator => JIterator}

    /** @return the number of bytes to load a file into memory before writing to disk */
    def memLimit: Int
    /** @return the directory to write temp files to */
    def tempDir: JFile
    /** @return a configured FileItemFactory to parse a request */
    def factory(writeAfter: Int, writeDir: JFile): FileItemFactory

    case class JIteratorWrapper[A](i: JIterator[A]) extends Iterator[A] {
      def hasNext: Boolean = i.hasNext
      def next(): A = i.next
    }

    /** convert java iterator to scala iterator */
    implicit def ji2si[A](i : JIterator[A]) = new JIteratorWrapper[A](i)

    /**
      Given a req, extract the multipart form params into a (Map[String, Seq[String]], Map[String, Seq[FileItem]], request).
      The Map is assigned a default value of Nil, so param("p") would return Nil if there
      is no such parameter, or (as normal for servlets) a single empty string if the
      parameter was supplied without a value. */
    def apply(req: HttpRequest[HttpServletRequest]) = {
      val items =  new ServletFileUpload(factory(memLimit, tempDir)).parseRequest(
        req.underlying
      ).iterator.asInstanceOf[JIterator[ACFileItem]]
      val (params, files) = genTuple[String, DiskFileWrapper, ACFileItem](items) ((maps, item) =>
        if(item.isFormField) (maps._1 + (item.getFieldName -> (item.getString :: maps._1(item.getFieldName))), maps._2)
        else (maps._1, maps._2 + (item.getFieldName -> (new DiskFileWrapper(item) :: maps._2(item.getFieldName))))
      )
      MultipartData(params, files)
    }
  }

  /** generates a tuple of (Map[String, List[A]], Map[String, List[B]]) */
  private def genTuple[A, B, C](iter: Iterator[C])(f: ((Map[String, List[A]], Map[String, List[B]]), C) => (Map[String, List[A]], Map[String, List[B]])) =
   ((Map.empty[String, List[A]].withDefaultValue(Nil), Map.empty[String, List[B]].withDefaultValue(Nil)) /: iter)(f(_,_))
}
