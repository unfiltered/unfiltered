package unfiltered.netty

import org.jboss.netty.channel._
import java.nio.charset.Charset

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

trait ExceptionHandling { self: SimpleChannelUpstreamHandler/*SimpleChannelHandler*/ =>
  import org.jboss.netty.handler.codec.http.HttpVersion._
  import org.jboss.netty.handler.codec.http.HttpResponseStatus._
  import org.jboss.netty.handler.codec.http.{HttpResponse => NHttpResponse, DefaultHttpResponse}
  import org.jboss.netty.handler.codec.frame.TooLongFrameException
  import java.nio.channels.ClosedChannelException
  import unfiltered.response._

    /** Binds a Netty HttpResponse res to Unfiltered's HttpResponse to apply any
   * response function to it. */
  private def response[T <: NHttpResponse](res: T)(rf: ResponseFunction[T]) =
    rf(new ResponseBinding(res)).underlying

  /** @return a new Netty DefaultHttpResponse bound to an Unfiltered HttpResponse */
  private val defaultResponse = response(new DefaultHttpResponse(HTTP_1_1, BAD_REQUEST))_

  override def exceptionCaught(ctx: ChannelHandlerContext, msg: ExceptionEvent): Unit =
    (msg.getCause, msg.getChannel) match {
      case (e: ClosedChannelException, _) =>
        error("Exception thrown while writing to a closed channel: %s" format e.getMessage)
      case (e: TooLongFrameException, ch) =>
        if(ch.isConnected) {
          ch.write(defaultResponse(BadRequest ~> PlainTextContent))
            .addListener(ChannelFutureListener.CLOSE)
        }
      case (e, ch) =>
        if(ch.isConnected) {
          ch.write(defaultResponse(InternalServerError ~> PlainTextContent))
            .addListener(ChannelFutureListener.CLOSE)
        }
    }
}

/** Extracts HttpRequest if a side effect is not implied */
object Idempotent {
  import unfiltered.request.{HttpRequest, GET, HEAD}
  def unapply[T](r: HttpRequest[T]) = {
    if(GET :: HEAD :: Nil map(_.unapply(r)) filter(_.isDefined) isEmpty) None
    else Some(r)
  }
}

object Resources {
  val utf8 = Charset.forName("UTF-8")
  val iso99591 = Charset.forName("ISO-8859-1")
}

/** Serves static resources.
 *  adaptered from Netty's example code HttpStaticFileServerHandler
 *  The behavior for dirIndexes (listing files under a directory) is not yet implemented and may be removed
 */
case class Resources(base: java.net.URL, cacheSeconds: Int = 60, dirIndexes: Boolean = false, passOnFail: Boolean = false)
  extends unfiltered.netty.channel.Plan with ExceptionHandling {
  import Resources._

  import unfiltered.request._
  import unfiltered.response._

  import scala.util.control.Exception.allCatch

  import java.util.{Calendar, GregorianCalendar}
  import java.io.{File, FileNotFoundException, RandomAccessFile}
  import java.net.URLDecoder

  import org.jboss.netty.channel.{DefaultFileRegion, ChannelFuture, ChannelFutureListener, ChannelFutureProgressListener}
  import org.jboss.netty.handler.codec.http.{HttpHeaders, HttpResponse => NHttpResponse}
  import org.jboss.netty.handler.stream.ChunkedFile
  import org.jboss.netty.buffer.ChannelBuffers

  import java.nio.channels.ClosedChannelException

  // todo: why doesn't type variance work here?
  // Returning Pass here will tell unfiltered to send the request upstream, otherwise
  // this method handles the request itself
  def passOr[T <: NHttpResponse](rf: => ResponseFunction[NHttpResponse])(req: HttpRequest[ReceivedMessage]) =
    if(passOnFail) Pass else req.underlying.respond(rf)

  def forbid = passOr(Forbidden)_

  def notFound = passOr(NotFound)_

  def badRequest = passOr(BadRequest ~> PlainTextContent)_

  def intent = {
    case Idempotent(Path(path)) & req => accessible(path.drop(1)) match {
      case Some(file) =>
        IfModifiedSince(req) match {
          case Some(since) if(since.getTime == file.lastModified) =>
            // close immediately and do not include a content-length header
            // http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
            req.underlying.event.getChannel.write(req.underlying.defaultResponse(
              NotModified ~> Date(Dates.format(new GregorianCalendar().getTime))
            )).addListener(ChannelFutureListener.CLOSE)
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
              val writeHeaders = chan.write(req.underlying.defaultResponse(heads ~> Expires(Dates.format(cal.getTime))))

              def lastly(future: ChannelFuture) =
                if(!HttpHeaders.isKeepAlive(req.underlying.request)) {
                   future.addListener(ChannelFutureListener.CLOSE)
                }

              // TODO. what to do if connection is reset by peer after writing the heads
              // but before writing the body?
              if(GET.unapply(req) isDefined) {
                if(req.isSecure) chan.write(new ChunkedFile(raf, 0, len, 8192))
                else {
                  // using zero-copy
                  val region = new DefaultFileRegion(raf.getChannel, 0, len)
                  val writeFile = chan.write(region)
                  writeFile.addListener(new ChannelFutureListener {
                    def operationComplete(f: ChannelFuture) = region.releaseExternalResources
                  })
                  lastly(writeFile)
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

  /** Converts a raw uri to a safe system file. Attempts to prevent
   *  security holes where resources are accessed with .. paths
   *  potentially outside of the root of the web app
   */
  private def accessible(uri: String) =
    (allCatch.opt { URLDecoder.decode(uri, utf8.name()) }
    orElse {
      allCatch.opt { URLDecoder.decode(uri, iso99591.name()) }
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
