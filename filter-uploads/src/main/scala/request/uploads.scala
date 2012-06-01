package unfiltered.filter.request

import unfiltered.request._
import unfiltered.request.{
  MultiPartMatcher, MultipartData,AbstractDiskFile,
  TupleGenerator, AbstractStreamedFile, StreamedExtractor,
  AbstractDiskExtractor, DiskExtractor }

import unfiltered.filter.util.IteratorConversions
import scala.util.control.Exception.allCatch

import javax.servlet.http.HttpServletRequest

import org.apache.commons.{fileupload => fu}
import fu.servlet.ServletFileUpload
import java.io.{File => JFile}

import IteratorConversions._

/** Matches requests that have multipart content */
object MultiPart extends MultiPartMatcher[HttpRequest[HttpServletRequest]] {
  def unapply(req: HttpRequest[HttpServletRequest]) =
    if (ServletFileUpload.isMultipartContent(req.underlying))
      Some(req)
    else None
}

/** Represents an uploaded file loaded into memory (and possibly written to disk) */
class DiskFileWrapper(item: fu.FileItem) extends AbstractDiskFile {
  def write(out: JFile): Option[JFile] = try {
    item.write(out)
    Some(out)
  } catch {
    case _ => None
  }

  def inMemory = item.isInMemory
  def bytes = item.get
  def size = item.getSize
  val name = item.getName
  val contentType = item.getContentType
}

/** Represents an uploaded file exposing a stream to read its contents */
class StreamedFileWrapper(fstm: fu.FileItemStream) extends AbstractStreamedFile
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
object MultiPartParams extends TupleGenerator {

  object Streamed extends StreamedExtractor[HttpRequest[HttpServletRequest]] {
    import fu.{FileItemIterator, FileItemStream}
    import fu.util.Streams
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

    protected def extractStr(fstm: FileItemStream) = withStreamedFile[String](fstm) { stm =>
      Streams.asString(stm)
    }
  }

  /** On-disk  multi-part form data extractor */
  object Disk extends AbstractDisk with DiskExtractor

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

    override def factory(writeAfter: Int, writeDir: JFile) = new ByteArrayFileItemFactory
    val memLimit = Int.MaxValue
    val tempDir = new java.io.File(".")
  }

  trait AbstractDisk extends AbstractDiskExtractor[HttpRequest[HttpServletRequest]] {
    import fu.{FileItemFactory, FileItem => ACFileItem}
    import java.util.{Iterator => JIterator}
    import fu.disk.{DiskFileItemFactory}
    
     /** @return a configured FileItemFactory to parse a request */
    def factory(writeAfter: Int, writeDir: JFile): FileItemFactory =
      new DiskFileItemFactory(writeAfter, writeDir)

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
}
