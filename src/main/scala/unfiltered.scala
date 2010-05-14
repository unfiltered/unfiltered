package unfiltered

import javax.servlet.{Filter, FilterConfig, FilterChain, ServletRequest, ServletResponse}
import unfiltered.response._

trait InittedFilter extends Filter {
  private var config_var: FilterConfig = _
  def init(config: FilterConfig) { config_var = config }
  def config = config_var

  def destroy { }
}

class Handler(val filter: PartialFunction[ServletRequest, Response]) extends InittedFilter {
  val complete_filter = filter.orElse[ServletRequest, Response] {
    case _ => Pass
  }
  def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
     complete_filter(request) match {
       case Pass => chain.doFilter(request, response)
       case _ => //
     }
  }
}
