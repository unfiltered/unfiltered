package unfiltered.request

import org.scalatest.{Matchers, WordSpec}
import unfiltered.response._
import test.AgentStrings

class AgentSpecJetty
extends AgentSpec
with unfiltered.scalatest.jetty.Planned // TODO this order matters!

//object AgentSpecNetty
//extends Specification
//with unfiltered.specs2.netty.Planned
//with AgentSpec

trait AgentSpec extends WordSpec with Matchers with unfiltered.scalatest.Hosted {
  def intent[A,B]: unfiltered.Cycle.Intent[A,B] = {
    case GET(_) & AgentIs.Chrome(_) => ResponseString("chromium")
    case GET(_) & AgentIs.Safari(_) & AgentIs.Mobile(_) => ResponseString("safari mobile")
    case GET(_) & AgentIs.Safari(_) => ResponseString("safari")
    case GET(_) & AgentIs.FireFox(_) => ResponseString("firefox")
    case GET(_) & AgentIs.IE(_) => ResponseString("ie")
  }

  "AgentIs should" should {
    "match chrome" in {
      val resp = http(host / "test" <:< Map("User-Agent" -> AgentStrings.chrome.head) as_str)
      resp should be("chromium")
    }
    "match safari" in {
      val resp = http(host / "test" <:< Map("User-Agent" -> AgentStrings.safari.head) as_str)
      resp should be("safari")
    }
    "match mobile safari" in {
      val resp = http(host / "test" <:< Map("User-Agent" -> "Mozilla/5.0 (iPhone; U; CPU iPhone OS 4_1 like Mac OS X; en-us) AppleWebKit/532.9 (KHTML, like Gecko) Version/4.0.5 Mobile/8B5097d Safari/6531.22.7") as_str)
      resp should be("safari mobile")
    }
    "match firefox" in {
      val resp = http(host / "test" <:< Map("User-Agent" -> AgentStrings.firefox.head)  as_str)
      resp should be("firefox")
    }
    "match ie" in {
      val resp = http(host / "test" <:< Map("User-Agent" -> AgentStrings.ie.head)  as_str)
      resp should be("ie")
    }
  }
}