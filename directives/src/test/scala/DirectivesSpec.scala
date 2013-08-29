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

  case class BadParam(msg: String) extends ResponseJoiner(msg)( messages =>
      BadRequest ~> ResponseString(messages.mkString("\n"))
  )

  implicit val asInt =
    data.as.String ~> data.as.Int.fail((name, i) => BadParam(name + " is not an int: " + i))

  implicit def require[T] = data.Require[T].fail(name => BadParam(name + " is missing"))

  val asEven = data.Predicate[Int]( _ % 2 == 0 ).fail(
    (name, i) => BadParam(name + " is not even: " + i)
  )

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
    case Seg(List("valid_parameters")) =>
      for {
        optInt <- data.as.Option[Int] named "option_int"
        reqInt <- data.as.Require[Int] named "require_int"
        evenInt <- (asEven ~> require) named "even_int"
        _ <- data.as.String ~> data.as.Int named "ignored_explicit_int"
      } yield Ok ~> ResponseString((
        evenInt + optInt.getOrElse(0) + reqInt
      ).toString)
    case Seg(List("independent_parameters")) =>
      for {
        optInt & reqInt & evenInt & _ <-
          (data.as.Option[Int] named "option_int") &
          (data.as.Require[Int] named "require_int") &
          ((asEven ~> require) named "even_int") &
          (data.as.String ~> data.as.Int named "ignored_explicit_int")
      } yield Ok ~> ResponseString((
        optInt.getOrElse(0) + reqInt + evenInt
      ).toString)
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
  "Directive parameters" should {
    "respond with parameter if accepted" in {
      val resp = Http(localhost / "valid_parameters"
        << Map(
          "option_int" -> 3.toString,
          "require_int" -> 4.toString,
          "even_int" -> 8.toString
        ) OK as.String)
      resp() must_== "15"
    }
    "respond if optional parameters are missing" in {
      val resp = Http(localhost / "valid_parameters"
        << Map(
          "require_int" -> 4.toString,
          "even_int" -> 8.toString
        ) OK as.String)
      resp() must_== "12"
    }
    "fail if even format is wrong" in {
      val resp = Http(localhost / "valid_parameters"
        << Map(
          "require_int" -> 4.toString,
          "even_int" -> 7.toString
        ))
      resp().getStatusCode must_== 400
      resp().getResponseBody must_== "even_int is not even: 7"
    }
    "fail if int format is wrong" in {
      val resp = Http(localhost / "valid_parameters"
        << Map(
          "require_int" -> 4.toString,
          "even_int" -> "eight"
        ))
      resp().getStatusCode must_== 400
      resp().getResponseBody must_== "even_int is not an int: eight"
    }
    "fail if required parameter is missing" in {
      val resp = Http(localhost / "valid_parameters"
        << Map(
          "require_int" -> 4.toString
        ))
      resp().getStatusCode must_== 400
      resp().getResponseBody must_== "even_int is missing"
    }
  }
  "Directive independent parameters" should {
    "respond with parameter if accepted" in {
      val resp = Http(localhost / "independent_parameters"
        << Map(
          "option_int" -> 3.toString,
          "require_int" -> 4.toString,
          "even_int" -> 8.toString
        ) OK as.String)
      resp() must_== "15"
    }
    "respond with all errors" in {
      val resp = Http(localhost / "independent_parameters"
        << Map(
          "option_int" -> "four",
          "even_int" -> 7.toString
        ))
      resp().getStatusCode must_== 400
      resp().getResponseBody must_== """option_int is not an int: four
require_int is missing
even_int is not even: 7"""
    }
  }
}
