package unfiltered.netty

import unfiltered.netty.async.Plan
import unfiltered.netty.resources.{ FileSystemResource, Resolve, Resource }
import unfiltered.request.{ GET, HEAD, HttpRequest, IfModifiedSince, Path, & }
import unfiltered.response.{
  BadRequest, CacheControl, ContentLength, ContentType, Date,
  Expires, Forbidden, LastModified, NotFound, NotModified, Ok,
  Pass, PlainTextContent, ResponseFunction }

import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler.Sharable
import io.netty.handler.codec.http.{
  LastHttpContent, HttpResponse }
import io.netty.handler.stream.ChunkedStream

import java.io.{ File, FileNotFoundException }
import java.net.{ URL, URLDecoder }
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.{ Calendar, GregorianCalendar }

import scala.util.control.Exception.allCatch

object Dates {
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
@Sharable
case class Resources(
  base: java.net.URL,
  cacheSeconds: Int = 60,
  passOnFail: Boolean = true)
  extends Plan with ServerErrorResponse {
  import Resources._

  // Returning Pass here will send the request upstream, otherwise
  // this method handles the request itself
  def passOr[T <: HttpResponse](
    rf: => ResponseFunction[HttpResponse])
    (req: HttpRequest[ReceivedMessage]) =
    if (passOnFail) Pass else req.underlying.respond(rf)

  def forbid = passOr(Forbidden)_

  def notFound = passOr(NotFound)_

  def badRequest = passOr(BadRequest ~> PlainTextContent)_
  
  def respondNotModified(msg: ReceivedMessage) {
    val now = Dates.format(new GregorianCalendar().getTime)
    /** close immediately and do not include content-length header
      * http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html */
    msg.context.write(msg.defaultResponse(NotModified ~> Date(now)))
      .addListener(ChannelFutureListener.CLOSE)
  }

  def intent = {
    case Retrieval(Path(path)) & req => safe(path.drop(1)) match {
      case Some(rsrc) =>
        val receivedMessage = req.underlying
        val ctx = receivedMessage.context
        IfModifiedSince(req) match {
          case Some(since) if (since.getTime == rsrc.lastModified) =>
            respondNotModified(receivedMessage)
          case _ =>
            if (!rsrc.exists || rsrc.hidden) notFound(req)
            else if (rsrc.directory) forbid(req)
            else try {
              val len = rsrc.size
              val cal = new GregorianCalendar()
              val baseHeads = Ok ~> 
                // note: bin/text/charset not included
                Date(Dates.format(cal.getTime)) ~>
                CacheControl("private, max-age=%d" format cacheSeconds) ~>
                LastModified(Dates.format(rsrc.lastModified))

              cal.add(Calendar.SECOND, cacheSeconds)
              
              val customHeads = baseHeads ~> Expires(Dates.format(cal.getTime))

              val operationFuture = if (GET.unapply(req).isDefined && ctx.channel.isOpen) {
                rsrc match {
                  case FileSystemResource(file) =>
                    receivedMessage.sendFile(file)(customHeads)
                  case other =>
                    ctx.write(customHeads ~>
                      ContentLength(rsrc.size.toString) ~>
                      ContentType(Mimes(rsrc.path)))
                    ctx.write(new ChunkedStream(other.in))
                    val future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                    receivedMessage.finishResponse(future)
                }
              } else {
                ctx.write(customHeads ~>
                  ContentLength(rsrc.size.toString) ~>
                  ContentType(Mimes(rsrc.path)))
                val future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                receivedMessage.finishResponse(future)
              }
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
          if (p.contains(File.separator + ".") ||
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
