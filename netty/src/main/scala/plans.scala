package unfiltered.netty

import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import unfiltered.response.ResponseFunction
import unfiltered.request.HttpRequest

object Plan {
  type Intent = unfiltered.Roundtrip.Intent[DefaultHttpRequest]
}
/** The default Netty Plan. (There may be other kinds of channel handlers?) */
abstract class Plan extends UnfilteredChannelHandler

class Planify(val intent: Plan.Intent) extends Plan

object Planify {
  def apply(intent: Plan.Intent) = new Planify(intent)
}

case class Channeled(cf: org.jboss.netty.channel.Channel => Unit) extends unfiltered.response.ResponseFunction {
  def apply[T](res: unfiltered.response.HttpResponse[T]) = res
}
