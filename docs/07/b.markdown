Execution and Exceptions
------------------------

That's pretty nifty how we started up a Netty server in a few lines of
code, just like with Jetty, right? But while `Planify` is really handy
for experimenting in the console, it makes a lot of assumptions that
are unlikely to hold for a live server.

### A Less Simple Plan

Let's see what the plan looks like with those defaults made explicit.

```scala
import unfiltered.netty._

trait MyPlan extends cycle.Plan with 
cycle.ThreadPool with ServerErrorResponse

Object Hello extends MyPlan {
  def intent = {
    case _ => ResponseString("hello world")
  }
}
```

The traits define methods needed by `cycle.Plan`. But what for?

### Deferred Execution

Netty handlers are engaged on an I/O worker thread. In some scenarios
(including this one, actually) it would be just fine to prepare a
response on this thread. If the work is CPU-bound (does not perform
blocking operations), you can mix in the `cycle.SynchronousExecution`
trait instead, for no-overhead execution.

But a typical web application does need to access a database or
*something*, and the typical way to do this is with a blocking
call. That's why `Planify` uses the `cycle.ThreadPool` trait, which
defers application of the intent partial function to a cached thread
pool.

Unfortunately, that's not the end of the story with deferred
execution. Applications also need to worry about running out of
memory; deferred request handling to an executor as fast as Netty can
accept requests is a would do that.

To avoid that scenario, you should equip your plans with an executor
that consumes a limited amount of memory and blocks on incoming
requests when that limit is reached. If you've defined a simple local
base plan (like the `MyPlan` above), you can customize it later
(ideally, before your server throws an out of memory exception) with
with a memory-aware thread pool executor.

```scala
trait MyPlan extends cycle.Plan with 
cycle.DeferralExecutor with cycle.DeferredIntent with 
ServerErrorResponse {
  def underlying = MyExecutor.underlying
}
object MyExecutor {
  import org.jboss.netty.handler.execution._
  import java.util.concurrent.Executors
  lazy val underlying = new MemoryAwareThreadPoolExecutor(
    16, 65536, 1048576)
}
```

### Expecting Exceptions

The `ServerErrorResponse` trait also implements behavior that your
application will likely need to customize, sooner or later. Instead of
mixing in that trait, you can implement `onException` directly in your
base plan. Below is the default exception handler, which merely logs
the stack trace to stdout and serves a very terse error response.

```scala
trait MyPlan extends cycle.Plan with 
cycle.DeferralExecutor with cycle.DeferredIntent {
  def underlying = MyExecutor.underlying
  def onException(ctx: ChannelHandlerContext, t: Throwable) {
    val ch = ctx.getChannel
    if (ch.isOpen) try {
      println("Exception caught handling request:")
      t.printStackTrace()
      val res = new DefaultHttpResponse(
        HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
      res.setContent(ChannelBuffers.copiedBuffer(
        "Internal Server Error".getBytes("utf-8")))
        ch.write(res).addListener(ChannelFutureListener.CLOSE)
    } catch {
      case _ => ch.close()
    }
  }
}
```
