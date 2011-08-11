package unfiltered.netty.cycle

import java.util.concurrent.{ExecutorService,Executors}

trait CachedThreadPool extends JUCExecutor { self: Plan =>
  lazy val underlying = Executors.newCachedThreadPool()
}

trait SynchronousExecution { self: Plan =>
  def execute(f: => Unit) { f }
  def shutdown() { }
}

trait JUCExecutor { self: Plan =>
  def underlying: ExecutorService
  def execute(f: => Unit) {
    underlying.execute(new Runnable {
      def run { f }
    })
  }
  def shutdown() { underlying.shutdown }
}
