package unfiltered.jetty

import javax.servlet.Filter

import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}

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

    for (filterAdder <- filterAdders.reverseIterator)
      filterAdder.addToContext(ctx)
  }
  def attach(filter: FilterAdder) = copy(filterAdders = filter :: filterAdders)
}
