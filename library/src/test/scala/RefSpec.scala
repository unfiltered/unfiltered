package unfiltered.response.link

import org.scalacheck.Gen
import org.scalatest._
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class RefSpec extends PropSpec with GeneratorDrivenPropertyChecks with Matchers {

  property("'title*' takes precedence over 'title'") { ??? }

  doThings("rel")
  doThings("media")
  doThings("title")
  doThings("title*")
  doThings("type")

  def doThings(param: String): Unit = {
    property(s"'$param' parameter occurs only once") { ??? }
  }

}
