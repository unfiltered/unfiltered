package unfiltered.netty.uploads

import unfiltered.netty.RequestBinding

import unfiltered.request.HttpRequest
import unfiltered.request.{MultiPartMatcher,MultipartData}
import unfiltered.request.{DiskExtractor,AbstractDiskExtractor, AbstractDiskFile}
import unfiltered.request.TupleGenerator

import org.jboss.{netty => jnetty}  // 3.x
import io.{netty => ionetty}        // 4.x
import jnetty.handler.codec.http.{HttpRequest => NHttpRequest}
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

  object Disk extends AbstractDisk with DiskExtractor

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
  val name = item.getName
  val contentType = item.getContentType
}

object PostDecoder{
  def apply(req: NHttpRequest) = new PostDecoder(req)
}

class PostDecoder(req: NHttpRequest) {
  import Implicits._
  import scala.collection.JavaConversions._

  private lazy val decoder = try {
    val factory = new IODefaultHttpDataFactory(IODefaultHttpDataFactory.MINSIZE)
    Some(new IOHttpPostRequestDecoder(factory, req))
  } catch {
    case e: IOHttpPostRequestDecoder.ErrorDataDecoderException => None
    /** GET method. Can't create a decoder. */
    case e: IOHttpPostRequestDecoder.IncompatibleDataDecoderException => None
  }

  def isMultipart: Boolean = decoder.map(_.isMultipart).getOrElse(false)

  def items: List[IOInterfaceHttpData] = decoder.map(_.getBodyHttpDatas.toList).getOrElse(List())

  def fileUploads = items collect { case file: IOFileUpload => file }
}