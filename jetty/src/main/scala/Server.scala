package unfiltered.jetty

import javax.servlet.Filter

import org.eclipse.jetty.server.handler.ContextHandlerCollection

/** Holds connector providers that listen to selected ports and interfaces.
  * ConnectorBuilder provides convenience methods for attaching connectors. */
case class Server(
  connectorProviders: List[ConnectorProvider],
  contextAdders: List[ContextAdder]
) extends unfiltered.util.RunnableServer
    with unfiltered.util.PlanServer[Filter]
    with ConnectorBuilder {
  type ServerBuilder = Server

  def attach(connector: ConnectorProvider) = copy(
    connectorProviders = connector :: connectorProviders
  )
  def attach(contextAdder: ContextAdder) = copy(
    contextAdders = contextAdder :: contextAdders
  )

  def originalContext(replace: ContextAdder => ContextAdder) = copy(
    contextAdders = contextAdders.reverse match {
      case head :: tail => (replace(head) :: tail).reverse
      case _ => contextAdders
    })

  /** attaches to first-added (last) context */
  def attach(filterAdder: FilterAdder) = originalContext(_.attach(filterAdder))

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

  def context(path: String)(block: ContextAdder => ContextAdder) = attach(
    block(DefaultServletContextAdder(path, Nil, None))
  )

  def filter(filter: => Filter) = attach(FilterAdder(BasicFilterHolder(filter)))

  def makePlan(plan: => Filter) = filter(plan)

  def resources(path: java.net.URL) = originalContext(_.resources(path))


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
  * ConnectorBuilder provides convenience methods for attaching
  * connectors. */
object Server extends ConnectorBuilder {
  def attach(connector: ConnectorProvider) =
    Server(connector :: Nil, DefaultServletContextAdder("/", Nil, None) :: Nil)
}
