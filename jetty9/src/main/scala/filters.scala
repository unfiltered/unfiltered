package unfiltered.jetty

import java.util.EnumSet
import java.util.concurrent.atomic.AtomicInteger
import javax.servlet.{ Filter, DispatcherType }

import org.eclipse.jetty.servlet.{ FilterHolder, ServletContextHandler }

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
  private val counter = new AtomicInteger
  def name = prefix + " " + counter.incrementAndGet
}

object CountedName {
  val Servlet = CountedName("Servlet")
  val Filter = CountedName("Filter")
}
