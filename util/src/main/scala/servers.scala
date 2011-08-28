package unfiltered.util

/** Unfiltered's base server trait, something plans can be added to */
trait Server[T] {
  def plan(plan: T): Server[T]
}
trait StartableServer {
  def start(): this.type
  def stop(): this.type
  def destroy(): this.type
  val port:  Int
}
trait RunnableServer extends StartableServer {
  /** Calls run with no afterStart or afterStop functions */
  def run() {
    run { _ => () }
  }
  /** Starts the server then takes an action */
  def run(afterStart: this.type => Unit) {
    run(afterStart, { _ => () })
  }
  /** Starts the server, calls afterStart. then waits. The waiting behavior
   * depends on whether the current thread is "main"; if not "main" it
   * assumes this is an interactive session with sbt and waits for any input,
   * then calls stop(), afterStop(...), and finally destroy(). If the
   * current thread is "main", it waits indefinitely and performs stop()
   * and afterStop(...) in a shutdown hook.
   */
  def run(afterStart: this.type => Unit, afterStop: this.type => Unit) {
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
