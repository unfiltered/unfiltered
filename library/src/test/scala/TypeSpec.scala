package unfiltered.response

import org.specs._

class TypeSpec extends Specification {
  "CssContent" should {
    "resolve as text/css with a charset" in {
      CssContent.contentType must_== "text/css; charset=utf-8"
    }
  }
  "PdfContent" should {
    "resolve as application/pdf with no charset" in {
      PdfContent.contentType must_== "application/pdf"
    }
  }
}
