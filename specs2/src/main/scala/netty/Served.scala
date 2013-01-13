package unfiltered.specs2.netty

import org.specs2._
import org.specs2.specification._

import unfiltered.netty._
import unfiltered.netty.cycle._

trait Planned extends Served {
  import unfiltered.netty.cycle._

  def setup = _.chunked().handler(planify(intent))
  def intent[A,B]: unfiltered.Cycle.Intent[A,B]
}

trait Served extends Started {
  def setup: (Http => Http)
  lazy val server = setup(Http(port))
}

trait Started extends unfiltered.specs2.Hosted with BaseSpecification {
	
  def server: Server
  
  def after = {
    server.stop()
    server.destroy()
    executor.shutdown()
  }

  def before = {
    server.start()
  }

  override def map(fs: =>Fragments) = Step(before) ^ fs ^ Step(after)

  lazy val executor = java.util.concurrent.Executors.newCachedThreadPool()

  /** planify using a local executor. Global executor is problematic
   *  for tests since it is shutdown by each server instance.*/
  def planify(intentIn: Plan.Intent) =
    new Plan with DeferralExecutor with DeferredIntent
             with ServerErrorResponse {
      def underlying = executor
      val intent = intentIn
    }
}
