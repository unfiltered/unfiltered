package unfiltered.request

import org.specs._

object BasicAuthSpec extends Specification {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  
  import dispatch._
  
  class TestPlan extends unfiltered.Planify({
    case GET(UFPath("/secret", BasicAuth(creds, _))) => creds match {
      case ("test", "secret") => ResponseString("pass")
      case _ => ResponseString("fail")
    }
  })
  val server = unfiltered.server.Http(8080).filter(new TestPlan)
  val host = :/("localhost", 8080)
  
  doBeforeSpec { server.daemonize() }
  
  "Basic Auth" should {
    "authenticate a valid user" in {
      val resp = Http(host / "secret" as_!("test", "secret") as_str)
      resp must_=="pass"
    }
    "not authenticate an invalid user" in {
      val resp = Http(host / "secret" as_!("joe", "shmo") as_str)
      resp must_=="fail"
    }
  }
  
  doAfterSpec { server.stop() }
}