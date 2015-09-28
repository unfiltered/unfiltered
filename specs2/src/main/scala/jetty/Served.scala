package unfiltered.specs2
package jetty

import unfiltered.specs2.Hosted
import org.specs2.mutable.{Specification, SpecificationLike}
import org.specs2.specification.core.Fragments
import org.specs2.specification.Step


trait Planned extends Served {

  def setup = _.plan(unfiltered.filter.Planify(intent))

  def intent[A, B]: unfiltered.Cycle.Intent[A, B]
}

trait Served extends Specification with Hosted {

  import unfiltered.jetty._

  def after = {
    server.stop()
    server.destroy()
  }

  def before = {
    server.start()
  }

  def setup: (Server => Server)

  lazy val server = setup(Server.http(port))

  override def map(fs: =>Fragments) = Step(before) ^ fs ^ Step(after)
}
