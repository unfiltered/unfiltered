package unfiltered.request

import org.specs2.mutable._

class BasicAuthSpecJetty extends Specification with unfiltered.specs2.jetty.Planned with BasicAuthSpec

class BasicAuthSpecNetty extends Specification with unfiltered.specs2.netty.Planned with BasicAuthSpec

trait BasicAuthSpec extends Specification with unfiltered.specs2.Hosted {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  def intent[A, B]: unfiltered.Cycle.Intent[A, B] = {
    case GET(UFPath("/secret") & BasicAuth(name, pass)) =>
      (name, pass) match {
        case ("test", "secret") => ResponseString("pass")
        case _ => ResponseString("fail")
      }
    case GET(UFPath("/spec") & BasicAuth(name, pass)) =>
      (name, pass) match {
        case ("test", "secret:password") => ResponseString("pass")
        case _ => ResponseString("fail")
      }
    case GET(UFPath("/blank-blank") & BasicAuth(name, pass)) =>
      (name, pass) match {
        case ("", "") => ResponseString("pass")
        case _ => ResponseString("fail")
      }
    case GET(UFPath("/user-blank") & BasicAuth(name, pass)) =>
      (name, pass) match {
        case ("test", "") => ResponseString("pass")
        case _ => ResponseString("fail")
      }
    case GET(UFPath("/blank-pass") & BasicAuth(name, pass)) =>
      (name, pass) match {
        case ("", "secret") => ResponseString("pass")
        case _ => ResponseString("fail")
      }
    case GET(UFPath("/blank-pass-colon") & BasicAuth(name, pass)) =>
      (name, pass) match {
        case ("", "secret:password") => ResponseString("pass")
        case _ => ResponseString("fail")
      }
    case _ => ResponseString("not found")
  }

  "Basic Auth" should {
    "authenticate a valid user" in {
      val resp = http(req(host / "secret").as_!("test", "secret")).as_string
      resp must_== "pass"
    }
    "authenticate a valid user with a blank password" in {
      val resp = http(req(host / "user-blank").as_!("test", "")).as_string
      resp must_== "pass"
    }
    "authenticate a valid user with a password that contains a :" in {
      val resp = http(req(host / "spec").as_!("test", "secret:password")).as_string
      resp must_== "pass"
    }
    "authenticate a valid user with a blank username and a blank password" in {
      val resp = http(req(host / "blank-blank").as_!("", "")).as_string
      resp must_== "pass"
    }
    "authenticate a valid user with a blank username and a good password" in {
      val resp = http(req(host / "blank-pass").as_!("", "secret")).as_string
      resp must_== "pass"
    }
    "authenticate a valid user with a blank username and a good password that contains a :" in {
      val resp = http(req(host / "blank-pass-colon").as_!("", "secret:password")).as_string
      resp must_== "pass"
    }
    "not authenticate an invalid user" in {
      val resp = httpx(req(host / "secret").as_!("joe", "shmo")).as_string
      resp must_== "fail"
    }
    "not authenticate an empty Authorization header" in {
      val resp = httpx(req(host / "secret") <:< Map("Authorization" -> "")).as_string
      resp must_== "not found"
    }
  }
}
