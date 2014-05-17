package unfiltered.netty

import org.specs2.mutable.Specification

/** Tests a netty server configured to handle static resources only */
object ResourcesSpec extends Specification with unfiltered.specs2.netty.Served {
   import dispatch.classic._

   implicit class toStatusVerb(req: dispatch.classic.Request) {
     def statuscode = dispatch.classic.Handler(req, {
       case (code, _, _) => code
     }, scala.util.control.Exception.nothingCatcher)
   }

   def setup = _.resources(getClass().getResource("/files/"), passOnFail = false)
   "A resource server" should {
     "respond with a valid file" in {
       xhttp(host / "foo.css" as_str) must_==("* { margin:0; }")
     }
     "respond with an expected Content-Type" in {
       def mustHaveType(path: String, `type`: String) =
         xhttp(host / path >:> { h => h }) must havePair(("Content-Type", Set(`type`)))
       mustHaveType("foo.html", "text/html")
       mustHaveType("foo.css", "text/css")
       mustHaveType("foo.txt", "text/plain")
       mustHaveType("foo.js", "application/javascript")
     }
     "respond with useful headers" in {
       val headers = xhttp(host / "foo.css" >:> { h => h })
       headers must haveKey("Date")
       headers must haveKey("Expires")
       headers must haveKey("Last-Modified")
       headers must haveKey("Content-Length")
       headers must haveKey("Content-Type")
       headers must haveKey("Cache-Control")
     }
     "respond sith Forbidden (403) for requests with questionable paths" in {
        xhttp(host / ".." / ".." statuscode) must be_==(403)
     }
     "respond with NotFound (404) for requests for non-existant files" in {
       xhttp(host / "foo.bar" statuscode) must be_==(404)
     }
     "respond with Forbidden (403) for requests for a directory by default" in {
       xhttp(host statuscode) must be_==(403)
     }
     "respond with BadRequest (400) with a non GET request" in  {
       xhttp(host.POST / "foo.css" statuscode) must be_==(400)
     }
     "respond with a NotModified (304) with a If-Modified-Since matches resources lastModified time" in {
       import java.util.{Calendar, Date, GregorianCalendar}
       import java.io.File
       val rsrc = new File(getClass().getResource("/files/foo.css").getFile)
       val cal = new GregorianCalendar()
       cal.setTime(new Date(rsrc.lastModified))
       val ifmodsince = Map(
         "If-Modified-Since" -> Dates.format(cal.getTime))
       xhttp(host / "foo.css" <:< ifmodsince statuscode) must be_==(304)
       xhttp(host / "foo.css" <:< ifmodsince >:> { h => h }) must not(havePair(
         "Connection" -> "keep-alive"
       ))
     }
   }
}
