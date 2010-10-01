package unfiltered.websockets

import unfiltered.netty.{Server => NServer, HouseKeepingChannelHandler}

object WebSocketServer {
  def apply(path: String, port: Int)(intent: PartialFunction[SocketCallback, Unit]) =
     new WebSocketServer(port, path, intent).run
}

class WebSocketServer(port: Int, path: String, intent: PartialFunction[SocketCallback, Unit]) 
  extends NServer(port, new WebSocketHandler(path, intent)) {
  import org.jboss.{netty => netty}
  import netty.channel.{ChannelPipeline, ChannelPipelineFactory, ChannelStateEvent}
  import netty.handler.codec.http.{HttpChunkAggregator, HttpRequestDecoder, HttpResponseEncoder}
  import netty.channel.Channels._
  
  override lazy val pipelineFactory = new ChannelPipelineFactory {
    def getPipeline = (pipeline /: pipings) ((p, e) => { p.addLast(e._1, e._2); p })
    lazy val pipings = Map(
      "decoder" -> new HttpRequestDecoder,
      "aggregator" -> new HttpChunkAggregator(65536),
      "encoder" -> new HttpResponseEncoder,
      "handler" -> lastHandler,
      "houskeeping" -> new HouseKeepingChannelHandler(channels)
    ) 
  }
  
  /** @todo can this be a trait? */
  def run = Thread.currentThread.getName match {
    case "main" => 
      start()
      println("Embedded websocket server running on port %s." format port)
    case _ => 
      start()
      println("Embedded websocket server running on port %s. Press any key to stop." format port)
      def doWait() {
        try { Thread.sleep(1000) } catch { case _: InterruptedException => () }
        if(System.in.available() <= 0)
          doWait()
      }
      doWait()
      stop()
  }
}

/*
object XWebSocketServer {
  import netty.bootstrap.ServerBootstrap
  import netty.channel.socket.nio.NioServerSocketChannelFactory
  import java.util.concurrent.Executors
  import java.net.InetSocketAddress
  
  class XWebSocketServer[T](port: Int, chanfact: NioServerSocketChannelFactory, path: String, intent: PartialFunction[SocketCallback, Unit]) {
    import netty.channel.Channels._
    import netty.channel.{ChannelPipeline, ChannelPipelineFactory, ChannelStateEvent}
    import netty.channel.group.DefaultChannelGroup
    import netty.handler.codec.http.{HttpChunkAggregator, HttpRequestDecoder, HttpResponseEncoder}
    
    val cg = new DefaultChannelGroup("websocket server")
    
    def run = Thread.currentThread.getName match {
      case "main" => 
        start
        println("Embedded websocket server running on port %s." format port)
      case _ => 
        start
        println("Embedded websocket server running on port %s. Press any key to stop." format port)
        def doWait() {
          try { Thread.sleep(1000) } catch { case _: InterruptedException => () }
          if(System.in.available() <= 0)
            doWait()
        }
        doWait()
        stop
    }
    
    private def start = {
      val boots = new ServerBootstrap(chanfact)
      boots.setPipelineFactory(pipe)
      cg.add(boots.bind(new InetSocketAddress(port)))
    }
    
    def stop = {
      cg.close.awaitUninterruptibly
      chanfact.releaseExternalResources
    }
    
    def pipe = new ChannelPipelineFactory {
      def getPipeline = (pipeline /: pipings) ((p, e) => { p.addLast(e._1, e._2); p })
      lazy val pipings = Map(
        "decoder" -> new HttpRequestDecoder,
        "aggregator" -> new HttpChunkAggregator(65536),
        "encoder" -> new HttpResponseEncoder,
        "handler" -> new WebSocketHandler(path, intent),
        "channel_collector" -> new SimpleChannelUpstreamHandler {
            override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) = cg.add(e.getChannel)
         }
      ) 
    }
  }
  
  def apply(path: String, port: Int)(intent: PartialFunction[SocketCallback, Unit]) =
     new WebSocketServer(port, new NioServerSocketChannelFactory(
        Executors.newCachedThreadPool, Executors.newCachedThreadPool), path, intent
      ).run
}*/