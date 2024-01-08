package unfiltered.jetty

import java.util.EnumSet
import java.util.concurrent.atomic.AtomicInteger
import jakarta.servlet.Filter
import jakarta.servlet.DispatcherType
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.servlet.ServletContextHandler

object BasicFilterHolder {
  def apply(filter: Filter): FilterHolder = {
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
  def addToContext(ctx: ServletContextHandler): Unit = {
    ctx.addFilter(filterHolder, pathSpec, dispatches)
  }
}

case class CountedName(prefix: String) {
  private[this] val counter = new AtomicInteger
  def name: String = prefix + " " + counter.incrementAndGet
}

object CountedName {
  val Servlet: CountedName = CountedName("Servlet")
  val Filter: CountedName = CountedName("Filter")
}
