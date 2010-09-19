package unfiltered.spec.netty

import org.specs._

trait Served extends unfiltered.spec.Hosted {
  shareVariables()

  import unfiltered.netty._
  def setup: (Int => Server)
  lazy val server = setup(port)
  var channel: org.jboss.netty.channel.Channel = _
  
  doBeforeSpec { channel = server.start() }
  doAfterSpec { channel.unbind() }
}
