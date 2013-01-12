package unfiltered.specs2
package jetty

import unfiltered.specs2.Hosted
import org.specs2.specification.{BaseSpecification, Step, Fragments}


trait Planned extends Served {

  def setup = _.plan(unfiltered.filter.Planify(intent))

  def intent[A, B]: unfiltered.Cycle.Intent[A, B]
}

trait Served extends Hosted with BaseSpecification {

  import unfiltered.jetty._

  def after = {
    server.stop()
    server.destroy()
  }

  def before = {
    server.start()
  }

  def setup: (Server => Server)

  lazy val server = setup(Http(port))

  override def map(fs: =>Fragments) = Step(before) ^ fs ^ Step(after)
}
