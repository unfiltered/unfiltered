package unfiltered.jetty

import javax.servlet.Filter

import org.eclipse.jetty.server.handler.ContextHandlerCollection

/** Holds connector providers that listen to selected ports and interfaces.
  * ConnectorBuilder provides convenience methods for adding connectors. */
case class Server(
  connectorProviders: List[ConnectorProvider],
  contextAdders: List[ContextAdder]
) extends unfiltered.util.RunnableServer
    with unfiltered.util.PlanServer[Filter]
    with ConnectorBuilder {
  type ServerBuilder = Server

  /** Add a connector to this server. */
  def connector(connector: ConnectorProvider) = copy(
    connectorProviders = connector :: connectorProviders
  )

  /** Update the server's first-added context. */
  def originalContext(replace: ContextAdder => ContextAdder) = copy(
    contextAdders = contextAdders.reverse match {
      case head :: tail => (replace(head) :: tail).reverse
      case _ => contextAdders
    })

  /** The mutable underlying jetty server object. This is built
    * on-demand according to the discribed configuration. */
  lazy val underlying = {
    val server = new org.eclipse.jetty.server.Server()
    for (provider <- connectorProviders.reverseIterator)
      server.addConnector(provider.connector)
    val contextHandlers = new ContextHandlerCollection
    for (adder <- contextAdders.reverseIterator)
      adder.addToParent(contextHandlers)
    server.setHandler(contextHandlers)
    server
  }

  /** Add a servlet context with the given path */
  def context(path: String)(block: ContextAdder => ContextAdder) = copy(
    contextAdders =
      block(DefaultServletContextAdder(path, Nil, None)) :: contextAdders
  )

  @deprecated("Use `plan(filter)`", "0.8.1")
  def filter(filter: Filter) = plan(filter)

  /** Add a filter as a by-name parameter. Generally you should use
    * `plan(plan)` instead. */
  def makePlan(plan: => Filter) = originalContext(
    _.filterAdder(FilterAdder(BasicFilterHolder(plan)))
  )

  /** Add a resource path to the original, root context */
  def resources(path: java.net.URL) = originalContext(_.resources(path))

  /** Ports used by this server, reported by super-trait */
  def ports: Traversable[Int] = connectorProviders.reverse.map(_.port)

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

/** Base object that used to construct Server instances.
  * ConnectorBuilder provides convenience methods for adding
  * connectors. */
object Server extends ConnectorBuilder {
  def connector(connector: ConnectorProvider) =
    Server(connector :: Nil, DefaultServletContextAdder("/", Nil, None) :: Nil)
}
