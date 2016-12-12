package unfiltered.util

/** Unfiltered's base server trait, something plans can be added to */
trait Server { self =>
  // ServerBuilder is concretely defined in the final case clases,
  // and we can always return `this` for ServerBuilder
  type ServerBuilder >: self.type <: Server
}
trait PlanServer[T] extends Server { self =>
  type ServerBuilder >: self.type <: PlanServer[T]
  def plan(plan: T): ServerBuilder = makePlan(plan)
  def makePlan(plan: => T): ServerBuilder
}

/** Describes a server's host and port bindings. */
trait PortBindingInfo {
  def host: String
  def port: Int
  def scheme: String
  def url = s"$scheme://$host:$port"
}
trait HttpPortBinding {
  val scheme = "http"
}
trait HttpsPortBinding {
  val scheme = "https"
}
@deprecated("Info-only binding for older server builders", since="0.8.1")
case class HttpPortBindingShim(host: String, port: Int)
extends PortBindingInfo with HttpPortBinding

@deprecated("Info-only binding for older server builders", since="0.8.1")
case class HttpsPortBindingShim(host: String, port: Int)
extends PortBindingInfo with HttpsPortBinding

trait StartableServer extends Server {
  def start(): ServerBuilder
  def stop(): ServerBuilder
  def destroy(): ServerBuilder
  /** network interface/host name and port bound for a server */
  def portBindings:  Traversable[PortBindingInfo]
}
trait RunnableServer extends StartableServer { self =>
  /** Calls run with no afterStart or afterStop functions */
  def run(): Unit = {
    run { _ => () }
  }
  /** Starts the server then takes an action */
  def run(afterStart: ServerBuilder => Unit): Unit = {
    run(afterStart, { _ => () })
  }
  /** Starts the server, calls afterStart. then waits. The waiting behavior
   * depends on whether the current thread is "main"; if not "main" it
   * assumes this is an interactive session with sbt and waits for any input,
   * then calls stop(), afterStop(...), and finally destroy(). If the
   * current thread is "main", it waits indefinitely and performs stop()
   * and afterStop(...) in a shutdown hook.
   */
  def run(afterStart: ServerBuilder => Unit, afterStop: ServerBuilder => Unit): Unit = {
    Thread.currentThread.getName match {
      case "main" =>
        Runtime.getRuntime.addShutdownHook(new Thread {
          override def run(): Unit = {
            RunnableServer.this.stop()
            afterStop(RunnableServer.this)
          }
        })
        start()
        afterStart(RunnableServer.this)
        val lock = new AnyRef
        lock.synchronized { lock.wait() }
      case _ =>
        start()
        afterStart(RunnableServer.this)
        println("Embedded server listening at")
        for (binding <- portBindings)
          println(s"  ${binding.url}")
        println("Press any key to stop.")
        def doWait(): Unit = {
          try { Thread.sleep(1000) } catch { case _: InterruptedException => () }
          if(System.in.available() <= 0)
            doWait()
        }
        doWait()
        stop()
        afterStop(RunnableServer.this)
        destroy()
    }
  }
}
