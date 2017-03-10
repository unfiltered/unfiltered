package unfiltered.specs2.netty

import unfiltered.netty.{Server, ServerErrorResponse}
import unfiltered.netty.cycle.{DeferralExecutor, DeferredIntent, Plan}
import org.specs2.mutable._
import io.netty.channel.ChannelHandler.Sharable
import io.netty.util.ResourceLeakDetector

trait Planned extends Served {
  def setup = _.plan(planify(intent))
  def intent[A,B]: unfiltered.Cycle.Intent[A,B]
}

trait Served extends Started {
  def setup: Server => Server
  lazy val server = setup(Server.http(port))
}

trait Started extends unfiltered.specs2.Hosted with SpecificationLike {

  // Enables paranoid resource leak detection which reports where the leaked object was accessed recently,
  // at the cost of the highest possible overhead (for testing purposes only).
  ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID)

  def server: Server

  override def afterAll(): Unit = {
    server.stop()
    server.destroy()
    executor.shutdown()
    super.afterAll()
  }

  override def beforeAll(): Unit = {
    server.start()
    super.beforeAll()
  }

 // override def map(fs: =>Fragments) = Step(before) ^ fs ^ Step(after)

  lazy val executor = java.util.concurrent.Executors.newCachedThreadPool()

  /** planify using a local executor. Global executor is problematic
   *  for tests since it is shutdown by each server instance.*/
  def planify(intentIn: Plan.Intent) =
    new StartedPlan(intentIn)

  @Sharable
  class StartedPlan(intentIn: Plan.Intent) extends Plan
   with DeferralExecutor
   with DeferredIntent
   with ServerErrorResponse {
    def underlying = executor
    val intent = intentIn
  }
}
