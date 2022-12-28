package unfiltered.directives

import org.specs2.mutable._
import unfiltered.request._
import unfiltered.directives.data.Requiring
import unfiltered.directives.data.Interpreter
import java.util.concurrent.atomic.AtomicLong

import okhttp3.MediaType

object DirectivesSpecJetty
extends Specification
with unfiltered.specs2.jetty.Planned
with DirectivesSpec

object DirectivesSpecNetty
extends Specification
with unfiltered.specs2.netty.Planned
with DirectivesSpec

trait DirectivesSpec extends SpecificationLike with unfiltered.specs2.Hosted {
  import unfiltered.response._
  import Directives._

  // create a directive for a particular content type
  def contentType(tpe:String) =
    when { case RequestContentType(`tpe`) => } orElse UnsupportedMediaType

  // create a directive for any content type
  def someContentType =
    when { case RequestContentType(t) => t } orElse UnsupportedMediaType

  // enables `===` in awesome_json case
  implicit val contentTypeAwesome: Directive.Eq[RequestContentType.type, String, Any, ResponseFunction[Any], String] =
    Directive.Eq { (R:RequestContentType.type, value:String) =>
      when { case R(`value`) => value } orElse UnsupportedMediaType
    }

  case class BadParam(msg: String) extends ResponseJoiner(msg)( messages =>
      BadRequest ~> ResponseString(messages.mkString("\n"))
  )

  implicit val asInt: Interpreter[Seq[String], Option[Int], BadParam] =
    data.as.String ~> data.as.Int.fail((name, i) => BadParam(name + " is not an int: " + i))

  implicit def require[T]: Requiring[T, BadParam] = data.Requiring[T].fail(name => BadParam(name + " is missing"))

  val asEven = data.Conditional[Int]( _ % 2 == 0 ).fail(
    (name, i) => BadParam(name + " is not even: " + i)
  )

  case class Prize(num: Long)
  val callers = new AtomicLong()
  val MaxPrizes = 3

  // limited time offers. expect a side effect!
  val asPrize = (data.Requiring[Prize]
                  .fail(name => BadParam(s"${name} are out of stock"))
                  .named("prizes", Some(callers.getAndIncrement()).filter(_ < MaxPrizes).map(Prize(_))))

