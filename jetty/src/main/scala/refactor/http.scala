package unfiltered.jetty.refactor

import java.util.EnumSet
import javax.servlet.{ Filter, DispatcherType }

import org.eclipse.jetty.server.{Server => JettyServer, Connector, Handler, HandlerContainer}
import org.eclipse.jetty.server.handler.{ContextHandler, ContextHandlerCollection}
import org.eclipse.jetty.server.bio.SocketConnector
import org.eclipse.jetty.server.ssl.SslSocketConnector
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.servlet.{
  FilterHolder, FilterMapping, ServletContextHandler, ServletHolder}

/** Holds connector providers that listen to selected ports and interfaces.
  * ConnectorBuilder provides convenience methods for attaching connectors. */
case class Http(
  connectorProviders: List[ConnectorProvider],
  contextAdders: List[ContextAdder]
) extends unfiltered.util.RunnableServer
    with unfiltered.util.PlanServer[Filter]
    with ConnectorBuilder {
  type ServerBuilder = Http

  def attach(connector: ConnectorProvider) = copy(
    connectorProviders = connector :: connectorProviders
  )
  def attach(contextAdder: ContextAdder) = copy(
    contextAdders = contextAdder :: contextAdders
  )

  /** attaches to most recently added context */
  def attach(filterAdder: FilterAdder) = copy(
    contextAdders = contextAdders match {
      case head :: tail => head.attach(filterAdder) :: tail
      case _ => contextAdders
    })

  lazy val underlying = {
    val server = new JettyServer()
    for (provider <- connectorProviders)
      server.addConnector(provider.connector)
    val contextHandlers = new ContextHandlerCollection
    for (adder <- contextAdders)
      adder.addToParent(contextHandlers)
    server.setHandler(contextHandlers)
    server
  }
  def context(path: String)(block: ContextAdder => ContextAdder) = attach(
    block(DefaultServletContextAdder(path, Nil))
  )
  def filter(filter: => Filter) = attach(FilterAdder(BasicFilterHolder(filter)))
  def makePlan(plan: => Filter) = filter(plan)

  /** Supertypes assume we listen on one port only, so give them the first. */
  lazy val port = connectorProviders.headOption.map(_.port).getOrElse(0)
  /** Starts server in the background */
  def start() = {
    underlying.setStopAtShutdown(true)
    underlying.start()
    this
  }
  /** Stops server running in the background */
  def stop() = {
    underlying.stop()
    this
  }
  /** Destroys the Jetty server instance and frees its resources.
   * Call after stopping a server, if finished with the instance,
   * to help avoid PermGen errors in an ongoing JVM session. */
  def destroy() = {
    underlying.destroy()
    this
  }
}

/** Base object that used to construct Http instances.
  * ConnectorBuilder provides convenience methods for attaching
  * connectors. */
object Http extends ConnectorBuilder {
  def attach(connector: ConnectorProvider) =
    Http(connector :: Nil, DefaultServletContextAdder("/", Nil) :: Nil)
}

/** Convenience methods for attaching connector providers. */
trait ConnectorBuilder {
  val allInterfacesHost = "0.0.0.0"
  val localInterfaceHost = "127.0.0.1"
  val defaultHttpPort = 80
  val defaultHttpsPort = 443
  val defaultKeystorePathProperty = "jetty.ssl.keyStore"
  val defaultKeystorePasswordProperty = "jetty.ssl.keyStorePassword"

  def attach(connector: ConnectorProvider): Http

  def apply(port: Int = defaultHttpPort, host: String = allInterfacesHost) = attach(
    SocketConnectorProvider(port, host)
  )

  def local(port: Int): Http = attach(
    SocketConnectorProvider(port, localInterfaceHost)
  )

  def anylocal: Http = local(unfiltered.util.Port.any)

