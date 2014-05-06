package unfiltered.netty

import unfiltered.netty.async.Plan
import unfiltered.netty.resources.{ FileSystemResource, Resolve, Resource }
import unfiltered.request.{ GET, HEAD, HttpRequest, IfModifiedSince, Path, & }
import unfiltered.response.{
  BadRequest, CacheControl, Connection, ContentLength, ContentType, Date,
  Expires, Forbidden, LastModified, NotFound, NotModified, Ok,
  Pass, PlainTextContent, ResponseFunction }

import io.netty.channel.{ ChannelFuture, ChannelFutureListener, DefaultFileRegion }
import io.netty.channel.ChannelHandler.Sharable
import io.netty.handler.codec.http.{
  LastHttpContent, HttpHeaders, HttpResponse }
import io.netty.handler.stream.{ ChunkedFile, ChunkedStream }
import io.netty.util.{ CharsetUtil, ReferenceCountUtil }
import java.io.{ File, FileNotFoundException, RandomAccessFile }
import java.net.{ URL, URLDecoder }
import java.text.SimpleDateFormat
import java.util.{ Calendar, GregorianCalendar }
import javax.activation.MimetypesFileTypeMap

import scala.util.control.Exception.allCatch

object Mimes {
  private lazy val types =
    new MimetypesFileTypeMap(getClass.getResourceAsStream("/mime.types"))
  def apply(path: String) = types.getContentType(path)
}

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
    if (passOnFail) Pass
    else req.underlying.respond(rf)

  def forbid = passOr(Forbidden)_

  def notFound = passOr(NotFound)_

  def badRequest = passOr(BadRequest ~> PlainTextContent)_

  def intent = {
    case Retrieval(Path(path)) & req => safe(path.drop(1)) match {
      case Some(rsrc) =>
        val ctx = req.underlying.context
        IfModifiedSince(req) match {
          case Some(since) if (since.getTime == rsrc.lastModified) =>
            // close immediately and do not include content-length header
            // http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
            ctx.write(
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
              var defaultHeaders = Ok ~> ContentLength(len.toString) ~>
                // note: bin/text/charset not included
                ContentType(Mimes(rsrc.path)) ~>
                Date(Dates.format(cal.getTime)) ~>
                CacheControl("private, max-age=%d" format cacheSeconds) ~>
                LastModified(Dates.format(rsrc.lastModified))

              cal.add(Calendar.SECOND, cacheSeconds)

              val headers =
                if (HttpHeaders.isKeepAlive(req.underlying.request))
                  defaultHeaders ~> Connection(HttpHeaders.Values.KEEP_ALIVE)
                else defaultHeaders
              // we are sending are using a partial response
              // because files are chunked.
              val writeHeaders = ctx.write(
                req.underlying.partialResponse(
                  headers ~> Expires(Dates.format(cal.getTime))))

              def lastly(future: ChannelFuture) = {
                // close channel if not keep alive
                if (!HttpHeaders.isKeepAlive(req.underlying.request)) {
                   future.addListener(ChannelFutureListener.CLOSE)
                }
                // be sure to adjust reference count
                future.addListener(new ChannelFutureListener {
                  def operationComplete(f: ChannelFuture) {
                    req.underlying.content.map { c =>
                      ReferenceCountUtil.release(c)
                    }
                  }
                })
              }

              if (GET.unapply(req).isDefined && ctx.channel.isOpen) {
                rsrc match {
                  case FileSystemResource(_) =>
                    val raf = new RandomAccessFile(rsrc.path, "r")
                    ctx.write(
                      if (req.isSecure)
                        new ChunkedFile(
                          raf, 0, len, 8192/*ChunkedStream.DEFAULT_CHUNK_SIZE*/)
                      else new DefaultFileRegion(raf.getChannel, 0, len))

                    lastly(ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT))
                  case other =>
                    ctx.writeAndFlush(new ChunkedStream(other.in))
                }
              } else lastly(writeHeaders) // HEAD request
            } catch {
              case e: FileNotFoundException =>
                notFound(req)
            }
        }
      case _ =>
        forbid(req)
    }
    case req =>
      badRequest(req)
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
    (allCatch.opt { URLDecoder.decode(uri, CharsetUtil.UTF_8.name()) }
     orElse {
       allCatch.opt { URLDecoder.decode(uri, CharsetUtil.ISO_8859_1.name()) }
     })
}
