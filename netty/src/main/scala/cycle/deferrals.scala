package unfiltered.netty.cycle

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** Evaluates the intent in an unbounded CachedThreadPool
 * executor. For a production environment with known
 * memory constraints, it is better to extend
 * `DeferralExecutor with DeferredIntent` and supply
 * a configured MemoryAwareThreadPoolExecutor.*/
trait ThreadPool extends DeferralExecutor with DeferredIntent {
  val underlying = ThreadPool.executor
}

object ThreadPool {
  def executor = Executors.newCachedThreadPool()
}

/** Evaluates the intent and its response function on
 * an I/O worker thread. This is only appropriate if the
 * intent is fully CPU-bound. If any thread-blocking
 * I/O is required, use deferred execution.*/
trait SynchronousExecution {
  def executeIntent(thunk: => Unit): Unit = { thunk }
  def executeResponse(thunk: => Unit): Unit = { thunk }
  def shutdown(): Unit = {}
}

trait Deferral {
  def defer(f: => Unit): Unit
}

/** Defers all processing of the intent to a Deferral mechanism. */
trait DeferredIntent { self: Deferral =>
  def executeIntent(thunk: => Unit): Unit = { defer { thunk } }
  def executeResponse(thunk: => Unit): Unit = { thunk }
}

/** Defers application of the intent's response function
 * to a Deferral mechanism. This allows the intent to inspect
 * the request and potentially return Pass on the worker thread,
 * only deferring if it is to produce a response. The `Defer`
 * object should be used to ensure that blocking operations
 * are not performed in the evaluation of the intent function.*/
trait DeferredResponse { self: Deferral =>
  def executeIntent(thunk: => Unit): Unit = { thunk }
  def executeResponse(thunk: => Unit): Unit = { defer { thunk } }
}

/** Uses an ExecutorService to perform deferred tasks. */
trait DeferralExecutor extends Deferral {
  def underlying: ExecutorService
  def shutdown(): Unit = { underlying.shutdown() }
  def defer(f: => Unit): Unit = {
    try {
      underlying.execute(new Runnable {
        def run: Unit = { f }
      })
    } catch {
      /** Our underlying executor has already shutdown or has already completed all of its tasks following shutdown */
      case e: RejectedExecutionException if underlying.isShutdown || underlying.isTerminated => // no-op
    }
  }
}
