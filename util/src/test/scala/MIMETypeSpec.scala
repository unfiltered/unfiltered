package unfiltered.util

import org.specs2.mutable.Specification

object MIMETypeSpec extends Specification {
  "MIMETypes" should {
    "parse text/plain" in {
      val mime = MIMEType.parse("text/plain")
      mime should beSome(MIMEType("text", "plain", Map.empty))
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
  }
}
