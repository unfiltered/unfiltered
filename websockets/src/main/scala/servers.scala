package unfiltered.websockets

import unfiltered.netty.{Server => NServer, HouseKeepingChannelHandler}

object WebSocketServer {
  def apply(path: String, port: Int)(intent: PartialFunction[SocketCallback, Unit]) =
     new WebSocketServer(port, path, intent).run
  def apply(host: String, path: String, port: Int)(intent: PartialFunction[SocketCallback, Unit]) =
      new WebSocketServer(port, host, path, intent).run
}

case class WebSocketServer(port: Int, host: String, path: String, 
                           intent: PartialFunction[SocketCallback, Unit]) 
    extends NServer with unfiltered.util.RunnableServer {
  import org.jboss.{netty => netty}
  import netty.channel.{ChannelPipeline, ChannelPipelineFactory, ChannelStateEvent}
  import netty.handler.codec.http.{HttpChunkAggregator, HttpRequestDecoder, HttpResponseEncoder}
  import netty.channel.Channels._
  
  def stop() = {
    closeConnections()
    destroy()
  }
  def this(port: Int, path: String, intent: PartialFunction[SocketCallback, Unit]) = 
    this(port, "0.0.0.0", path, intent)
  
  lazy val pipelineFactory = new ChannelPipelineFactory {
    def getPipeline = (pipeline /: pipings) ((p, e) => { p.addLast(e._1, e._2); p })
    lazy val pipings = Map(
      "decoder" -> new HttpRequestDecoder,
      "aggregator" -> new HttpChunkAggregator(65536),
      "encoder" -> new HttpResponseEncoder,
      "handler" -> new WebSocketHandler(path, intent),
      "housekeeping" -> new HouseKeepingChannelHandler(channels)
    ) 
  }
}
