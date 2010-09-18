package unfiltered.jetty

import javax.servlet.http.HttpServletRequest
import org.eclipse.jetty.server.{Server => JettyServer, Connector, Handler}
import org.eclipse.jetty.server.handler.{ContextHandlerCollection, ResourceHandler}
import org.eclipse.jetty.servlet.{FilterHolder, FilterMapping, ServletContextHandler}
import org.eclipse.jetty.server.bio.SocketConnector
import org.eclipse.jetty.util.resource.Resource

case class Http(port: Int) extends Server {
  val conn = new SocketConnector()
  conn.setPort(port)
  server.addConnector(conn)
}

trait ContextBuilder { 
  def current: ServletContextHandler
  def filter(filt: javax.servlet.Filter): this.type = {
    current.addFilter(new FilterHolder(filt), "/*", FilterMapping.DEFAULT)
    this
  }

  def resources(path: java.net.URL): this.type = {
    current.setBaseResource(Resource.newResource(path))
    this
  }

}

trait Server extends ContextBuilder {
  val server = new JettyServer()
  val handlers = new ContextHandlerCollection
  server.setHandler(handlers)
  
  private def contextHandler(path: String) = {
    val ctx = new ServletContextHandler(handlers, path, false, false)
    ctx.addServlet(classOf[org.eclipse.jetty.servlet.DefaultServlet], "/")
    handlers.addHandler(ctx)
    ctx
  }
  
  def context(path: String)(block: ContextBuilder => Unit) = {
    block(new ContextBuilder {
      val current = contextHandler(path)
    })
    Server.this
  }
  lazy val current = contextHandler("/")
  
  /** Calls run with a no-op afterStart */
  def run() {
    run { _ => () }
  }
  /** Starts the server, calls andThen, and joins the server's controlling thread. If the
   * current thread is not the main thread, e.g. if running in sbt, waits for input in a
   * loop and stops the server as soon as any key is pressed.  */
  def run(afterStart: this.type => Unit) {
    // enter wait loop if not in main thread, e.g. running inside sbt
    Thread.currentThread.getName match {
      case "main" => 
        server.setStopAtShutdown(true)
        server.start()
        afterStart(Server.this)
        server.join()
      case _ => 
        server.start()
        afterStart(Server.this)
        println("Embedded server running. Press any key to stop.")
        def doWait() {
          try { Thread.sleep(1000) } catch { case _: InterruptedException => () }
          if(System.in.available() <= 0)
            doWait()
        }
        doWait()
        stop()
    }
  }
  /** Starts server in the background */
  def start() = {
    server.start()
    Server.this
  }
  /** Stops server running in the background */
  def stop() = {
    server.stop()
    Server.this
  }
}
