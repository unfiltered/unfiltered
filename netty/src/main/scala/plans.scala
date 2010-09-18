package unfiltered.netty

import unfiltered.Unfiltered.Intent
import org.jboss.netty.handler.codec.http.DefaultHttpRequest

/** The default Netty Plan. (There may be other kinds of channel handlers?) */
abstract class Plan extends UnfilteredChannelHandler

class Planify(val intent: Intent[DefaultHttpRequest]) extends Plan

object Planify {
  def apply(intent: Intent[DefaultHttpRequest]) = new Planify(intent)
}
