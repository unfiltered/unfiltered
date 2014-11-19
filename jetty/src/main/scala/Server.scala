package unfiltered.jetty

import org.eclipse.jetty.server.{NCSARequestLog, Handler}
import unfiltered.util.{ PlanServer, RunnableServer }
import javax.servlet.Filter

import org.eclipse.jetty.server.handler.{ContextHandlerCollection, RequestLogHandler, HandlerCollection}

/** Holds port bindings for selected ports and interfaces. The
  * PortBindings trait provides convenience methods for bindings. */
case class Server(
  portBindings: List[PortBinding],
  contextAdders: List[ContextAdder],
  requestLogging: Option[RequestLogging] = None
) extends RunnableServer
    with PlanServer[Filter]
    with PortBindings {
  type ServerBuilder = Server

  /** Add a port binding to this server. */
  def portBinding(binding: PortBinding) = copy(
    portBindings = binding :: portBindings
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
    for (binding <- portBindings.reverseIterator)
      server.addConnector(binding.connector)
    val contextHandlers = new ContextHandlerCollection
    for (adder <- contextAdders.reverseIterator)
      adder.addToParent(contextHandlers)
    server.setHandler(withLogging(contextHandlers, requestLogging))
    server
  }

  private def withLogging(contextHandlers: ContextHandlerCollection,
                          requestLogging: Option[RequestLogging]) = {
    requestLogging.fold[Handler](
      contextHandlers)(rl => {
      val handlers = new HandlerCollection()
      val requestLogHandler = new RequestLogHandler()
      val requestLog = new NCSARequestLog(rl.filename)
      requestLog.setRetainDays(rl.retainDays);
      requestLog.setExtended(rl.extended);
      requestLog.setLogTimeZone(rl.timezone);
      requestLogHandler.setRequestLog(requestLog);
      handlers.setHandlers(Array(contextHandlers, requestLogHandler))
      handlers
    })
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

  /** Configure global logging of requests to a logfile in Common or Extended log format.
    * [[http://en.wikipedia.org/wiki/Category:Log_file_formats]] */
  def requestLogging(filename: String,
                     extended: Boolean = true,
                     dateFormat: String = "dd/MMM/yyyy:HH:mm:ss Z",
                     timezone: String = "GMT",
                     retainDays: Int = 31) = copy(requestLogging = {
    Some(RequestLogging(filename, extended, dateFormat, timezone, retainDays))
  })

  /** Ports used by this server, reported by super-trait */
  def ports: Traversable[Int] = portBindings.reverse.map(_.port)

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

/** Base object that used to construct Server instances.  The
  * PortBindings trait provides convenience methods for adding
  * bindings. */
object Server extends PortBindings {
  def portBinding(portBinding: PortBinding) =
    Server(portBinding :: Nil, DefaultServletContextAdder("/", Nil, None) :: Nil)
}
