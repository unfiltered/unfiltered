Asyncrazy Temperature Server
------------------------------

Putting this all together, we can build a server that would very
efficiently get your IP address banned by Google if you actually put
it on the web. But it's totally cool to run it locally. We think.


```scala
import dispatch._
import unfiltered.request._
import unfiltered.response._
import unfiltered.netty._

object Location extends 
Params.Extract("location", Params.first ~> Params.nonempty)

object Temperature extends async.Plan with ServerErrorResponse {
  val http = new nio.Http
  def intent = {
    case req @ GET(_) =>
      req.respond(view("", None))
    case req @ POST(Params(Location(loc))) =>
      http(:/("www.google.com") / "ig/api" <<? Map(
        "weather" -> loc) <> { reply =>
          val tempC = for {
            elem <- reply \\\\ "temp_c"
            attr <- elem.attribute("data") 
          } yield attr.toString
          req.respond(view(loc, tempC.headOption))
        }
      )
  }
  def view(loc: String, temp: Option[String]) = Html(
    <html>
      <body>
        <form method="POST">
          Location:
          <input value={loc} name="location" />
          <input type="submit" />
        </form>
        { temp.map { t => <p>It's {t}°C in {loc}!</p> }.toSeq }
      </body>
    </html>
  )
}
```

Put *all that* into a console, then start it:

```scala
Http(8080).chunked(1048576).plan(Temperature).run()
```

You can lookup the current temperature for almost anywhere; just type
in a place name or postal code and Google will probably figure it out.

When you are done checking the temperature of exciting places around
the world, shutdown the handler's Dispatch executor.

```scala
Temperature.http.shutdown()
```
