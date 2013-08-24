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

Netty handlers are invoked on an I/O worker thread. In some scenarios
(including this one, actually) it would be just fine to prepare a
response on this thread. If the work is CPU-bound (does not perform
blocking operations), you can mix in the `cycle.SynchronousExecution`
trait instead, for no-overhead execution.

But a typical web application does need to access a database or
*something*, and the typical way to do this is with a blocking
call. That's why `Planify` uses the `cycle.ThreadPool` trait, which
defers application of the intent partial function to a cached thread
pool.

### Deference has its Memory Limits

Unfortunately, that's not the end of the story with deferred
execution. Applications also need to worry about running out of
memory. If you defer request handling jobs to an executor queue as
fast as Netty can accept requests, any heap limit can be exceeded with
a severe enough usage spike.

To avoid that kind of failure, you should equip your plans with an
executor that consumes a limited amount of memory and blocks on
incoming requests when that limit is reached. If you've defined a
simple local base plan (like the `MyPlan` above), you can customize it
later (ideally, before your server throws an out of memory exception)
with with a memory-aware thread pool executor.

```scala
trait MyPlan extends cycle.Plan with
cycle.DeferralExecutor with cycle.DeferredIntent with
ServerErrorResponse {
  def underlying = MyExecutor.underlying
}
object MyExecutor {
  import org.jboss.netty.handler.execution._
  lazy val underlying = new MemoryAwareThreadPoolExecutor(
    16, 65536, 1048576)
}
```

### Expecting Exceptions

The `ServerErrorResponse` trait also implements behavior that your
application will likely need to customize, sooner or later. Instead of
mixing in that trait, you can implement `onException` directly in your
base plan. For a starting point see the source for the
[provided exception handler][onexc], which logs the stack trace to
stdout and serves a very terse error response. Normally an application
will hook into its own logger and serve a custom error page or
redirect.

[onexc]: https://github.com/unfiltered/Unfiltered/blob/master/netty/src/main/scala/exceptions.scala#L15