  def secure(
    port: Int = defaultHttpsPort,
    host: String = allInterfacesHost,
    keyStorePath: String,
    keyStorePassword: String
  ) = attach(
    SslSocketConnectorProvider(
      port,
      host,
      SslContextProvider.Simple(
        keyStorePath = keyStorePath,
        keyStorePassword = keyStorePassword
      )
    )
  )

  def secureSysProperties(
    port: Int = defaultHttpsPort,
    host: String = allInterfacesHost,
    keyStorePathProperty: String = defaultKeystorePathProperty,
    keyStorePasswordProperty: String = defaultKeystorePasswordProperty
  ) = attach(
    SslSocketConnectorProvider(
      port,
      host,
      PropertySslContextProvider(
        keyStorePathProperty = keyStorePathProperty,
        keyStorePasswordProperty = keyStorePasswordProperty
      )
    )
  )
}

trait ConnectorProvider {
  def connector: Connector
  def port: Int
  def host: String
}

case class SocketConnectorProvider (
  port: Int,
  host: String
) extends ConnectorProvider {
  lazy val connector = {
    val c = new SocketConnector
    c.setPort(port)
    c.setHost(host)
    c
  }
}

trait SslContextProvider {
  def keyStorePath: String
  def keyStorePassword: String
  lazy val sslContextFactory = {
    val factory = new SslContextFactory
    factory.setKeyStorePath(keyStorePath)
    factory.setKeyStorePassword(keyStorePassword)
    factory
  }
}

object SslContextProvider {
  case class Simple(
    keyStorePath: String,
    keyStorePassword: String
  ) extends SslContextProvider
}

case class PropertySslContextProvider(
  keyStorePathProperty: String,
  keyStorePasswordProperty: String
) extends SslContextProvider {
  private lazy val props = new sys.SystemProperties
  lazy val keyStorePath = props(keyStorePathProperty)
  lazy val keyStorePassword = props(keyStorePasswordProperty)
}

case class SslSocketConnectorProvider (
  port: Int,
  host: String,
  sslContextProvider: SslContextProvider
) extends ConnectorProvider {

  lazy val connector = {
    val c = new SslSocketConnector(sslContextProvider.sslContextFactory)
    c.setPort(port)
    c.setHost(host)
    c
  }
}

trait ContextAdder {
  def addToParent(parent: ContextHandlerCollection): Unit
  def attach(filter: FilterAdder): ContextAdder
  def filter(filter: Filter) = attach(FilterAdder(BasicFilterHolder(filter)))
}

case class DefaultServletContextAdder(
  path: String,
  filterAdders: List[FilterAdder]
) extends ContextAdder {
  def addToParent(parent: ContextHandlerCollection) = {
    val ctx = new ServletContextHandler(parent, path, false, false)
    val holder = new ServletHolder(classOf[org.eclipse.jetty.servlet.DefaultServlet])
    holder.setName(CountedName.Servlet.name)
    ctx.addServlet(holder, "/")

    for (filterAdder <- filterAdders)
      filterAdder.addToContext(ctx)
  }
  def attach(filter: FilterAdder) = copy(filterAdders = filter :: filterAdders)
}

object BasicFilterHolder {
  def apply(filter: Filter) = {
    val holder = new FilterHolder(filter)
    holder.setName(CountedName.Filter.name)
    holder
  }
}

case class FilterAdder(
  filterHolder: FilterHolder,
  pathSpec: String = "/*",
  dispatches: EnumSet[DispatcherType] = EnumSet.of(DispatcherType.REQUEST)
) {
  def addToContext(ctx: ServletContextHandler) {
    ctx.addFilter(filterHolder, pathSpec, dispatches)
  }
}

case class CountedName(prefix: String) {
  private val counter = new java.util.concurrent.atomic.AtomicInteger
  def name = prefix + " " + counter.incrementAndGet
}

object CountedName {
  val Servlet = CountedName("Servlet")
  val Filter = CountedName("Filter")
}
