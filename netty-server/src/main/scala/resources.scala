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
  LastHttpContent, HttpHeaderValues, HttpResponse, HttpUtil }
import io.netty.handler.stream.{ ChunkedFile, ChunkedStream }
import io.netty.util.CharsetUtil
import java.io.{ File, FileNotFoundException, RandomAccessFile }
import java.net.{ URL, URLDecoder }
import java.text.SimpleDateFormat
import java.util.{ Calendar, GregorianCalendar }
import java.util.concurrent.TimeUnit.MILLISECONDS
import javax.activation.MimetypesFileTypeMap

import scala.util.Try
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

        def lastly(future: ChannelFuture) = {
          // close channel if not keep alive
          if (!HttpUtil.isKeepAlive(req.underlying.request)) {
            future.addListener(ChannelFutureListener.CLOSE)
          }
          // be sure to adjust reference count
          future.addListener(req.underlying.releaser)
        }

        def setKeepAlive(headers: ResponseFunction[Any]) =
          if (HttpUtil.isKeepAlive(req.underlying.request))
            headers ~> Connection(HttpHeaderValues.KEEP_ALIVE.toString)
          else headers

        def seconds(t: Long) = MILLISECONDS.toSeconds(t)
        IfModifiedSince(req) match {
          // compare using 1 second resolution because that's the
          // precision of the time format in the IfModifiedSince header
          case Some(since) if seconds(since.getTime) == seconds(rsrc.lastModified) =>
            // do not include content-length header for HTTP status 304
            // https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
            val headers = setKeepAlive(
              NotModified ~>
              Date(Dates.format(new GregorianCalendar().getTime)))

            lastly(ctx.writeAndFlush(req.underlying.defaultResponse(headers)))

          case _ =>
            if (!rsrc.exists || rsrc.hidden) notFound(req)
            else if (rsrc.directory) forbid(req)
            else try {
              val len = rsrc.size
              val cal = new GregorianCalendar()
              val defaultHeaders = Ok ~> ContentLength(len.toString) ~>
                // note: bin/text/charset not included
                ContentType(Mimes(rsrc.path)) ~>
                Date(Dates.format(cal.getTime)) ~>
                CacheControl("private, max-age=%d" format cacheSeconds) ~>
                LastModified(Dates.format(rsrc.lastModified))

              cal.add(Calendar.SECOND, cacheSeconds)

              val headers = setKeepAlive(defaultHeaders)

              // we are sending are using a partial response
              // because files are chunked.
              val writeHeaders = ctx.write(
                req.underlying.partialResponse(
                  headers ~> Expires(Dates.format(cal.getTime))))

              if (GET.unapply(req).isDefined && ctx.channel.isOpen) {
                rsrc match {
                  case FileSystemResource(_) =>
                    val raf = new RandomAccessFile(rsrc.path, "r")
                    ctx.write(
                      if (req.isSecure)
                        new ChunkedFile(
                          raf, 0, len, 8192/*ChunkedStream.DEFAULT_CHUNK_SIZE*/)
                      else new DefaultFileRegion(raf.getChannel, 0, len))

                  case other =>
                    ctx.write(new ChunkedStream(other.in))
                }
                lastly(ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT))
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
   *  potentially outside of the root of the web app.
   *
   *  Further directory traversal requests should be assumed
   */
  private def safe(uri: String): Option[Resource] =
    decode(uri) flatMap { decoded =>
      decoded.replace('/', File.separatorChar) match {
        case p
          if p.contains(File.separator + ".") ||
              p.contains("." + File.separator) ||
              p.startsWith(".") ||
              p.startsWith("/") || // fixes any // requests which can expose a directory traversal problem
              p.endsWith(".") => None
        case path =>

          for {
            proposed <- Try(new URL(base, path)).toOption // check for MalformedURLExceptions
            _ <- Try(proposed.toURI).toOption // enforces URI$Parser to test for invalid characters i.e. /<  script > < / script>

            // create a path and check that the underlying path used is prefixed by the base path, this
            // ensures that there is no upward traversal of the base path as an extra security measure
            if proposed.toString.startsWith(base.toString)
            resolved <- Resolve(proposed)
          } yield resolved

      }
    }

  private def decode(uri: String) =
    (allCatch.opt { URLDecoder.decode(uri, CharsetUtil.UTF_8.name()) }
     orElse {
       allCatch.opt { URLDecoder.decode(uri, CharsetUtil.ISO_8859_1.name()) }
     })
}
