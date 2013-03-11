package unfiltered.netty.request

import unfiltered.netty
import unfiltered.netty._

import scala.util.control.Exception.allCatch

import unfiltered.netty.RequestBinding
import unfiltered.request.HttpRequest
import unfiltered.request.RequestContentType

import unfiltered.request.{
  MultiPartMatcher,MultipartData, AbstractDiskFile, TupleGenerator,
  AbstractStreamedFile, StreamedExtractor, AbstractDiskExtractor,
  DiskExtractor }

import org.jboss.{netty => jnetty}
import jnetty.handler.codec.http.{HttpRequest => NHttpRequest}
import jnetty.handler.codec.http.{InterfaceHttpData => IOInterfaceHttpData}
import jnetty.handler.codec.http.{Attribute => IOAttribute}
import jnetty.handler.codec.http.{FileUpload => IOFileUpload}

import java.io.{File => JFile}

trait MultiPartCallback
case class Decode(binding: MultiPartBinding) extends MultiPartCallback

class MultiPartBinding(val decoder: Option[PostDecoder], msg: ReceivedMessage) extends RequestBinding(msg)

/** Matches requests that have multipart content */
object MultiPart extends MultiPartMatcher[RequestBinding] {
  def unapply(req: RequestBinding) = {
    RequestContentType(req) match {
      case Some(r) if isMultipart(r) => Some(req)
      case _ => None
    }
  }

  /** Check the ContentType header value to determine whether the request is multipart */
  private def isMultipart(contentType: String) = {
    val Multipart = "multipart/form-data"
    val Boundary = "boundary"
    // Check if the Post is using "multipart/form-data; boundary=--89421926422648"
    splitContentTypeHeader(contentType) match {
      case (Some(a), Some(b)) if a.toLowerCase.startsWith(Multipart) && b.toLowerCase.startsWith(Boundary) =>
        b.split("=").length == 2
      case _ => false
    }
  }

  /** Split the Content-Type header value into two strings */
  private def splitContentTypeHeader(sb: String): Tuple2[Option[String],Option[String]] = {
    def nonEmpty(s: String) = if (s.isEmpty) None else Some(s)

    val (contentType, params) = sb.trim.span(!_.isWhitespace)
    val ct = if (contentType.endsWith(";")) contentType.dropRight(1) else contentType
    (nonEmpty(ct), nonEmpty(params.trim))
  }
}

object MultiPartParams {

  /** Streamed multi-part data extractor */
  object Streamed extends StreamedExtractor[RequestBinding] {
    import java.util.{Iterator => JIterator}
    def apply(req: RequestBinding) = {

      val decoder = req match {
        case r: MultiPartBinding => r.decoder
        case _ => PostDecoder(req.underlying.request)
      }
      val params = decoder.map(_.parameters).getOrElse(List())
      val files = decoder.map(_.fileUploads).getOrElse(List())

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
    import java.util.{Iterator => JIterator}
    def apply(req: RequestBinding) = {

      val decoder = req match {
        case r: MultiPartBinding => r.decoder
        case _ => PostDecoder(req.underlying.request)
      }
      val params = decoder.map(_.parameters).getOrElse(List())
      val files = decoder.map(_.fileUploads).getOrElse(List())

      /** attempt to extract the first named param from the stream */
      def extractParam(name: String): Seq[String] = {
        params.filter(_.getName == name).map(_.getValue).toSeq
      }

      /** attempt to extract the first named file from the stream */
      def extractFile(name: String): Seq[StreamedFileWrapper] = {
         files.filter(_.getName == name).map(new MemoryFileWrapper(_)).toSeq
      }
      MultipartData(extractParam _,extractFile _)
      
    }
  }
}

/** Netty extractor for multi-part data destined for disk. */
trait AbstractDisk
extends AbstractDiskExtractor[RequestBinding] with TupleGenerator {
  import java.util.{Iterator => JIterator}
  def apply(req: RequestBinding) = {

    val items = req match {
      case r: MultiPartBinding => r.decoder.map(_.items).getOrElse(List()).toIterator
      case _ => PostDecoder(req.underlying.request).map(_.items).getOrElse(List()).toIterator 
    }  

    val (params, files) = genTuple[String, DiskFileWrapper, IOInterfaceHttpData](items) ((maps, item) => item match {
        case file: IOFileUpload =>
          (maps._1, maps._2 + (file.getName -> (new DiskFileWrapper(file) :: maps._2(file.getName))))
        case attr: IOAttribute =>
          (maps._1 + (attr.getName -> (attr.getValue :: maps._1(attr.getName))), maps._2)
      })

    MultipartData(params, files)
  }
}

class StreamedFileWrapper(item: IOFileUpload)
extends AbstractStreamedFile
  with unfiltered.request.io.FileIO {
  import java.io.{FileInputStream => JFileInputStream}

  val bstm = new JFileInputStream(item.getFile)

  def write(out: JFile): Option[JFile] = allCatch.opt {
    stream { stm =>
      toFile(stm)(out)
      out
    }
  }

  def stream[T]: (java.io.InputStream => T) => T =
    MultiPartParams.Streamed.withStreamedFile[T](bstm)_
  val name = item.getFilename
  val contentType = item.getContentType
}

class DiskFileWrapper(item: IOFileUpload)
extends AbstractDiskFile {
  def write(out: JFile): Option[JFile] = try {
    item.renameTo(out)
    Some(out)
  } catch {
    case _: Throwable => None
  }

  def inMemory = item.isInMemory
  def bytes = item.get
  def size = item.length
  val name = item.getFilename
  val contentType = item.getContentType
}

/** Wrapper for an uploaded file with write functionality disabled. */
class MemoryFileWrapper(item: IOFileUpload)
extends StreamedFileWrapper(item) {
  override def write(out: JFile) = { 
    //error("File writing is not permitted")
    None
  }
  def isInMemory = item.isInMemory
  def bytes = item.get
  def size = item.length
}
