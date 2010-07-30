package unfiltered.request

import javax.servlet.http.HttpServletRequest

case class FileItem(name: String, content: Array[Byte], contentType: String)

object Params {
  /** Dress a Java Enumeration in Scala Iterator clothing */
  case class JEnumerationIterator[T](e: java.util.Enumeration[T]) extends Iterator[T] {
    def hasNext: Boolean =  e.hasMoreElements()
    def next: T = e.nextElement()
  }
  /**
    Given a req, extract the request params into a (Map[String, Seq[String]], request).
    The Map is assigned a default value of Nil, so param("p") would return Nil if there
    is no such parameter, or (as normal for servlets) a single empty string if the
    parameter was supplied without a value. */
  def unapply(req: HttpServletRequest) = {
    import org.apache.commons.{fileupload => fu}
    import fu.{FileItemFactory}
    import fu.disk.{DiskFileItemFactory}
    import fu.servlet.{ServletFileUpload}
    
    if (ServletFileUpload.isMultipartContent(req)) {
      val factory = new DiskFileItemFactory(Int.MaxValue, new java.io.File("."))
      val upload = new ServletFileUpload(factory)
      val items = upload.parseRequest(req).toArray.toList collect {
        case x: fu.FileItem => (x.getFieldName, x) }
      val names = (items map { item => item._1 }).distinct
      
      Some((((Map.empty[String, Seq[String]] /: names) ((m, n) => 
          m + (n -> (items filter { _._1 == n } map { _._2.getString }) )
        )).withDefaultValue(Nil),
        ((Map.empty[String, Seq[FileItem]] /: names) ((m, n) => 
            m + (n -> (items filter { x => x._1 == n && !x._2.isFormField } map { x =>
              FileItem(x._2.getName, x._2.get, x._2.getContentType) }) )
          )).withDefaultValue(Nil),
        req))
    } else {
      val names = JEnumerationIterator[String](req.getParameterNames.asInstanceOf[java.util.Enumeration[String]])
      Some(((Map.empty[String, Seq[String]] /: names) ((m, n) => 
          m + (n -> req.getParameterValues(n))
        )).withDefaultValue(Nil),
        Map.empty[String, Seq[FileItem]],
        req)     
    }
  }

  abstract class Named[T](name: String, f: Seq[String] => Option[T]) {
    def unapply(params: Map[String, Seq[String]]) = f(params(name)) map {
      (_, params)
    }
  }

  class Chained[T, R](f: T => R) extends (T => R) {
    def apply(t: T) = f(t)
    def ~> [Then] (that: R => Then) = new Chained(this andThen that)
  }

  val first = new Chained( { seq: Seq[String] => seq.headOption } )

  val trimmed = { (_: Option[String]) map { _.trim } }
  val nonempty = { (_: Option[String]) filter { ! _.isEmpty  } }

  val int = { s: Option[String] =>
    try { s map { _.toInt } } catch { case _ => None }
  }
}