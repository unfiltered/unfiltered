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
trait StartableServer extends Server {
  def start(): ServerBuilder
  def stop(): ServerBuilder
  def destroy(): ServerBuilder
  val port:  Int
}
trait RunnableServer extends StartableServer { self =>
  /** Calls run with no afterStart or afterStop functions */
  def run() {
    run { _ => () }
  }
  /** Starts the server then takes an action */
  def run(afterStart: ServerBuilder => Unit) {
    run(afterStart, { _ => () })
  }
  /** Starts the server, calls afterStart. then waits. The waiting behavior
   * depends on whether the current thread is "main"; if not "main" it
   * assumes this is an interactive session with sbt and waits for any input,
   * then calls stop(), afterStop(...), and finally destroy(). If the
   * current thread is "main", it waits indefinitely and performs stop()
   * and afterStop(...) in a shutdown hook.
   */
  def run(afterStart: ServerBuilder => Unit, afterStop: ServerBuilder => Unit) {
    Thread.currentThread.getName match {
      case "main" =>
        Runtime.getRuntime.addShutdownHook(new Thread {
          override def run() {
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
        println("Embedded server running on port %d. Press any key to stop." format port)
        def doWait() {
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
