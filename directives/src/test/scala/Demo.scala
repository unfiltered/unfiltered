import org.eclipse.jetty.server.session.SessionHandler
import unfiltered.filter.Plan
import unfiltered.filter.request.ContextPath
import unfiltered.jetty.Http
import unfiltered.request._
import unfiltered.response._

trait Demo extends Plan with App {
  Http(8080).filter(this).run()
}

// curl -v http://localhost:8080/example/x
// 405 - method not allowed

// curl -v -XPOST http://localhost:8080/example/x
// 415 - unsupported media type

// curl -v -XPOST http://localhost:8080/example/x -H "Content-Type:application/json" -d '{ "x" : 5 }'
// 406 - not acceptable

// curl -v -XPOST http://localhost:8080/example/x -H "Content-Type:application/json" -d '{ "x" : 5 }' -H "Accept:application/json"

object Demo0 extends Demo {
  // good code, bad http

  def intent = {
    case req @ POST(Path(Seg(List("example", id)))) & Accepts.Json(RequestContentType("application/json")) =>
      Ok ~> JsonContent ~> ResponseBytes(Body.bytes(req))
  }
}

object Demo1 extends Demo {
  // bad code, good http
  def intent = {
    case req @ Path(Seg(List("example", id))) => req match {
      case POST(_) => req match {
        case RequestContentType("application/json") => req match {
          case Accepts.Json(_) =>
            Ok ~> JsonContent ~> ResponseBytes(Body.bytes(req))
          case _ => NotAcceptable
        }
        case _ => UnsupportedMediaType
      }
      case _ => MethodNotAllowed
    }
  }
}

object Demo2 extends App {
  Http(8080).filter(new DemoPlan2).run()
}

class DemoPlan2 extends Plan {
  // good code, good http
  import unfiltered.directives._, Directives._

  // it's simple to define your own directives
  def contentType(tpe:String) =
    when{ case RequestContentType(`tpe`) => } orElse UnsupportedMediaType

  def intent = Path.Intent {
    case Seg(List("example", id)) =>
      for {
        _ <- POST
        _ <- contentType("application/json")
        _ <- Accepts.Json
        r <- request[Any]
      } yield Ok ~> JsonContent ~> ResponseBytes(Body bytes r)
  }
}

object DemoPlan2_1 extends App {
  Http(8080).filter(new DemoPlan2_1).run()
}

class DemoPlan2_1 extends Plan {
  import unfiltered.directives._, Directives._

  // existing types can be decoratet ( Eq, Gt and Lt )
  implicit val contentType = Directive.Eq{ (R:RequestContentType.type, value:String) =>
    when{ case R(`value`) => value } orElse UnsupportedMediaType
  }

  def intent = Path.Intent {
    case Seg(List("example", id)) =>
      for {
        _ <- POST
        _ <- RequestContentType === "application/json" // <-- look at the awesome syntax
        _ <- Accepts.Json
        r <- request[Any]
      } yield Ok ~> JsonContent ~> ResponseBytes(Body bytes r)
  }
}

object Demo3 extends App {
  val http = Http(8080).filter(new DemoPlan3)
  http.current.setSessionHandler(new SessionHandler)
  http.run()
}

class DemoPlan3 extends Plan {
  import unfiltered.directives._, Directives._
  import javax.servlet.http.HttpServletRequest

  val Intent = Directive.Intent[HttpServletRequest, String]{ case ContextPath(_, path) => path }

  case class User(name:String)

  def session = underlying[HttpServletRequest].map{ _.getSession }

  def user = session.flatMap{ s =>
    val u = Option(s.getAttribute("user")).map(_.asInstanceOf[User])
    getOrElse(u, Redirect("/login"))
  }

  def intent = Intent {
    case "/" =>
      for {
        _ <- GET
        u <- user
      } yield Html5(<h1>Hi {u.name}</h1>)

    case "/login" =>
      val get = for{ _ <- GET } yield
        Html5(
          <form action={"/login"} method="post">
            <input type="text" name="user"/>
            <input type="submit" value="login"/>
          </form>)

      // curl -v http://localhost:8080/login -XPOST
      object userParam extends Params.Extract("user", Params.first)

      val post = for{
        _    <- POST
        name <- userParam.fail ~> ResponseString("user required")
        s    <- session
      } yield {
        s.setAttribute("user", User(name))
        Redirect("/")
      }

      get | post
  }
}
