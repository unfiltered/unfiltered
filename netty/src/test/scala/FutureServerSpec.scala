package unfiltered.netty

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory}

import org.specs2.mutable.Specification
import unfiltered.response.{Pass, ResponseString}
import unfiltered.request.{GET, Path => UFPath}

import scala.concurrent.{ExecutionContext, Future}

class FutureServerSpec extends Specification with org.specs2.matcher.ThrownMessages with unfiltered.specs2.netty.Served {

  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2, NamedDaemonTF.server))

  def setup = _.plan(future.Planify {
    case GET(UFPath("/ping")) =>
      Future.successful(ResponseString("pong"))

    case GET(UFPath("/future-ping")) =>
      Future { ResponseString(http(host / "ping").as_string) }

    case GET(UFPath("/pass")) =>
      Future.successful(Pass)
  })

  "A Server" should {
    "pass upstream on Pass, respond in last handler" in {
      skip("How to pass asynchronously?")
      http(host / "pass").as_string must_== "pass"
    }
    "respond with future results" in {
      http(host / "future-ping").as_string must_== "pong"
    }
  }
}

class NamedDaemonTF(base: String) extends ThreadFactory {
  private val counter = new AtomicInteger(0)
  private val delegate = Executors.defaultThreadFactory()

  override def newThread(r: Runnable): Thread = {
    val t = delegate.newThread(r)
    t.setDaemon(true)
    t.setName(s"${base}-${counter.incrementAndGet()}")
    t
  }
}

object NamedDaemonTF {
  val server = new NamedDaemonTF("server")
}
