package unfiltered.netty

import unfiltered.Unfiltered.Intent
import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import unfiltered.response.ResponseFunction

/** The default Netty Plan. (There may be other kinds of channel handlers?) */
abstract class Plan extends UnfilteredChannelHandler

class Planify(val intent: Intent[DefaultHttpRequest, ResponseFunction]) extends Plan

object Planify {
  def apply(intent: Intent[DefaultHttpRequest, ResponseFunction]) = new Planify(intent)
}

case class Channeled(cf: org.jboss.netty.channel.Channel => Unit) extends unfiltered.response.ResponseFunction {
  def apply[T](res: unfiltered.response.HttpResponse[T]) = res
}
