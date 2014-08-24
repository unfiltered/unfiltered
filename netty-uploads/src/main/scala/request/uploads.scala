package unfiltered.netty.request

import unfiltered.netty.{ ReceivedMessage, RequestBinding }
import unfiltered.request.{
  AbstractDiskExtractor,
  AbstractDiskFile,  
  AbstractStreamedFile,  
  DiskExtractor,
  MultiPartMatcher,
  MultipartData,
  RequestContentType,
  StreamedExtractor,
  TupleGenerator
}
import unfiltered.request.io.FileIO
import unfiltered.util.control.NonFatal

import io.netty.handler.codec.http.multipart.{
  Attribute,
  FileUpload,
  InterfaceHttpData
}

import scala.util.control.Exception.allCatch

import java.io.{ File, FileInputStream, InputStream }

// fixme(doug): there's only one concrete impl. is this really needed?
trait MultiPartCallback
case class Decode(binding: MultiPartBinding) extends MultiPartCallback

// todo(doug): make sure decoder gets destroyed after responding
class MultiPartBinding(val decoder: Option[PostDecoder], msg: ReceivedMessage) extends RequestBinding(msg)

/** Matches requests that have multipart content */
object MultiPart extends MultiPartMatcher[RequestBinding] {  
  private[this] val Type = "multipart/form-data"
  private[this] val Boundary = "boundary"

  def unapply(req: RequestBinding) =
    RequestContentType(req) match {
      case Some(r) if isMultipart(r) => Some(req)
      case _ => None
    }

  /** Check the ContentType header value to determine whether the request is multipart
   *  note(doug) - future versions of netty define HttpPostRequestDecoder.isMultipart(_)
   *               https://github.com/netty/netty/blob/netty-4.1.0.Beta1/codec-http/src/main/java/io/netty/handler/codec/http/multipart/HttpPostRequestDecoder.java#L141
   *               we should eventually prefer that and do away with alot of this code
   */
  private def isMultipart(contentType: String) = {
    // Check if the Post is using "multipart/form-data; boundary=--89421926422648"
    splitContentTypeHeader(contentType) match {
      case (Some(a), Some(b)) if a.toLowerCase.startsWith(Type) && b.toLowerCase.startsWith(Boundary) =>
        b.split("=").length == 2
      case _ => false
    }
  }

  /** Split the Content-Type header value into two strings */
  private def splitContentTypeHeader(sb: String): (Option[String],Option[String]) = {
    def nonEmpty(s: String) = if (s.isEmpty) None else Some(s)

    val (contentType, params) = sb.trim.span(!_.isWhitespace)
    val ct = if (contentType.endsWith(";")) contentType.dropRight(1) else contentType
    (nonEmpty(ct), nonEmpty(params.trim))
  }
}

object MultiPartParams {

  /** Streamed multi-part data extractor */
  object Streamed extends StreamedExtractor[RequestBinding] {    
    def apply(req: RequestBinding) = {

      val decoder = req match {
        case r: MultiPartBinding => r.decoder
        case _ => PostDecoder(req)
      }
      val params = decoder.map(_.parameters).getOrElse(Nil)
      val files = decoder.map(_.fileUploads).getOrElse(Nil)

      /** attempt to extract the first named param from the stream */
      def extractParam(name: String): Seq[String] = {
        params.filter(_.getName == name).map(_.getValue).toSeq
      }

      /** attempt to extract the first named file from the stream */
      def extractFile(name: String): Seq[StreamedFileWrapper] = {
         files.filter(_.getName == name).map(new StreamedFileWrapper(_)).toSeq
      }
      MultipartData(extractParam _,extractFile _)
    }
  }

  /** On-disk multi-part form data extractor */
  object Disk extends AbstractDisk with DiskExtractor

  /** All in memory multi-part form data extractor.
      This exposes a very specific class of file references
      intended for use in environments such as GAE where writing
      to disk is prohibited.
      */
  object Memory extends StreamedExtractor[RequestBinding] {
    def apply(req: RequestBinding) = {

      val decoder = req match {
        case r: MultiPartBinding => r.decoder
        case _ => PostDecoder(req)
      }
      val params = decoder.map(_.parameters).getOrElse(Nil)
      val files = decoder.map(_.fileUploads).getOrElse(Nil)

      /** attempt to extract the first named param from the stream */
      def extractParam(name: String): Seq[String] = {
        params.filter(_.getName == name).map(_.getValue).toSeq
      }

      /** attempt to extract the first named file from the stream */
      def extractFile(name: String): Seq[StreamedFileWrapper] = {
         files.filter(_.getName == name).map(new MemoryFileWrapper(_)).toSeq
      }
      MultipartData(extractParam _, extractFile _)      
    }
  }
}

/** Netty extractor for multi-part data destined for disk. */
trait AbstractDisk
  extends AbstractDiskExtractor[RequestBinding]
  with TupleGenerator {
  def apply(req: RequestBinding) = {
    val items = req match {
      case r: MultiPartBinding => r.decoder.map(_.items).getOrElse(Nil).toIterator
      case _ => PostDecoder(req).map(_.items).getOrElse(Nil).toIterator
    }  

    val (params, files) = genTuple[String, DiskFileWrapper, InterfaceHttpData](items) ((maps, item) => item match {
        case file: FileUpload =>
          (maps._1, maps._2 + (file.getName -> (new DiskFileWrapper(file) :: maps._2(file.getName))))
        case attr: Attribute =>
          (maps._1 + (attr.getName -> (attr.getValue :: maps._1(attr.getName))), maps._2)
      })

    MultipartData(params, files)
  }
}

class StreamedFileWrapper(item: FileUpload)
  extends AbstractStreamedFile
  with FileIO {

  val bstm = new FileInputStream(item.getFile)

  def write(out: File): Option[File] = allCatch.opt {
    stream { stm =>
      toFile(stm)(out)
      out
    }
  }

  def stream[T]: (InputStream => T) => T =
    MultiPartParams.Streamed.withStreamedFile[T](bstm)_
  val name = item.getFilename
  val contentType = item.getContentType
}

class DiskFileWrapper(item: FileUpload)
  extends AbstractDiskFile {
  def write(out: File): Option[File] = try {
    item.renameTo(out)
    Some(out)
  } catch {
    case NonFatal(_) => None
  }

  def inMemory = item.isInMemory
  def bytes = item.get
  def size = item.length
  val name = item.getFilename
  val contentType = item.getContentType
}

/** Wrapper for an uploaded file with write functionality disabled. */
class MemoryFileWrapper(item: FileUpload)
  extends StreamedFileWrapper(item) {
  /** in memory file uploads never write to disk. calling this method
   *  has no effect*/
  override def write(out: File) = None
  def isInMemory = item.isInMemory
  def bytes = item.get
  def size = item.length
}
