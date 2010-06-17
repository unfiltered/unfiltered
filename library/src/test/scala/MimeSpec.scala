package unfiltered.request

import org.specs._

class MimeSpec extends Specification {
  "Mime" should {
    "match strings with known extensions" in {
      ("test.json" match {
        case Mime(mime) => Some(mime)
        case _ => None
      }) must beSome("application/json")
    }
    "match strings with multiple extensions" in {
      ("test.ext.json" match {
        case Mime(mime) => Some(mime)
        case _ => None
      }) must beSome("application/json")
    }
    "not match strings with no extensions" in {
      ("test" match {
        case Mime(mime) => Some(mime)
        case _ => None
      }) must beNone
    }
    "not match strings with unknown extensions" in {
      ("test.dson" match {
        case Mime(mime) => Some(mime)
        case _ => None
      }) must beNone
    }
  }
}