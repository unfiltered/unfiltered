package unfiltered.directives

import org.specs._

import unfiltered.request._

object DirectivesSpecJetty
extends unfiltered.spec.jetty.Planned
with DirectivesSpec

object DirectivesSpecNetty
extends unfiltered.spec.netty.Planned
with DirectivesSpec

trait DirectivesSpec extends unfiltered.spec.Hosted {
  import unfiltered.response._
  import unfiltered.response._
  import unfiltered.directives._, Directives._

  import dispatch._, Defaults._

  // it's simple to define your own directives
  def contentType(tpe:String) =
    when{ case RequestContentType(`tpe`) => } orElse UnsupportedMediaType

  // enables `===` in awesome_json case
  implicit val contentTypeAwesome =
    Directive.Eq { (R:RequestContentType.type, value:String) =>
      when { case R(`value`) => value } orElse UnsupportedMediaType
    }

  def intent[A,B] = Directive.Intent.Path {
    case Seg(List("accept_json", id)) =>
      for {
        _ <- POST
        _ <- contentType("application/json")
        _ <- Accepts.Json
        r <- request[Any]
      } yield Ok ~> JsonContent ~> ResponseBytes(Body.bytes(r))
    case Seg(List("awesome_json", id)) =>
      for {
        _ <- POST
        _ <- RequestContentType === "application/json" // <-- awesome syntax
        _ <- Accepts.Json
        r <- request[Any]
      } yield Ok ~> JsonContent ~> ResponseBytes(Body bytes r)
  }

  val someJson = """{"a": 1}"""

  def localhost = dispatch.host("127.0.0.1", port)

  "Directives" should {
    "respond with json if accepted" in {
      val resp = Http(localhost / "accept_json" / "123"
        <:< Map("Accept" -> "application/json")
        <:< Map("Content-Type" -> "application/json")
        << someJson OK as.String)
      resp() must_== someJson
    }
    "respond with not acceptable accepts header missing" in {
      val resp = Http(localhost / "accept_json" / "123"
        <:< Map("Content-Type" -> "application/json")
        << someJson)
      resp().getStatusCode must_== 406
    }
    "respond with unsupported media if content-type wrong" in {
      val resp = Http(localhost / "accept_json" / "123"
        <:< Map("Accept" -> "application/json")
        <:< Map("Content-Type" -> "text/plain")
        << someJson)
      resp().getStatusCode must_== 415
    }
    "respond with 404 if not matching" in {
      val resp = Http(localhost / "accept_other" / "123"
        << someJson)
      resp().getStatusCode must_== 404
    }
  }
  "Directives decorated" should {
    "respond with json if accepted" in {
      val resp = Http(localhost / "awesome_json" / "123"
        <:< Map("Accept" -> "application/json")
        <:< Map("Content-Type" -> "application/json")
        << someJson OK as.String)
      resp() must_== someJson
    }
    "respond with unsupported media if content-type wrong" in {
      val resp = Http(localhost / "awesome_json" / "123"
        <:< Map("Accept" -> "application/json")
        <:< Map("Content-Type" -> "text/plain")
        << someJson)
      resp().getStatusCode must_== 415
    }
    "respond with unsupported media if content-type missing" in {
      val resp = Http(localhost / "awesome_json" / "123"
        <:< Map("Accept" -> "application/json")
        << someJson)
      resp().getStatusCode must_== 415
    }
  }
}
