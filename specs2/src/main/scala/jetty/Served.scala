package unfiltered.specs2
package jetty

import org.specs2.mutable._
import unfiltered.jetty.Server

trait Planned extends Served {

  def setup: Server => Server = _.plan(unfiltered.filter.Planify(intent))

  def intent[A, B]: unfiltered.Cycle.Intent[A, B]
}

trait Served extends Hosted with SpecificationLike {

  import unfiltered.jetty._

  def setup: (Server => Server)

  lazy val server: Server = setup(Server.http(port))

  override def afterAll(): Unit = {
    server.stop()
    server.destroy()
    super.afterAll()
  }

  override def beforeAll(): Unit = {
    server.start()
    super.beforeAll()
  }
}
