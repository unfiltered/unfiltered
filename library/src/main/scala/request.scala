package unfiltered.request

import javax.servlet.ServletRequest
import javax.servlet.http.HttpServletRequest

object HTTPS {
  def unapply(req: HttpServletRequest) = 
    if (req.getProtocol.equalsIgnoreCase("HTTPS")) Some(req)
    else None
}

class Method(method: String) {
  def unapply(req: HttpServletRequest) = 
    if (req.getMethod.equalsIgnoreCase(method)) Some(req)
    else None
}

object GET extends Method("GET")
object POST extends Method("POST")
object PUT extends Method("PUT")
object DELETE extends Method("DELETE")
object HEAD extends Method("HEAD")

object Path {
  def unapply(req: HttpServletRequest) = Some((req.getRequestURI, req))
}
object Seg {
  def unapply(path: String): Option[List[String]] = path.split("/").toList match {
    case "" :: rest => Some(rest) // skip a leading slash
    case all => Some(all)
  }
}

class RequestHeader(name: String) {
  def unapplySeq(req: HttpServletRequest): Option[Seq[String]] = { 
    def headers(e: java.util.Enumeration[_]): List[String] =
      if (e.hasMoreElements) e.nextElement match {
        case v: String => v :: headers(e)
        case _ => headers(e)
      } else Nil
    Some(headers(req.getHeaders(name)))
  }
}

// http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.10

object Accept extends RequestHeader("Accept")
object AcceptCharset extends RequestHeader("Accept-Charset")
object AcceptEncoding extends RequestHeader("Accept-Encoding")
object AcceptLanguage extends RequestHeader("Accept-Language")
object Authorization extends RequestHeader("Authorization")
object Connection extends RequestHeader("Connection")
object Expect extends RequestHeader("Expect")
object From extends RequestHeader("From")
object Host extends RequestHeader("Host")
object IfMatch extends RequestHeader("If-Match")
object IfModifiedSince extends RequestHeader("If-Modified-Since")
object IfNoneMatch extends RequestHeader("If-None-Match")
object IfRange extends RequestHeader("If-Range")
object IfUnmodifiedSince extends RequestHeader("If-Unmodified-Since")
object MaxForwards extends RequestHeader("Max-Forwards")
object ProxyAuthorization extends RequestHeader("Proxy-Authorization")
object Range extends RequestHeader("Range")
object Referer extends RequestHeader("Referer")
object TE extends RequestHeader("TE")
object Upgrade extends RequestHeader("Upgrade")
object UserAgent extends RequestHeader("User-Agent")
object Via extends RequestHeader("Via")

object InStream {
  def unapply(req: HttpServletRequest) = Some(req.getInputStream, req)
}

object Read {
  def unapply(req: HttpServletRequest) = Some(req.getReader, req)
}

object Bytes {
  def unapply(req: HttpServletRequest) = {
    val InStream(in, _) = req
    val bos = new java.io.ByteArrayOutputStream
    val ba = new Array[Byte](4096)
    /* @scala.annotation.tailrec */ def read {
      val len = in.read(ba)
      if (len > 0) bos.write(ba, 0, len)
      if (len >= 0) read
    }
    read
    in.close
    bos.toByteArray match {
      case Array() => None
      case ba => Some(ba, req)
    }
  }
}

