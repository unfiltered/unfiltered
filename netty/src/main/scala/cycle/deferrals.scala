package unfiltered.netty.cycle

import java.util.concurrent.{ExecutorService,Executors}

/** Evaluates the intent in an unbonuded CachedThreadPool
 * executor. For a production environment with known
 * memory constraints, it is better to extend
 * `DeferralExecutor with DeferredIntent` and supply
 * a configured MemoryAwareThreadPoolExecutor.*/
trait ThreadPool extends DeferralExecutor with DeferredIntent {
  self: Plan =>
  def underlying = ThreadPool.executor
}

object ThreadPool {
  lazy val executor = Executors.newCachedThreadPool()
}

/** Evaluates the intent and its response function on
 * an I/O worker thread. This is only appopriate if the
 * intent is fully CPU-bound. If any thread-blocking
 * I/O is required, use deferred execution.*/
trait SynchronousExecution { self: Plan =>
  def executeIntent(thunk: => Unit) { thunk }
  def executeResponse(thunk: => Unit) { thunk }
  def shutdown() { }
}

trait Deferral { self: Plan =>
  def defer(f: => Unit)
}

/** Defers all processing of the intent to a Deferral mechanism. */
trait DeferredIntent { self: Plan with Deferral =>
  def executeIntent(thunk: => Unit) { defer { thunk } }
  def executeResponse(thunk: => Unit) { thunk }
}

/** Defers evaluation of the given block until the response function
 * is applied. Useful with a DeferredResponse plan, not needed
 * for a DeferredIntent plan. */
object Defer {
  import unfiltered.response.{HttpResponse,ResponseFunction,Responder}
  def apply[A](rf: => ResponseFunction[A]) = new Responder[A] {
    def respond(res: HttpResponse[A]) { rf(res) }
  }
}

/** Defers application of the intent's response function
 * to a Deferral mechanism. This allows the intent to inspect
 * the request and potentially return Pass on the worker thread,
 * only deferring if it is to produce a response. The `Defer`
 * object should be used to ensure that blocking operations
 * are not performed in the evaluation of the intent function.*/
trait DeferredResponse { self: Plan with Deferral =>
  def executeIntent(thunk: => Unit) { thunk }
  def executeResponse(thunk: => Unit) { defer { thunk } }
}

/** Uses an ExecutorService to perform deferred tasks. */
trait DeferralExecutor extends Deferral { self: Plan =>
  def underlying: ExecutorService
  def shutdown() { underlying.shutdown() }
  def defer(f: => Unit) {
    underlying.execute(new Runnable {
      def run { f }
    })
  }
}
