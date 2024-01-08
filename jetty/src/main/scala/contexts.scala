package unfiltered.jetty

import jakarta.servlet.Filter
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.resource.Resource

trait ContextAdder {
  def addToParent(parent: ContextHandlerCollection): Unit
  def filterAdder(filter: FilterAdder): ContextAdder
  def plan(filter: Filter): ContextAdder = filterAdder(FilterAdder(BasicFilterHolder(filter)))
  def resources(path: java.net.URL): ContextAdder
  def allowAliases(aliases: Boolean): ContextAdder
}

case class DefaultServletContextAdder(
  path: String,
  filterAdders: List[FilterAdder],
  resourcePath: Option[java.net.URL],
  aliases: Boolean = false
) extends ContextAdder {
  def addToParent(parent: ContextHandlerCollection): Unit = {
    val ctx = new ServletContextHandler(parent, path, false, false)
    val holder = new ServletHolder(classOf[org.eclipse.jetty.servlet.DefaultServlet])
    holder.setName(CountedName.Servlet.name)
    ctx.addServlet(holder, "/")

    for (filterAdder <- filterAdders.reverseIterator)
      filterAdder.addToContext(ctx)

    for (path <- resourcePath)
      ctx.setBaseResource(Resource.newResource(path))

    if (aliases)
      ctx.addAliasCheck(new ContextHandler.ApproveAliases())
  }

  def filterAdder(filter: FilterAdder): ContextAdder = copy(filterAdders = filter :: filterAdders)

  def resources(path: java.net.URL): ContextAdder = copy(resourcePath = Some(path))

  def allowAliases(aliases: Boolean): ContextAdder = copy(aliases = aliases)
}
