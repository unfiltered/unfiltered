package unfiltered.request

import test.AgentStrings
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AgentMatchSpec extends AnyWordSpec with Matchers {
  "Chrome strings" should {
    "satisfy the chrome predicate" in {
      AgentStrings.chrome.map(AgentIs.chromeAM).forall(identity) shouldBe true
    }
    "fail the firefox predicate" in {
      AgentStrings.chrome.map(AgentIs.firefoxAM).forall(!_) shouldBe true
    }
    "fail the safari predicate" in {
      AgentStrings.chrome.map(AgentIs.safariAM).forall(!_) shouldBe true
    }
    "fail the ie predicate" in {
      AgentStrings.chrome.map(AgentIs.ieAM).forall(!_) shouldBe true
    }
    "fail the opera predicate" in {
      AgentStrings.chrome.map(AgentIs.operaAM).forall(!_) shouldBe true
    }
  }
  
  "Safari strings" should {
    "satisfy the safari predicate" in {
      AgentStrings.safari.map(AgentIs.safariAM).forall(identity) shouldBe true
    }
    "fail the chrome predicate" in {
      AgentStrings.safari.map(AgentIs.chromeAM).forall(!_) shouldBe true
    }
    "fail the firefox predicate" in {
      AgentStrings.safari.map(AgentIs.firefoxAM).forall(!_) shouldBe true
    }
    "fail the ie predicate" in {
      AgentStrings.safari.map(AgentIs.ieAM).forall(!_) shouldBe true
    }
    "fail the opera predicate" in {
      AgentStrings.safari.map(AgentIs.operaAM).forall(!_) shouldBe true
    }
  }
  
  "Firefox strings" should {
    "satisfy the firefox predicate" in {
      AgentStrings.firefox.map(AgentIs.firefoxAM).forall(identity) shouldBe true
    }
    "fail the chrome predicate" in {
      AgentStrings.firefox.map(AgentIs.chromeAM).forall(!_) shouldBe true
    }
    "fail the safari predicate" in {
      AgentStrings.firefox.map(AgentIs.safariAM).forall(!_) shouldBe true
    }
    "fail the ie predicate" in {
      AgentStrings.firefox.map(AgentIs.ieAM).forall(!_) shouldBe true
    }
    "fail the opera predicate" in {
      AgentStrings.firefox.map(AgentIs.operaAM).forall(!_) shouldBe true
    }
  }
  
  "Opera strings" should {
    "satisfy the opera predicate" in {
      AgentStrings.opera.map(AgentIs.operaAM).forall(identity) shouldBe true
    }
    "fail the chrome predicate" in {
      AgentStrings.opera.map(AgentIs.chromeAM).forall(!_) shouldBe true
    }
    "fail the safari predicate" in {
      AgentStrings.opera.map(AgentIs.safariAM).forall(!_) shouldBe true
    }
    "fail the ie predicate" in {
      AgentStrings.opera.map(AgentIs.ieAM).forall(!_) shouldBe true
    }
    "fail the firefox predicate" in {
      AgentStrings.opera.map(AgentIs.firefoxAM).forall(!_) shouldBe true
    }
  }
  
  "Internet Explorer strings" should {
    "satisfy the ie predicate" in {
      AgentStrings.ie.map(AgentIs.ieAM).forall(identity) shouldBe true
    }
    "fail the chrome predicate" in {
      AgentStrings.ie.map(AgentIs.chromeAM).forall(!_) shouldBe true
    }
    "fail the safari predicate" in {
      AgentStrings.ie.map(AgentIs.safariAM).forall(!_) shouldBe true
    }
    "fail the opera predicate" in {
      AgentStrings.ie.map(AgentIs.operaAM).forall(!_) shouldBe true
    }
    "fail the firefox predicate" in {
      AgentStrings.ie.map(AgentIs.firefoxAM).forall(!_) shouldBe true
    }
  }
}
