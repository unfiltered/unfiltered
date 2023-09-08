package unfiltered.netty

import io.netty.channel.EventLoopGroup
import io.netty.channel.group.{ ChannelGroup, DefaultChannelGroup }
import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup}
import io.netty.channel.kqueue.{KQueue, KQueueEventLoopGroup}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.util.concurrent.{EventExecutor, GlobalEventExecutor}

/** Defines the set of resources used for process scheduling
 *  and collecting active channels needed for graceful shutdown */
trait Engine {
  /** a shared event loop group for accepting connections shared between bootstraps */
  def acceptor: EventLoopGroup
  /** a shared event loop group for handling accepted connections shared between bootstraps */
  def workers: EventLoopGroup
  /** a channel group used to collect active channels so that they may be shutdown properly on RunnableServer#stop() */
  def channels: ChannelGroup
}

object Engine {
  object Default extends Engine {
    private [Default] def bestEventLoopGroup = if (Epoll.isAvailable) {
      new EpollEventLoopGroup()
    } else if (KQueue.isAvailable) {
      new KQueueEventLoopGroup()
    } else {
      new NioEventLoopGroup()
    }
    def acceptor: EventLoopGroup = bestEventLoopGroup
    def workers: EventLoopGroup = bestEventLoopGroup
    def channels: ChannelGroup = defaultChannels(GlobalEventExecutor.INSTANCE)
  }
  private [Engine] def defaultChannels(executor: EventExecutor) =
    new DefaultChannelGroup("Netty Unfiltered Server Channel Group", executor)

  /** An interface building netty server engines */
  trait Builder[T] {
    def engine: Engine

    def use(engine: Engine): T

    /** Specifies the EventLoopGroup use to handle incoming connections */
    def acceptor(group: EventLoopGroup) = use(new Engine {
      lazy val acceptor = group
      lazy val workers = engine.workers
      lazy val channels = engine.channels
    })

    /** Specifies the EventLoopGroup use to handle processing of registered channels */
    def workers(group: EventLoopGroup) = use(new Engine {
      lazy val acceptor = engine.acceptor
      lazy val workers = group
      lazy val channels = engine.channels
    })

    /** Specifies the ChannelGroup used for collecting connected channels */
    def channels(group: ChannelGroup) = use(new Engine {
      lazy val acceptor = engine.acceptor
      lazy val workers = engine.workers
      lazy val channels = group
    })

    /** Sets channels to a DefaultChannelGroup using the given executor */
    def channelsExecutor(executor: EventExecutor) =
      channels(defaultChannels(executor))
  }
}
