package unfiltered.server

import org.eclipse.jetty.server.{Server => JettyServer, Connector, Handler}
import org.eclipse.jetty.server.handler.{HandlerCollection, ResourceHandler}
import org.eclipse.jetty.servlet.{FilterHolder, ServletContextHandler}
import org.eclipse.jetty.server.bio.SocketConnector
import org.eclipse.jetty.util.resource.Resource

case class Http(port: Int) extends Server {
  val conn = new SocketConnector()
  conn.setPort(port)
  server.addConnector(conn)
}

trait Server {
  val server = new JettyServer()
  val handlers = new HandlerCollection
  server.setHandler(handlers)
  
  def handler(block: HandlerCollection => Handler) = {
    handlers.addHandler(block(handlers))
    this
  }

  def filter(filt: javax.servlet.Filter) = handler { handlers =>
    val context = new ServletContextHandler(handlers, "/")
    context.addFilter(new FilterHolder(filt), "/*", 
      ServletContextHandler.NO_SESSIONS|ServletContextHandler.NO_SECURITY)
    context.addServlet(classOf[org.eclipse.jetty.servlet.DefaultServlet], "/")
    context
  }
  
  def resources(marker: java.net.URL) = handler { container =>
    val s = marker.toString
    val resource_handler = new ResourceHandler
    resource_handler.setBaseResource(Resource.newResource(s.toString.substring(0, s.lastIndexOf("/"))))
    resource_handler
  }
  
  def start() {
    // enter wait loop if not in main thread, e.g. running inside sbt
    Thread.currentThread.getName match {
      case "main" => 
        server.setStopAtShutdown(true)
        server.start()
        server.join()
      case _ => 
        server.start()
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
  /** programatic way to start server in the background */
  def daemonize() {
    server.start()
  }
    
  def stop() {
    server.stop() 
  }
}
