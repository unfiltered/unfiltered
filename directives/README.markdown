Unfiltered-Directives
=====================

A monadic api for [Unfiltered](http://github.com/unfiltered/unfiltered) demonstrated at [NEScala 2013](http://nescala.org/)

Download / Use
========
Unfiltered-Directives is published to maven central for scala versions "2.9.1-1", "2.9.2" and "2.10.0"

    libraryDependencies += "com.jteigen" %% "unfiltered-directives" % "0.1"

Why ?
=====
Writing well behaved http applications is hard. You are often left with the choice between readable code and a well behaved application.
Unfiltered-Directives is an attempt at making it simpler to write readable and well behaved applications.
Its design is influenced by (parser-combinators)[https://github.com/scala/scala/blob/master/src/library/scala/util/parsing/combinator/Parsers.scala],
but unlike the parser combinators, it avoids the heavy use of operators.

1 - Good code, Bad HTTP
-----------------------
While this code is very readable and simple to understand, it's hard to use.
If the client doesn't get everything right the result will be a 404 (not found) - making it hard for the client to understand whats wrong

    def intent = {
      case req @ POST(Path("/example"))) & Accepts.Json(RequestContentType("application/json")) =>
        Ok ~> JsonContent ~> ResponseBytes(Body.bytes(req))
    }

2 - Bad code, Good HTTP
-----------------------
A better implementation is something like this, where the client will be given a reasonable status indicating whats wrong.
But it's less readable, and if you have multiple resources you will quickly find yourself copy-pasting a lot of that behaviour
between your resources

    def intent = {
      case req @ Path("/example") => req match {
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

3 - Good code, Good HTTP
------------------------
Unfiltered-Directives provides a nice api for providing reasonable HTTP behaviour, while allowing you to write
good and readable code. The following example behaves exactly like the previous example, but is much more usable

    import directives._, Directives._

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

4 - Reuse
---------
Directives are composable and simple to reuse. This example demonstrates how you can build and reuse a session based directive requiring users to login.
 

	import directives._, Directives._
	import javax.servlet.http.HttpServletRequest

	case class User(name:String)

    // depending on the underlying request directive, providing the HttpSession
	def session = underlying[HttpServletRequest].map(_.getSession)

    // depending on the session directive, getting the User from session, orElse redirecting user to the `"/login"` page
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
	
	  case "/mypage" =>
	    for {
		  _ <- POST
		  u <- user
	    } yield doStuffWithUser(u)
	}

Runnable examples are available under `src/test/scala`

Licence
-----
MIT licenced (same licence as Unfiltered)