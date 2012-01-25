package unfiltered.netty.uploads

import scala.util.control.Exception.allCatch

import unfiltered.netty.RequestBinding

import unfiltered.request.HttpRequest
import unfiltered.request.{MultiPartMatcher,MultipartData}
import unfiltered.request.{DiskExtractor,AbstractDiskExtractor, AbstractDiskFile,StreamedExtractor,AbstractStreamedFile }
import unfiltered.request.TupleGenerator

import org.jboss.{netty => jnetty}  // 3.x
import jnetty.handler.codec.http.{HttpRequest => NHttpRequest}

import io.{netty => ionetty}        // 4.x
import ionetty.handler.codec.http.{HttpPostRequestDecoder => IOHttpPostRequestDecoder}
import ionetty.handler.codec.http.{DefaultHttpDataFactory => IODefaultHttpDataFactory}
import ionetty.handler.codec.http.{InterfaceHttpData => IOInterfaceHttpData}
import ionetty.handler.codec.http.{Attribute => IOAttribute}
import ionetty.handler.codec.http.{FileUpload => IOFileUpload}

import java.io.{File => JFile}

/** Matches requests that have multipart content */
object MultiPart extends MultiPartMatcher[RequestBinding] {
  def unapply(req: RequestBinding) = {
    if (PostDecoder(req.underlying.request).isMultipart)
      Some(req)
    else None
  }
}

object MultiPartParams extends TupleGenerator {

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

  trait AbstractDisk extends AbstractDiskExtractor[RequestBinding] {
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
  
}

class MemoryFileWrapper(item: IOFileUpload) extends StreamedFileWrapper(item) {
  override def write(out: JFile) = { 
    //error("File writing is not permitted")
    None
  }
  def isInMemory = item.isInMemory
  def bytes = item.get
  def size = item.length
}

class StreamedFileWrapper(item: IOFileUpload) extends AbstractStreamedFile
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

class DiskFileWrapper(item: IOFileUpload) extends AbstractDiskFile {
  def write(out: JFile): Option[JFile] = try {
    item.renameTo(out)
    Some(out)
  } catch {
    case _ => None
  }

  def isInMemory = item.isInMemory
  def bytes = item.get
  def size = item.length
  val name = item.getFilename
  val contentType = item.getContentType
}

/** A PostDecoder wraps a HttpPostRequestDecoder which is available in netty 4 onwards. We implicitly convert a netty 3 HttpRequest to a netty 4 HttpRequest to enable us to use the new multi-part decoding features (until such time as netty 4 is officially released and unfiltered uses it by default). Decoding chunked messages, while supported by netty 4 is not implemented here, so use of a HttpChunkAggregator in the handler pipeline is mandatory for now. */
class PostDecoder(req: NHttpRequest) {
  /** Enable implicit conversion between netty 3.x and 4.x. One day this won't be needed any more :) */
  import Implicits._

  import scala.collection.JavaConversions._

  private lazy val decoder = try {
    val factory = new IODefaultHttpDataFactory(IODefaultHttpDataFactory.MINSIZE)
    Some(new IOHttpPostRequestDecoder(factory, req))
  } catch {
    /** Q. Would it be more useful to throw errors here? */
    case e: IOHttpPostRequestDecoder.ErrorDataDecoderException => None
    /** GET method. Can't create a decoder. */
    case e: IOHttpPostRequestDecoder.IncompatibleDataDecoderException => None
  }

  def isMultipart: Boolean = decoder.map(_.isMultipart).getOrElse(false)

  def items: List[IOInterfaceHttpData] = decoder.map(_.getBodyHttpDatas.toList).getOrElse(List())

  def fileUploads = items collect { case file: IOFileUpload => file }

  def parameters = items collect { case param: IOAttribute => param }
}

object PostDecoder{
  def apply(req: NHttpRequest) = new PostDecoder(req)
}