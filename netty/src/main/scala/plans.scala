package unfiltered.netty

import unfiltered.request.HttpRequest
import unfiltered.response.ResponseFunction
import org.jboss.netty.handler.codec.http.DefaultHttpRequest


class Planify(val filter: PartialFunction[HttpRequest[DefaultHttpRequest], ResponseFunction]) extends UnfilteredChannelHandler

object Planify {
  def apply(filter: PartialFunction[HttpRequest[DefaultHttpRequest], ResponseFunction]) = new Planify(filter)
}
