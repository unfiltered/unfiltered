package unfiltered.jetty

import javax.servlet.http.HttpServletRequest
import org.eclipse.jetty.server.{Server => JettyServer, Connector, Handler}
import org.eclipse.jetty.server.handler.{ContextHandlerCollection, ResourceHandler}
import org.eclipse.jetty.servlet.{FilterHolder, FilterMapping, ServletContextHandler, ServletHolder}
import org.eclipse.jetty.server.bio.SocketConnector
import org.eclipse.jetty.util.resource.Resource
import java.util.concurrent.atomic.AtomicInteger

case class Http(port: Int, host: String) extends Server {
  /** use the factory method */
  @deprecated def this(port: Int) = this(port, "0.0.0.0")
  val url = "http://%s:%d/" format (host, port)
  val conn = new SocketConnector()
  conn.setPort(port)
  conn.setHost(host)
  underlying.addConnector(conn)
}

case class Https(port: Int, host: String) extends Server with Ssl {
  val url = "http://%s:%d/" format (host, port)
  def sslPort = port
  sslConn.setHost(host)
}

/** Provides ssl support to a Server. This trait only requires a x509 keystore cert.
  * A keyStore, keyStorePassword are required and default to using the system property values
  * "jetty.ssl.keyStore" and "jetty.ssl.keyStorePassword" respectively.
  * For added truststore support, mix in the Trusted trait */
trait Ssl { self: Server =>
  import org.eclipse.jetty.server.ssl.SslSocketConnector
  
  def tryProperty(name: String) = System.getProperty(name) match {
    case null => error("required system property not set %s" format name)
    case prop => prop
  }
  
  def sslPort: Int
  val sslMaxIdleTime = 90000
  val sslHandshakeTimeout = 120000
  lazy val keyStore = tryProperty("jetty.ssl.keyStore")
  lazy val keyStorePassword = tryProperty("jetty.ssl.keyStorePassword")

  val sslConn = new SslSocketConnector()
  sslConn.setPort(sslPort)
  sslConn.setKeystore(keyStore)
  sslConn.setKeyPassword(keyStorePassword)
  sslConn.setMaxIdleTime(sslMaxIdleTime)
  sslConn.setHandshakeTimeout(sslHandshakeTimeout)
  underlying.addConnector(sslConn)
}

/** Provides truststore support to an Ssl supported Server 
  * A trustStore and trustStorePassword are required and default 
  * to the System property values "jetty.ssl.trustStore" and 
  * "jetty.ssl.trustStorePassword" respectively */
trait Trusted { self: Ssl =>
  lazy val trustStore = tryProperty("jetty.ssl.trustStore")
  lazy val trustStorePassword = tryProperty("jetty.ssl.trustStorePassword")
  sslConn.setTruststore(trustStore)
  sslConn.setTrustPassword(trustStorePassword)
}

trait ContextBuilder {
  val counter: AtomicInteger
  def current: ServletContextHandler
  def filter(filt: javax.servlet.Filter): this.type = {
    val holder = new FilterHolder(filt)
    holder.setName("Filter %s" format counter.incrementAndGet)
    current.addFilter(holder, "/*", FilterMapping.DEFAULT)
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

object Http {
  /** bind to the given port for any host */
  def apply(port: Int): Http = Http(port, "0.0.0.0")
  /** bind to a the loopback interface only */
  def local(port: Int) = Http(port, "127.0.0.1")
  /** bind to any available port on the loopback interface */
  def anylocal = local(unfiltered.util.Port.any)
}

object Https {
  /** bind to the given port for any host */
  def apply(port: Int): Https = Https(port, "0.0.0.0")
  /** bind to a the loopback interface only */
  def local(port: Int) = Https(port, "127.0.0.1")
  /** bind to any available port on the loopback interface */
  def anylocal = local(unfiltered.util.Port.any)
}

trait Server extends ContextBuilder with unfiltered.util.RunnableServer { self =>
  val underlying = new JettyServer()
  val handlers = new ContextHandlerCollection
  val counter = new AtomicInteger
  
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
      val counter = Server.this.counter
    })
    Server.this
  }
  lazy val current = contextHandler("/")
  
  /** Starts server in the background */
  def start() = {
    underlying.setStopAtShutdown(true)
    underlying.start()
    Server.this
  }
  /** Stops server running in the background */
  def stop() = {
    underlying.stop()
    Server.this
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
