package unfiltered.netty

import org.jboss.netty.channel.SimpleChannelUpstreamHandler

object Mimes {
  import javax.activation.MimetypesFileTypeMap

  lazy val underlying = new MimetypesFileTypeMap(getClass().getResourceAsStream("/mime.types"))
  def apply(path: String) = underlying.getContentType(path)
}

object Dates {
  import java.text.SimpleDateFormat
  import java.util.{Date, Locale, TimeZone}

  val HttpDateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
  val HttpDateGMTTimezone = "GMT"
  def format(d: Long): String =
    new SimpleDateFormat(HttpDateFormat, Locale.US) {
      setTimeZone(TimeZone.getTimeZone(HttpDateGMTTimezone))
    }.format(d)
  def format(d: Date): String = format(d.getTime)
}

/** Serves static resources.
 *  adaptered from Netty's example code HttpStaticFileServerHandler
 *  The behavior for dirIndexes (listing files under a directory) is not yet implemented and may be removed
 */
case class Resources(base: java.net.URL, cacheSeconds: Int = 60, dirIndexes: Boolean = false, passOnFail: Boolean = false)
  extends unfiltered.netty.channel.Plan {

  import unfiltered.request._
  import unfiltered.response._

  import scala.util.control.Exception.allCatch

  import java.util.{Calendar, GregorianCalendar}
  import java.io.{File, FileNotFoundException, RandomAccessFile}
  import java.net.URLDecoder

  import org.jboss.netty.channel.{DefaultFileRegion, ChannelFuture, ChannelFutureListener, ChannelFutureProgressListener}
  import org.jboss.netty.handler.codec.http.{HttpHeaders, HttpResponse => NHttpResponse}
  import org.jboss.netty.handler.stream.ChunkedFile

  // todo: why doesn't type variance work here?
  // Returning Pass here will tell unfiltered to send the request upstream, otherwise
  // this method handles the request itself
  def passOr[T <: NHttpResponse](rf: => ResponseFunction[NHttpResponse])(req: HttpRequest[ReceivedMessage]) =
    if(passOnFail) Pass else req.underlying.respond(rf)

  def forbid = passOr(Forbidden)_

  def notFound = passOr(NotFound)_

  def badRequest = passOr(BadRequest ~> PlainTextContent)_

  def intent = {
    case GET(Path(path)) & req => accessible(path.drop(1)) match {
      case Some(file) =>
        IfModifiedSince(req) match {
          case Some(since) if(since.getTime <= file.lastModified) =>
            req.underlying.respond(
              NotModified ~> Date(Dates.format(new GregorianCalendar().getTime))
            )
          case _ =>
            if(file.isHidden || !file.exists) notFound(req)
            else if(!file.isFile) forbid(req)
            else try {
              val raf = new RandomAccessFile(file, "r")
              val len = raf.length
              val cal = new GregorianCalendar()
              var heads = Ok ~> ContentLength(len.toString) ~>
                ContentType(Mimes(file.getPath)) ~> // note: bin/text/charset not included
                Date(Dates.format(cal.getTime)) ~>
                CacheControl("private, max-age=%d" format cacheSeconds) ~>
                LastModified(Dates.format(file.lastModified))

              cal.add(Calendar.SECOND, cacheSeconds)

              val chan = req.underlying.event.getChannel
              chan.write(req.underlying.defaultResponse(heads ~> Expires(Dates.format(cal.getTime))))

              val writeFile =
                if(!req.isSecure) chan.write(new ChunkedFile(raf, 0, len, 8192))
                else {
                  val reg = new DefaultFileRegion(raf.getChannel, 0, len)
                  val f = chan.write(reg)
                  f.addListener(new ChannelFutureProgressListener {
                    def operationComplete(f: ChannelFuture) = reg.releaseExternalResources
                    def operationProgressed(f: ChannelFuture, amt: Long, cur: Long, total: Long) = {}
                  })
                  f
                }
              if(!HttpHeaders.isKeepAlive(req.underlying.request)) writeFile.addListener(ChannelFutureListener.CLOSE)
            } catch {
              case e: FileNotFoundException => notFound(req)
            }
        }
      case _ => forbid(req)
    }
    case req => badRequest(req)
  }

  /** Converts a raw uri to a safe system file. Attempts to prevent
   *  security holes where resources are accessed with .. paths
   *  potentially outside of the root of the web app
   */
  private def accessible(uri: String) =
    (allCatch.opt {
      URLDecoder.decode(uri, "UTF-8")
    } orElse {
      allCatch.opt { URLDecoder.decode(uri, "ISO-8859-1") }
    }) match {
      case Some(decoded) =>
        decoded.replace('/', File.separatorChar) match {
          case fpath
            if(fpath.contains(File.separator + ".") ||
               fpath.contains("." + File.separator) ||
               fpath.startsWith(".") ||
               fpath.endsWith(".")) => None
          case fpath =>
            Some(new File(new java.net.URL(base, fpath).getFile))
        }
      case _ => None
    }
}
