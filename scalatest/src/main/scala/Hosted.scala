package unfiltered
package scalatest

import java.util.concurrent.{Executors, ThreadFactory}

import okhttp3._
import okio.ByteString
import org.scalatest.{BeforeAndAfterAll, Suite}
import unfiltered.request.Method

import scala.language.implicitConversions

trait Hosted extends BeforeAndAfterAll { self: Suite =>
  val port = unfiltered.util.Port.any
  val host = HttpUrl.parse(s"http://localhost:$port")

  private var dispatcher: Dispatcher = _

  override protected def beforeAll(): Unit = {
    dispatcher = new Dispatcher(Executors.newFixedThreadPool(10, new ThreadFactory {
      val counter = new java.util.concurrent.atomic.AtomicInteger()
      val defaultThreadFactory = Executors.defaultThreadFactory()
      override def newThread(r: Runnable) = {
        val thread = defaultThreadFactory.newThread(r)
        thread.setName("okhttp-dispatcher-" + counter.incrementAndGet())
        thread.setDaemon(true)
        thread
      }
    }))
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    if (dispatcher != null) {
      dispatcher.executorService().shutdown()
    }
    dispatcher = null
    super.afterAll()
  }

  def http(req: Request): Response = {
    val response = httpx(req)
    if (response.code == 200) {
      response
    } else {
      throw StatusCode(response.code)
    }
  }

  def httpx(req: Request): Response = {
    requestWithNewClient(req, new OkHttpClient.Builder())
  }

  def requestWithNewClient(req: Request, builder: OkHttpClient.Builder): Response = {
    import collection.JavaConverters._

    val client = builder.dispatcher(dispatcher).build()
    val res = client.newCall(req).execute()
    val headers = res.headers.toMultimap.asScala.mapValues(_.asScala.toList).toMap
    val transformed = Response(res.code(), headers, Option(res.body()).map{ body =>
      val bytes = body.bytes()
      ByteString.of(bytes, 0, bytes.length)
    })
    res.close()
    transformed
  }

  def req(url: HttpUrl): Request = new Request.Builder().url(url).build()

  case class StatusCode(code: Int) extends RuntimeException(code.toString)

  implicit class HttpUrlExtensions(url: HttpUrl) {
    def /(part: String) = url.newBuilder.addPathSegment(part).build

    def <<?(query: Map[String, String]): HttpUrl = {
      val b = url.newBuilder()
      query.foreach{case (k, v) => b.addQueryParameter(k, v)}
      b.build()
    }
  }

  implicit class RequestExtensions(request: Request) {
    def <:<(headers: Map[String, String]): Request = {
      val builder = request.newBuilder()
      headers.foreach{case (k, v) => builder.addHeader(k, v)}
      builder.build()
    }

    def <<?(query: Map[String, String]): Request = {
      val b = request.url().newBuilder()
      query.foreach{case (k, v) => b.addQueryParameter(k, v)}
      req(b.build())
    }

    def as_!(user: String, password: String) = {
      val builder = request.newBuilder()
      val basicAuth = Credentials.basic(user, password)
      builder.addHeader("Authorization", basicAuth)
      builder.build()
    }

    def <<(data: Map[String, String], method: Method = unfiltered.request.POST): Request = {
      val builder = request.newBuilder()
      val form = new FormBody.Builder()
      data.foreach{case (k,v) => form.add(k, v)}
      builder.method(method.method, form.build())
      builder.build()
    }

    def POST[A](data: A, mt: MediaType = MediaType.parse("application/octet-stream"))(implicit c: ByteStringToConverter[A]): Request = {
      val builder = request.newBuilder()
      builder.post(RequestBody.create(mt, c.toByteString(data))).build()
    }

    def POST[A](body: RequestBody): Request = {
      val builder = request.newBuilder()
      builder.post(body).build()
    }

    def <<*(name: String, file: java.io.File, mt: String) = {
      val mp = new MultipartBody.Builder().
        setType(MultipartBody.FORM).
        addFormDataPart(name, file.getName, RequestBody.create(MediaType.parse(mt), file)).build()
      POST(mp)
    }
  }


  case class Response(code: Int, headers: Map[String, List[String]], body: Option[ByteString]) {
    def as_string = body.map(_.utf8()).getOrElse("")
    def header(name: String): Option[List[String]] = headers.get(name.toLowerCase)
    def firstHeader(name: String): Option[String] = header(name).flatMap(_.headOption)
  }

  trait ByteStringToConverter[A] {
    def toByteString(a: A): ByteString
  }

  object ByteStringToConverter {
    implicit val StringByteStringConverter: ByteStringToConverter[String] =
      (a: String) => ByteString.encodeUtf8(a)

    implicit val IdentityStringConverter: ByteStringToConverter[ByteString] =
      (a: ByteString) => a

    implicit val bytesStringConverter: ByteStringToConverter[Array[Byte]] =
      (a: Array[Byte]) => ByteString.of(a, 0, a.length)
  }

  implicit def urlToGetRequest(url: HttpUrl): Request = req(url)
}
