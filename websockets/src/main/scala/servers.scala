package unfiltered.netty.websockets

import unfiltered.netty.{Server => NServer, HouseKeepingChannelHandler}

@deprecated("Use unfiltered.netty.Http, it supports websocket plans")
object WebSocketServer {
  import unfiltered.request.{Path=>UFPath}

  def apply(Path: String, port: Int)(intent: Plan.SocketIntent) =
    new WebSocketServer(port, ({ case UFPath(Path) => intent}: Plan.Intent))

  def apply(host: String, Path: String,  port: Int)(intent: Plan.SocketIntent) =
    new WebSocketServer(host, port, ({ case UFPath(Path) => intent }: Plan.Intent))

}

@deprecated("Use unfiltered.netty.Http, it supports websocket plans")
class WebSocketServer(val host: String, val port: Int, intent: Plan.Intent)
    extends NServer {
  import org.jboss.{netty => jnetty}
  import jnetty.channel.{ChannelPipeline, ChannelPipelineFactory, ChannelStateEvent}
  import jnetty.handler.codec.http.{HttpChunkAggregator, HttpRequestDecoder, HttpResponseEncoder}
  import jnetty.channel.Channels._

  def stop() = {
    closeConnections()
    destroy()
  }

  def this(port: Int, intent: Plan.Intent) =
    this("0.0.0.0", port, intent)

  lazy val pipelineFactory = new ChannelPipelineFactory {
    def getPipeline = (pipeline /: pipings) ((p, e) => { p.addLast(e._1, e._2); p })
    lazy val pipings = Map(
      "decoder" -> new HttpRequestDecoder,
      "aggregator" -> new HttpChunkAggregator(65536),
      "encoder" -> new HttpResponseEncoder,
      "handler" -> Planify(intent),
      "housekeeping" -> new HouseKeepingChannelHandler(channels)
    )
  }
}