object Params {
  /** Dress a Java Enumeration in Scala Iterator clothing */
  case class JEnumerationIterator[T](e: java.util.Enumeration[T]) extends Iterator[T] {
    def hasNext: Boolean =  e.hasMoreElements()
    def next: T = e.nextElement()
  }
  /**
    Given a req, extract the request params into a (Map[String, Seq[String]], requset).
    The Map is assigned a default value of Nil, so param("p") would return Nil if there
    is no such parameter, or (as normal for servlets) a single empty string if the
    parameter was supplied without a value. */
  def unapply(req: HttpServletRequest) = {
    val names = JEnumerationIterator[String](req.getParameterNames.asInstanceOf[java.util.Enumeration[String]])
    Some(((Map.empty[String, Seq[String]] /: names) ((m, n) => 
        m + (n -> req.getParameterValues(n))
      )).withDefaultValue(Nil), req)
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

/** Extractor and util for common mime types */
object Mime {
  val types = Map(
    ".3gp"     -> "video/3gpp",
    ".a"       -> "application/octet-stream",
    ".ai"      -> "application/postscript",
    ".aif"     -> "audio/x-aiff",
    ".aiff"    -> "audio/x-aiff",
    ".asc"     -> "application/pgp-signature",
    ".asf"     -> "video/x-ms-asf",
    ".asm"     -> "text/x-asm",
    ".asx"     -> "video/x-ms-asf",
    ".atom"    -> "application/atom+xml",
    ".au"      -> "audio/basic",
    ".avi"     -> "video/x-msvideo",
    ".bat"     -> "application/x-msdownload",
    ".bin"     -> "application/octet-stream",
    ".bmp"     -> "image/bmp",
    ".bz2"     -> "application/x-bzip2",
    ".c"       -> "text/x-c",
    ".cab"     -> "application/vnd.ms-cab-compressed",
    ".cc"      -> "text/x-c",
    ".chm"     -> "application/vnd.ms-htmlhelp",
    ".class"   -> "application/octet-stream",
    ".com"     -> "application/x-msdownload",
    ".conf"    -> "text/plain",
    ".cpp"     -> "text/x-c",
    ".crt"     -> "application/x-x509-ca-cert",
    ".css"     -> "text/css",
    ".csv"     -> "text/csv",
    ".cxx"     -> "text/x-c",
    ".deb"     -> "application/x-debian-package",
    ".der"     -> "application/x-x509-ca-cert",
    ".diff"    -> "text/x-diff",
    ".djv"     -> "image/vnd.djvu",
    ".djvu"    -> "image/vnd.djvu",
    ".dll"     -> "application/x-msdownload",
    ".dmg"     -> "application/octet-stream",
    ".doc"     -> "application/msword",
    ".dot"     -> "application/msword",
    ".dtd"     -> "application/xml-dtd",
    ".dvi"     -> "application/x-dvi",
    ".ear"     -> "application/java-archive",
    ".eml"     -> "message/rfc822",
    ".eps"     -> "application/postscript",
    ".exe"     -> "application/x-msdownload",
    ".f"       -> "text/x-fortran",
    ".f77"     -> "text/x-fortran",
    ".f90"     -> "text/x-fortran",
    ".flv"     -> "video/x-flv",
    ".for"     -> "text/x-fortran",
    ".gem"     -> "application/octet-stream",
    ".gemspec" -> "text/x-script.ruby",
    ".gif"     -> "image/gif",
    ".gz"      -> "application/x-gzip",
    ".h"       -> "text/x-c",
    ".htc"     -> "text/x-component",
    ".hh"      -> "text/x-c",
    ".htm"     -> "text/html",
    ".html"    -> "text/html",
    ".ico"     -> "image/vnd.microsoft.icon",
    ".ics"     -> "text/calendar",
    ".ifb"     -> "text/calendar",
    ".iso"     -> "application/octet-stream",
    ".jar"     -> "application/java-archive",
    ".java"    -> "text/x-java-source",
    ".jnlp"    -> "application/x-java-jnlp-file",
    ".jpeg"    -> "image/jpeg",
    ".jpg"     -> "image/jpeg",
    ".js"      -> "application/javascript",
    ".json"    -> "application/json",
    ".log"     -> "text/plain",
    ".m3u"     -> "audio/x-mpegurl",
    ".m4v"     -> "video/mp4",
    ".man"     -> "text/troff",
    ".manifest"-> "text/cache-manifest",
    ".mathml"  -> "application/mathml+xml",
    ".mbox"    -> "application/mbox",
    ".mdoc"    -> "text/troff",
    ".me"      -> "text/troff",
    ".mid"     -> "audio/midi",
    ".midi"    -> "audio/midi",
    ".mime"    -> "message/rfc822",
    ".mml"     -> "application/mathml+xml",
    ".mng"     -> "video/x-mng",
    ".mov"     -> "video/quicktime",
    ".mp3"     -> "audio/mpeg",
    ".mp4"     -> "video/mp4",
    ".mp4v"    -> "video/mp4",
    ".mpeg"    -> "video/mpeg",
    ".mpg"     -> "video/mpeg",
    ".ms"      -> "text/troff",
    ".msi"     -> "application/x-msdownload",
    ".odp"     -> "application/vnd.oasis.opendocument.presentation",
    ".ods"     -> "application/vnd.oasis.opendocument.spreadsheet",
    ".odt"     -> "application/vnd.oasis.opendocument.text",
    ".ogg"     -> "application/ogg",
    ".ogv"     -> "video/ogg",
    ".p"       -> "text/x-pascal",
    ".pas"     -> "text/x-pascal",
    ".pbm"     -> "image/x-portable-bitmap",
    ".pdf"     -> "application/pdf",
    ".pem"     -> "application/x-x509-ca-cert",
    ".pgm"     -> "image/x-portable-graymap",
    ".pgp"     -> "application/pgp-encrypted",
    ".pkg"     -> "application/octet-stream",
    ".pl"      -> "text/x-script.perl",
    ".pm"      -> "text/x-script.perl-module",
    ".png"     -> "image/png",
    ".pnm"     -> "image/x-portable-anymap",
    ".ppm"     -> "image/x-portable-pixmap",
    ".pps"     -> "application/vnd.ms-powerpoint",
    ".ppt"     -> "application/vnd.ms-powerpoint",
    ".ps"      -> "application/postscript",
    ".psd"     -> "image/vnd.adobe.photoshop",
    ".py"      -> "text/x-script.python",
    ".qt"      -> "video/quicktime",
    ".ra"      -> "audio/x-pn-realaudio",
    ".rake"    -> "text/x-script.ruby",
    ".ram"     -> "audio/x-pn-realaudio",
    ".rar"     -> "application/x-rar-compressed",
    ".rb"      -> "text/x-script.ruby",
    ".rdf"     -> "application/rdf+xml",
    ".roff"    -> "text/troff",
    ".rpm"     -> "application/x-redhat-package-manager",
    ".rss"     -> "application/rss+xml",
    ".rtf"     -> "application/rtf",
    ".ru"      -> "text/x-script.ruby",
    ".s"       -> "text/x-asm",
    ".sgm"     -> "text/sgml",
    ".sgml"    -> "text/sgml",
    ".sh"      -> "application/x-sh",
    ".sig"     -> "application/pgp-signature",
    ".snd"     -> "audio/basic",
    ".so"      -> "application/octet-stream",
    ".svg"     -> "image/svg+xml",
    ".svgz"    -> "image/svg+xml",
    ".swf"     -> "application/x-shockwave-flash",
    ".t"       -> "text/troff",
    ".tar"     -> "application/x-tar",
    ".tbz"     -> "application/x-bzip-compressed-tar",
    ".tcl"     -> "application/x-tcl",
    ".tex"     -> "application/x-tex",
    ".texi"    -> "application/x-texinfo",
    ".texinfo" -> "application/x-texinfo",
    ".text"    -> "text/plain",
    ".tif"     -> "image/tiff",
    ".tiff"    -> "image/tiff",
    ".torrent" -> "application/x-bittorrent",
    ".tr"      -> "text/troff",
    ".txt"     -> "text/plain",
    ".vcf"     -> "text/x-vcard",
    ".vcs"     -> "text/x-vcalendar",
    ".vrml"    -> "model/vrml",
    ".war"     -> "application/java-archive",
    ".wav"     -> "audio/x-wav",
    ".webm"    -> "video/webm",
    ".wma"     -> "audio/x-ms-wma",
    ".wmv"     -> "video/x-ms-wmv",
    ".wmx"     -> "video/x-ms-wmx",
    ".wrl"     -> "model/vrml",
    ".wsdl"    -> "application/wsdl+xml",
    ".xbm"     -> "image/x-xbitmap",
    ".xhtml"   -> "application/xhtml+xml",
    ".xls"     -> "application/vnd.ms-excel",
    ".xml"     -> "application/xml",
    ".xpm"     -> "image/x-xpixmap",
    ".xsl"     -> "application/xml",
    ".xslt"    -> "application/xslt+xml",
    ".yaml"    -> "text/yaml",
    ".yml"     -> "text/yaml",
    ".zip"     -> "application/zip"
  )
  
  private val Ext = """([.]\w+)$""".r
  
  /** given a file name, extract the extention and match its content type */
  def unapply(name: String) = Ext.findFirstMatchIn(name) match {
    case Some(ext) => types.get(ext.subgroups(0))
    case _ => None
  }
}