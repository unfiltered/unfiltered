Going Asynchronous
------------------

While the `cycle.Plan` gives us the means to implement a traditional
request-response cycle, with unfiltered-netty you also have the
option to respond asynchronously to the request. When used with other
asynchronous libraries, this can result in more efficient use of
threads and support for more simultaneous connections.

### Other Asynchronous Libraries

Luckily, the "nettyplayin" project created in the last few pages
already depends on a second asynchronous library, dispatch-nio, which
acts as client for HTTP requests to other services. Using an
`async.Plan` with `dispatch.nio.Http`, we can query other HTTP
services to satisfy a request without hoarding a thread for the many
milliseconds it could take to perform that request.

### Always Sunny in...

Google has a [secret][weather] weather API. Let's use that until
they take it offline.

[weather]: http://blog.programmableweb.com/2010/02/08/googles-secret-weather-api/

```scala
import dispatch._
val h = new nio.Http
def weather(loc: String) =
  :/("www.google.com") / "ig/api" <<? Map(
    "weather" -> loc)
```

Paste that into a console, then you can print the response like this:

```scala
h(weather("San+Francisco") >- { x => println(x) })
```

You may notice that the prompt for the next command appears before
the response is printed. It's working!

### Taking the Temperature

Now all we have to do is consume this service from a server.

```scala
import unfiltered.response._
import unfiltered.netty._

val temp = async.Planify {
  case req =>
    h(weather("San+Francisco") <> { reply =>
      val tempC = (reply \\\\ "temp_c").headOption.flatMap {
        _.attribute("data") 
      }.getOrElse("unknown")
      req.respond(PlainTextContent ~>
                  ResponseString(tempC + "°C"))
    })
}

Http(8080).plan(temp).run()
```

Pasting all that into a console should start up a server that always
gives you the temperature in San Francisco (the closest city by that
name to Google's headquarters, anyway). When you are done with this
hardcoded showpiece, shut down the Dispatch executor it was using so we
can move on real deal.

```scala
h.shutdown()
```
