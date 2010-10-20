package unfiltered.util

trait RunnableServer {
  def start(): this.type
  def stop(): this.type
  def join(): this.type
  def destroy(): this.type
  /** Calls run with a no-op afterStart */
  def run() {
    run { _ => () }
  }
  /** Starts the server, calls andThen, and joins the server's controlling thread. If the
   * current thread is not the main thread, e.g. if running in sbt, waits for input in a
   * loop and stops the server as soon as any key is pressed. In either case the server
   * instance is destroyed after being stopped. */
  def run(afterStart: this.type => Unit) {
    // enter wait loop if not in main thread, e.g. running inside sbt
    Thread.currentThread.getName match {
      case "main" => 
        start()
        afterStart(RunnableServer.this)
        join()
        destroy()
      case _ => 
        start()
        afterStart(RunnableServer.this)
        println("Embedded server running. Press any key to stop.")
        def doWait() {
          try { Thread.sleep(1000) } catch { case _: InterruptedException => () }
          if(System.in.available() <= 0)
            doWait()
        }
        doWait()
        stop()
        destroy()
    }
  }
}
