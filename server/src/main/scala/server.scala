package unfiltered.server

import org.eclipse.jetty.server.{Server => JettyServer, Connector, Handler}
import org.eclipse.jetty.server.handler.{ContextHandler, HandlerCollection, ResourceHandler}
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

  /** Attach a filter at the root context */
  val filter = filterAt("/")_
  /** Attach a filter at contextPath  */
  def filterAt(contextPath: String)(filt: javax.servlet.Filter) = at(contextPath) { context =>
    context.addFilter(new FilterHolder(filt), "/*", 
      ServletContextHandler.NO_SESSIONS|ServletContextHandler.NO_SECURITY)
    context
  }
  /** create a new resource handler at marker */  
  def resources(marker: java.net.URL) = handler { container =>
    val s = marker.toString
    val resource_handler = new ResourceHandler
    resource_handler.setBaseResource(Resource.newResource(s.toString.substring(0, s.lastIndexOf("/"))))
    resource_handler
  }
  
  /** Runs the server and joins its controlling thread. If the current thread is not the main thread, 
      e.g. if running in sbt, waits for input in a loop and stops the server as soon as any key is pressed.  */
  def run() {
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
  /** Starts server in the background */
  def start() {
    server.start()
  }
  /** Stops server running in the background */
  def stop() {
    server.stop() 
  }
  /** get or create a new context for a given contextPath  */
  private def at(contextPath: String)(f: ServletContextHandler => ServletContextHandler) =
    handler { handlers =>
      def newContext = {
        val context = new ServletContextHandler(handlers, contextPath)
        context.addServlet(classOf[org.eclipse.jetty.servlet.DefaultServlet], "/")
        context
      }
      f(handlers.getHandlers match {
        case null => newContext
        case arr => arr.toList.filter(_.isInstanceOf[ServletContextHandler])
                              .filter(_.asInstanceOf[ServletContextHandler].getContextPath == contextPath) match {
          case c :: _ => c.asInstanceOf[ServletContextHandler]
          case _ => newContext
        }
      })
    }
}