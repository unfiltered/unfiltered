package unfiltered.netty

import org.jboss.netty.channel._
import java.nio.charset.Charset

import unfiltered.netty.resources.Resolve

object Mimes {
  import javax.activation.MimetypesFileTypeMap

  lazy val underlying =
    new MimetypesFileTypeMap(getClass.getResourceAsStream("/mime.types"))
  def apply(path: String) = underlying.getContentType(path)
}

object Dates {
  import java.text.SimpleDateFormat
  import java.util.{ Date, Locale, TimeZone }

  val HttpDateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
  val HttpDateGMTTimezone = "GMT"
  def format(d: Long): String =
    new SimpleDateFormat(HttpDateFormat, Locale.US) {
      setTimeZone(TimeZone.getTimeZone(HttpDateGMTTimezone))
    }.format(d)
  def format(d: Date): String = format(d.getTime)
}

/** Extracts HttpRequest if a retrieval method */
object Retrieval {
  import unfiltered.request.{HttpRequest, GET, HEAD}
  def unapply[T](r: HttpRequest[T]) =
    GET.unapply(r).orElse { HEAD.unapply(r) }
}

object Resources {
  val utf8 = Charset.forName("UTF-8")
  val iso88591 = Charset.forName("ISO-8859-1")
}

/** Serves static resources.
 *  Adapted from Netty's example HttpStaticFileServerHandler
 */
case class Resources(base: java.net.URL,
                     cacheSeconds: Int = 60,
                     passOnFail: Boolean = true)
  extends unfiltered.netty.async.Plan with ServerErrorResponse {
  import Resources._
  import resources._
  import unfiltered.request._
  import unfiltered.response._
  import java.io.{ File, FileNotFoundException, RandomAccessFile }
  import java.net.{ URL, URLDecoder }
  import java.util.{ Calendar, GregorianCalendar }
  import org.jboss.netty.channel.{
    ChannelFuture, ChannelFutureListener, DefaultFileRegion }
  import org.jboss.netty.handler.codec.http.{
    HttpHeaders, HttpResponse => NHttpResponse }
  import org.jboss.netty.handler.stream.{ ChunkedFile, ChunkedStream }
  import scala.util.control.Exception.allCatch

  // Returning Pass here will send the request upstream, otherwise
  // this method handles the request itself
  def passOr[T <: NHttpResponse](rf: => ResponseFunction[NHttpResponse])
                                (req: HttpRequest[ReceivedMessage]) =
    if (passOnFail) Pass else req.underlying.respond(rf)

  def forbid = passOr(Forbidden)_

  def notFound = passOr(NotFound)_

  def badRequest = passOr(BadRequest ~> PlainTextContent)_

  def intent = {
    case Retrieval(Path(path)) & req => safe(path.drop(1)) match {
      case Some(rsrc) =>
        IfModifiedSince(req) match {
          case Some(since) if (since.getTime == rsrc.lastModified) =>
            // close immediately and do not include content-length header
            // http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
            req.underlying.event.getChannel.write(
              req.underlying.defaultResponse(
                NotModified ~>
                Date(Dates.format(new GregorianCalendar().getTime))
            )).addListener(ChannelFutureListener.CLOSE)
          case _ =>
            if (!rsrc.exists || rsrc.hidden) notFound(req)
            else if (rsrc.directory) forbid(req)
            else try {
              val len = rsrc.size
              val cal = new GregorianCalendar()
              var heads = Ok ~> ContentLength(len.toString) ~>
                // note: bin/text/charset not included
                ContentType(Mimes(rsrc.path)) ~>
                Date(Dates.format(cal.getTime)) ~>
                CacheControl("private, max-age=%d" format cacheSeconds) ~>
                LastModified(Dates.format(rsrc.lastModified))

              cal.add(Calendar.SECOND, cacheSeconds)

              val chan = req.underlying.event.getChannel
              val writeHeaders = chan.write(
                req.underlying.defaultResponse(
                  heads ~> Expires(Dates.format(cal.getTime))))

              def lastly(future: ChannelFuture) =
                if(!HttpHeaders.isKeepAlive(req.underlying.request)) {
                   future.addListener(ChannelFutureListener.CLOSE)
                }

              if(GET.unapply(req).isDefined && chan.isOpen) {
                rsrc match {
                  case FileSystemResource(_) =>
                    val raf = new RandomAccessFile(rsrc.path, "r")
                  if(req.isSecure)
                    chan.write(new ChunkedFile(
                      raf, 0, len, 8192/*ChunkedStream.DEFAULT_CHUNK_SIZE*/))
                  else {
                    // using zero-copy
                    val region =
                      new DefaultFileRegion(raf.getChannel, 0, len)
                    val writeFile = chan.write(region)
                    writeFile.addListener(new ChannelFutureListener {
                      def operationComplete(f: ChannelFuture) =
                        region.releaseExternalResources
                    })
                    lastly(writeFile)
                  }
                  case other =>
                    chan.write(new ChunkedStream(other.in))
                }
              } else lastly(writeHeaders)
            } catch {
              case e: FileNotFoundException => notFound(req)
            }
        }
      case _ => forbid(req)
    }
    case req => badRequest(req)
  }

  /** Converts a raw uri to a safe resource path. Attempts to prevent
   *  security holes where resources are accessed with .. paths
   *  potentially outside of the root of the web app
   */
  private def safe(uri: String): Option[Resource] =
    decode(uri) flatMap { decoded =>
      decoded.replace('/', File.separatorChar) match {
        case p
          if(p.contains(File.separator + ".") ||
             p.contains("." + File.separator) ||
             p.startsWith(".") ||
             p.endsWith(".")) => None
        case path =>
          Resolve(new URL(base, decoded))
      }
    }

   private def decode(uri: String) =
     (allCatch.opt { URLDecoder.decode(uri, utf8.name()) }
      orElse {
        allCatch.opt { URLDecoder.decode(uri, iso88591.name()) }
      })
}
