Directive Intent
----------------

Within an Unfiltered plan, an intent function maps from request
objects to response functions. With directives, you define a partial
function mapping from requests to a `Result`. We compose this function
with another, which produces the standard intent function understood
by Unfiltered.

> Directives are an example of an Unfiltered
> <a href="Just+Kitting.html">kit</a>, set of tools providing a higher
> level of abstraction over the core library.


### Selective enforcement

You can define a directive function using familiar request
extractors. Let's start with a raw intent function.

```scala
import unfiltered.request._
import unfiltered.response._

val Simple = unfiltered.filter.Planify {
  case Path("/") & Accepts.Json(_) =>
    JsonContent ~> ResponseString("""{ "response": "Ok" }""")
}
unfiltered.jetty.Server(8080).plan(Simple).run()
```

You can use curl to inspect the different responses:

```sh
curl -v http://localhost:42490/ -H "Accept: application/json"
curl -v http://localhost:42490/
```

The 404 response page to the second request is not so great. With
directives, we'll do better than that *by default*.

```scala
import unfiltered.directives._, Directives._

val Smart = unfiltered.filter.Planify { Directive.Intent {
  case Path("/") =>
    for {
      _ <- Accepts.Json
    } yield JsonContent ~> ResponseString("""{ "response": "Ok" }""")
} }
unfiltered.jetty.Server(8080).plan(Smart).run()
```

And with that you'll see a 406 Not Acceptable response when appropriate.

Directives results are typically, but not necessarily, defined in a
single for expression. In the expression above we discard the result
value of the `Accepts.Json` directive, as we did previously with the
extractor, and map it to our dummy response function.

The 406 error response is produced by the directive itself,
encapsulating behavior that can be used everywhere. What if you want
different error behavior? Make your own directive! (Seriously, you'll
see how on the next page.)

### One true path

You may have noticed that directives transfer (and enrich) routing
logic from extractors. If your extractors are reduced to the task of
matching against paths alone, you can even eliminate those.

```scala
val Sweet = unfiltered.filter.Planify { Directive.Intent.Path {
  case "/" =>
    for {
      _ <- Accepts.Json
    } yield JsonContent ~> ResponseString("""{ "response": "Ok" }""")
} }
unfiltered.jetty.Server(8080).plan(Sweet).run()
```

It looks pretty different, but remember that here still composes to a
standard Unfiltered intent function.
