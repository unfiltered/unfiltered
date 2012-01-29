package unfiltered.netty.request

import scala.util.control.Exception.allCatch

import unfiltered.netty.RequestBinding
import unfiltered.request.HttpRequest

import unfiltered.request.{
  MultiPartMatcher,MultipartData, AbstractDiskFile, TupleGenerator,
  AbstractStreamedFile, StreamedExtractor, AbstractDiskExtractor,
  DiskExtractor }

import org.jboss.{netty => jnetty}  // 3.x
import jnetty.handler.codec.http.{HttpRequest => NHttpRequest}

import io.{netty => ionetty}        // 4.x
import ionetty.handler.codec.http.{InterfaceHttpData => IOInterfaceHttpData}
import ionetty.handler.codec.http.{Attribute => IOAttribute}
import ionetty.handler.codec.http.{FileUpload => IOFileUpload}

import java.io.{File => JFile}

/** Matches requests that have multipart content */
object MultiPart extends MultiPartMatcher[RequestBinding] {
  def unapply(req: RequestBinding) = {
    /** TODO: Find a way to detect whether the req is multipart without parsing the whole thing first. 
    Maybe something like this:
    https://github.com/netty/netty/blob/master/codec-http/src/main/java/io/netty/handler/codec/http/HttpPostRequestDecoder.java#L246 */
    if (PostDecoder(req.underlying.request).isMultipart)
      Some(req)
    else None
  }
}

object MultiPartParams {

  /** Streamed multi-part data extractor */
  object Streamed extends StreamedExtractor[RequestBinding] {
    import java.util.{Iterator => JIterator}
    def apply(req: RequestBinding) = {

      val decoder = PostDecoder(req.underlying.request)
      val params = decoder.parameters
      val files = decoder.fileUploads

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

      val decoder = PostDecoder(req.underlying.request)
      val params = decoder.parameters
      val files = decoder.fileUploads

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
    val items = PostDecoder(req.underlying.request).items.toIterator

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
  import java.io.{ByteArrayInputStream => JByteArrayInputStream}

  val bstm = new JByteArrayInputStream(item.get)

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
    case _ => None
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