  def intent[A,B] = Directive.Intent.Path {
    case "/affirmation" =>
      Directives.success {
        ResponseString("this request needs no validation")
      }
    case "/commit_or" =>
      val a = for {
        _ <- GET
        _ <- commit
        _ <- Directives.failure(BadRequest)
      } yield Ok ~> ResponseString("a")
      val b = for {
        _ <- POST
      } yield Ok ~> ResponseString("b")
      a | b
    case Seg(List("accept_json", id)) =>
      for {
        _ <- POST
        _ <- contentType("application/json")
        _ <- Accepts.Json
        r <- request[Any]
      } yield Ok ~> JsonContent ~> ResponseBytes(Body.bytes(r))
    case Seg(List("if_json", id)) =>
      for {
        _ <- POST
        contentType <- someContentType if contentType == "application/json"
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
    case Seg(List("limited_offer")) =>
      for {
        prize <- asPrize
      } yield Ok ~> ResponseString(
        "Congratulations. You won prize %d".format(prize.num + 1)
      )
    case Seg(List("valid_parameters")) =>
      for {
        optInt <- data.as.Option[Int] named "option_int"
        reqInt <- data.as.Required[Int] named "require_int"
        evenInt <- (asEven ~> require) named "even_int"
        _ <- data.as.String ~> data.as.Int named "ignored_explicit_int"
      } yield Ok ~> ResponseString((
        evenInt + optInt.getOrElse(0) + reqInt
      ).toString)
    case Seg(List("independent_parameters")) =>
      for {
        optInt & reqInt & evenInt & _ <-
          (data.as.Option[Int] named "option_int") &
          (data.as.Required[Int] named "require_int") &
          ((asEven ~> require) named "even_int") &
          (data.as.String ~> data.as.Int named "ignored_explicit_int")
      } yield Ok ~> ResponseString((
        optInt.getOrElse(0) + reqInt + evenInt
      ).toString)
  }

  val someJson = """{"a": 1}"""

  //def localhost = dispatch.host("127.0.0.1", port)
  val JSONContentType = MediaType.parse("application/json")


  "Directives commit" should {
    "respond with expected committed error" in {
      httpx(host / "commit_or").code must_== 400
    }
    "try alternative when failing before commit" in {
      http(req(host / "commit_or").POST("")).code must_== 200
    }
  }
  "Directives" should {
    "response with a condition that is always true" in {
      http(host / "affirmation").as_string must_== "this request needs no validation"
    }
    "respond with expected response given named value" in {
      def expect(n: Int) = {
        val resp = httpx(host / "limited_offer").as_string
        val expected = if (n < MaxPrizes) "Congratulations. You won prize %d".format(n + 1) else "prizes are out of stock"
        resp must_== expected
      }
      (0 to MaxPrizes + 10).forall(expect(_))
    }
    "respond with json if accepted" in {
      val resp = http((req(host / "accept_json" / "123")
        <:< Map("Accept" -> "application/json", "Content-Type" -> "application/json"))
        POST(someJson, null)).as_string
      resp must_== someJson
    }
    "respond with not acceptable accepts header missing" in {
      val resp = httpx(req(host / "accept_json" / "123")
        //<:< Map("Content-Type" -> "application/json")
        POST(someJson, JSONContentType))
      resp.code must_== 406
    }
    "respond with unsupported media if content-type wrong" in {
      val resp = httpx((req(host / "accept_json" / "123")
        <:< Map("Accept" -> "application/json")).POST(someJson))
      resp.code must_== 415
    }
    "respond with 404 if not matching" in {
      val resp = httpx(req(host / "accept_other" / "123").POST(someJson))
      resp.code must_== 404
    }
  }
  "Directives decorated" should {
    "respond with json if accepted" in {
      val resp = http(req(host / "awesome_json" / "123")
        <:< Map("Accept" -> "application/json")
        POST(someJson, JSONContentType)).as_string
      resp must_== someJson
    }
    "respond with unsupported media if content-type wrong" in {
      val resp = httpx(req(host / "awesome_json" / "123")
        <:< Map("Accept" -> "application/json")
        POST(someJson))
      resp.code must_== 415
    }
    "respond with unsupported media if content-type missing" in {
      val resp = httpx(req (host / "awesome_json" / "123")
        <:< Map("Accept" -> "application/json")
        POST(someJson, null))
      resp.code must_== 415
    }
  }
  "Directives if filtering" should {
    "respond with json if accepted" in {
      val resp = http(req(host / "if_json" / "123")
        <:< Map("Accept" -> "application/json")
        POST(someJson, JSONContentType)).as_string
      resp must_== someJson
    }
    "respond with unsupported media if content-type wrong" in {
      val resp = httpx(req(host / "if_json" / "123")
        <:< Map("Accept" -> "application/json")
        POST(someJson))
      resp.code must_== 415
    }
    "respond with unsupported media if content-type missing" in {
      val resp = httpx(req(host / "if_json" / "123")
        <:< Map("Accept" -> "application/json")
        POST(someJson, null))
      resp.code must_== 415
    }
  }
  "Directive parameters" should {
    "respond with parameter if accepted" in {
      val resp = http(req(host / "valid_parameters")
        << Map(
          "option_int" -> 3.toString,
          "require_int" -> 4.toString,
          "even_int" -> 8.toString
        )).as_string
      resp must_== "15"
    }
    "respond if optional parameters are missing" in {
      val resp = http(req(host / "valid_parameters")
        << Map(
          "require_int" -> 4.toString,
          "even_int" -> 8.toString
        )).as_string
      resp must_== "12"
    }
    "fail if even format is wrong" in {
      val resp = httpx(req(host / "valid_parameters")
        << Map(
          "require_int" -> 4.toString,
          "even_int" -> 7.toString
        ))
      resp.code must_== 400
      resp.as_string must_== "even_int is not even: 7"
    }
    "fail if int format is wrong" in {
      val resp = httpx(req(host / "valid_parameters")
        << Map(
          "require_int" -> 4.toString,
          "even_int" -> "eight"
        ))
      resp.code must_== 400
      resp.as_string must_== "even_int is not an int: eight"
    }
    "fail if required parameter is missing" in {
      val resp = httpx(req(host / "valid_parameters")
        << Map(
          "require_int" -> 4.toString
        ))
      resp.code must_== 400
      resp.as_string must_== "even_int is missing"
    }
  }
  "Directive independent parameters" should {
    "respond with parameter if accepted" in {
      val resp = http(req(host / "independent_parameters")
        << Map(
          "option_int" -> 3.toString,
          "require_int" -> 4.toString,
          "even_int" -> 8.toString
        )).as_string
      resp must_== "15"
    }
    "respond with all errors" in {
      val resp = httpx(req(host / "independent_parameters")
        << Map(
          "option_int" -> "four",
          "even_int" -> 7.toString
        ))
      resp.code must_== 400
      resp.as_string must_== Seq("option_int is not an int: four", "require_int is missing", "even_int is not even: 7").mkString("\n")
    }
  }
}
