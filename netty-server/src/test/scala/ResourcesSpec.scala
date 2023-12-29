package unfiltered.netty

import okhttp3.HttpUrl
import org.specs2.mutable.Specification

/** Tests a netty server configured to handle static resources only */
class ResourcesSpec extends Specification with unfiltered.specs2.netty.Served {
  def setup = _.resources(getClass().getResource("/files/"), passOnFail = false)
  "A resource server" should {
    "respond with a valid file" in {
      httpx(host / "foo.css").as_string must_== "* { margin:0; }"
    }
    "respond with an expected Content-Type" in {
      def mustHaveType(path: String, `type`: String) =
        httpx(req(host / path)).firstHeader("Content-Type") must_== Some(`type`)
      mustHaveType("foo.html", "text/html")
      mustHaveType("foo.css", "text/css")
      mustHaveType("foo.txt", "text/plain")
      mustHaveType("foo.js", "application/javascript")
    }
    "respond with useful headers" in {
      val headers = httpx(req(host / "foo.css")).headers
      headers must haveKey("date")
      headers must haveKey("expires")
      headers must haveKey("last-modified")
      headers must haveKey("content-length")
      headers must haveKey("content-type")
      headers must haveKey("cache-control")
    }
    "respond sith Forbidden (403) for requests with questionable paths" in {
      httpx(host / ".." / "..").code must be_==(403)
    }
    "respond with NotFound (404) for requests for non-existant files" in {
      httpx(host / "foo.bar").code must be_==(404)
    }
    "respond with Forbidden (403) for directory traversal requests" in {
      httpx(HttpUrl.parse(s"http://localhost:$port///etc/passwd")).code must be_==(403)
    }
    "respond with Forbidden (403) for requests for a directory by default" in {
      httpx(host).code must be_==(403)
    }
    "respond with BadRequest (400) with a non GET request" in {
      httpx(req(host / "foo.css").POST("")).code must be_==(400)
    }
    "respond with a NotModified (304) with a If-Modified-Since matches resources lastModified time" in {
      import java.util.{Date, GregorianCalendar}
      import java.io.File
      val rsrc = new File(getClass().getResource("/files/foo.css").getFile)
      val cal = new GregorianCalendar()
      cal.setTime(new Date(rsrc.lastModified))
      val ifmodsince = Map("If-Modified-Since" -> Dates.format(cal.getTime))
      httpx(req(host / "foo.css") <:< ifmodsince).code must be_==(304)
    }
  }
}
