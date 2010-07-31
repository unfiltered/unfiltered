package unfiltered.request

import javax.servlet.http.HttpServletRequest

import org.apache.commons.{fileupload => fu}

class DiskFileWraper(item: fu.FileItem) {
  def write(out: java.io.File): Option[java.io.File] = try {
    item.write(out)
    Some(out)
  } finally {
    None
  }
  
  def isInMemory = item.isInMemory
  def bytes = item.get
  def size = item.getSize
  val name = item.getName
  val contentType = item.getContentType
}

/** Represents an uploaded file loaded into memory */
// case class ByteFileItem(name: String, content: Array[Byte], contentType: String)

/** Represents an uploaded file exposing a stream to read its contents */
class StreamedFileItem(val name: String, val stream: (java.io.InputStream => Unit) => Unit, val contentType: String)

/** Extactors for multi-part form param processing
  * @note a request can go through one pass of this extractor
  * afterwards, the requests input stream will appear empty
  */
object MultiPartParams {
  import fu.servlet.ServletFileUpload
   
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
      contain Map[String, Seq[StreamedFileItem]] rather than  Map[String, Seq[FileItem]] */
    def unapply(req: HttpServletRequest) =
      if (ServletFileUpload.isMultipartContent(req)) {
        val items =  new ServletFileUpload().getItemIterator(req).asInstanceOf[FileItemIterator]
        val (params, files) = genTuple[String, StreamedFileItem, FileItemStream](items)((maps, item) =>
          if(item.isFormField) (maps._1 + (item.getFieldName -> (extractStr(item) :: maps._1(item.getName))), maps._2)
          else (maps._1, maps._2 + (item.getFieldName -> (new StreamedFileItem(item.getName, withStreamedFile[Unit](item)_, item.getContentType) :: maps._2(item.getFieldName))))
        )
        Some(params, files, req)
      } else None
      
      private def withStreamedFile[T](fstm: FileItemStream)(f: java.io.InputStream => T): T = {
        val stm = fstm.openStream
        try { f(stm) } finally { stm.close }
      }

      private def extractStr(fstm: FileItemStream) = withStreamedFile[String](fstm) { stm =>
        Streams.asString(stm)
      }
  }
  
  /** Configuration info for multi part form data processing */
  object Disk extends AbstractDisk {
    // TODO come up with sensibl default
    val memLimit = Int.MaxValue
    val tempDir = new java.io.File(".")
  }
  
  /** Disk-based multi part form data extractor */
  trait AbstractDisk {
    import fu.{FileItemFactory, FileItem => ACFileItem}
    import fu.disk.{DiskFileItemFactory}
    import java.util.{Iterator => JIterator}

    def memLimit: Int
    def tempDir: java.io.File
    
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
    def unapply(req: HttpServletRequest) =
      if (ServletFileUpload.isMultipartContent(req)) {
        val fact = new DiskFileItemFactory(memLimit, tempDir)
        val items =  new ServletFileUpload(fact).parseRequest(req).iterator.asInstanceOf[JIterator[ACFileItem]]
        val (params, files) = genTuple[String, DiskFileWraper, ACFileItem](items) ((maps, item) =>
          if(item.isFormField) (maps._1 + (item.getFieldName -> (item.getString :: maps._1(item.getFieldName))), maps._2)
          else (maps._1, maps._2 + (item.getFieldName -> (new DiskFileWraper(item) :: maps._2(item.getFieldName))))
        )
        Some(params, files, req)
      } else None
  }
  
  /** generates a tuple of (Map[String, List[A]], Map[String, List[B]]) */
  private def genTuple[A, B, C](iter: Iterator[C])(f: ((Map[String, List[A]], Map[String, List[B]]), C) => (Map[String, List[A]], Map[String, List[B]])) =
   ((Map.empty[String, List[A]].withDefaultValue(Nil), Map.empty[String, List[B]].withDefaultValue(Nil)) /: iter)(f(_,_))
}