package unfiltered.jetty

import unfiltered.util.{ HttpPortBindingShim, PlanServer, Port, RunnableServer }

import org.eclipse.jetty.server.{Server => JettyServer, Connector, Handler}
import org.eclipse.jetty.server.handler.{
  ContextHandlerCollection, ResourceHandler}
import org.eclipse.jetty.servlet.{
  FilterHolder, FilterMapping, ServletContextHandler, ServletHolder}
import org.eclipse.jetty.server.bio.SocketConnector
import org.eclipse.jetty.util.resource.Resource

import java.util.EnumSet
import java.util.concurrent.atomic.AtomicInteger
import javax.servlet.{ Filter, DispatcherType }

@deprecated("Use unfiltered.jetty.Server", since="0.8.1")
object Http {
  /** bind to the given port for any host */
  def apply(port: Int): Http = Http(port, "0.0.0.0")
  /** bind to a the loopback interface only */
  def local(port: Int) = Http(port, "127.0.0.1")
  /** bind to any available port on the loopback interface */
  def anylocal = local(Port.any)
}

@deprecated("Use unfiltered.jetty.Server", since="0.8.1")
case class Http(port: Int, host: String) extends JettyBase {
  type ServerBuilder = Http
  val url = "http://%s:%d/" format (host, port)
  val conn = new SocketConnector()
  conn.setPort(port)
  conn.setHost(host)
  underlying.addConnector(conn)
  def portBindings = HttpPortBindingShim(host, port) :: Nil
}

trait ContextBuilder {
  val counter: AtomicInteger
  def current: ServletContextHandler
  def filter(filt: Filter): this.type = {
    val holder = new FilterHolder(filt)
    holder.setName("Filter %s" format counter.incrementAndGet)
    current.addFilter(holder, "/*", EnumSet.of(DispatcherType.REQUEST))
    this
  }

  /** Sets a base resource path for this context, in which
   * Jetty checks for file resources when no filters have
   * served a response. The `path` URL may refer to a file
   * (see File#toURL) or a location on the classpath. */
  def resources(path: java.net.URL): this.type = {
    current.setBaseResource(Resource.newResource(path))
    this
  }
}

@deprecated("Use unfiltered.jetty.Server", since="0.8.1")
trait JettyBase
extends ContextBuilder
with PlanServer[Filter]
with RunnableServer { self =>
  type ServerBuilder >: self.type <: JettyBase

  val underlying = new JettyServer()
  val handlers = new ContextHandlerCollection
  val counter = new AtomicInteger
  val url: String
  def makePlan(plan: => Filter) = filter(plan)

  underlying.setHandler(handlers)

  private def contextHandler(path: String) = {
    val ctx = new ServletContextHandler(handlers, path, false, false)
    val holder = new ServletHolder(classOf[org.eclipse.jetty.servlet.DefaultServlet])
    holder.setName("Servlet %s" format counter.incrementAndGet)
    ctx.addServlet(holder, "/")
    handlers.addHandler(ctx)
    ctx
  }

  def context(path: String)(block: ContextBuilder => Unit) = {
    block(new ContextBuilder {
      val current = contextHandler(path)
      val counter = JettyBase.this.counter
    })
    JettyBase.this
  }
  lazy val current = contextHandler("/")

  /** Starts server in the background */
  def start() = {
    underlying.setStopAtShutdown(true)
    underlying.start()
    JettyBase.this
  }
  /** Stops server running in the background */
  def stop() = {
    underlying.stop()
    JettyBase.this
  }
  /** Destroys the Jetty server instance and frees its resources.
   * Call after stopping a server, if finished with the instance,
   * to help avoid PermGen errors in an ongoing JVM session. */
  def destroy() = {
    underlying.destroy()
    this
  }
  def join() = {
    underlying.join()
    this
  }
}
