package unfiltered.util

import org.specs2.mutable.Specification

object MIMETypeSpec extends Specification {
  "MIMETypes" should {
    "parse */*" in {
      val mime = MIMEType.parse("*/*")
      mime should beSome(MIMEType.ALL)
    }
    "parse APPLICATION/*" in {
      val mime = MIMEType.parse("APPLICATION/*")
      mime should beSome(MIMEType("application", "*"))
    }
    "parse text/plain" in {
      val mime = MIMEType.parse("text/plain")
      mime should beSome(MIMEType("text", "plain"))
    }
    "parse text/plain; charset=utf-8" in {
      MIMEType.parse("text/plain; charset=utf-8") should beSome(new MIMEType("text", "plain", Map("charset" -> "utf-8")))
    }

    "parse text/plain; charset=utf-8; param2=hello" in {
      MIMEType.parse("text/plain; charset=utf-8; param2=hello") should beSome(new MIMEType("text", "plain", Map("charset" -> "utf-8", "param2" -> "hello")))
    }

    "parse application/foo+xml; charset=utf-8; param2=hello" in {
      MIMEType.parse("application/foo+xml; charset=utf-8; param2=hello") should beSome(new MIMEType("application", "foo+xml", Map("charset" -> "utf-8", "param2" -> "hello")))
    }
    "not parse" in {
      MIMEType.parse("application") should beNone
      MIMEType.parse("*") should beNone
      MIMEType.parse("foo") should beNone
      MIMEType.parse("foosdfsjkljkla%/asasdoskefhf") should beNone
    }
  }
}
