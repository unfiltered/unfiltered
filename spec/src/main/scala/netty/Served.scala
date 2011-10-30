package unfiltered.spec.netty

import org.specs._

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

trait Started extends unfiltered.spec.Hosted {
  shareVariables()
  def server: Server
  
  doBeforeSpec { server.start() }
  doAfterSpec { server.stop(); server.destroy(); executor.shutdown() }

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
