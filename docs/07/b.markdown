Parameters as Directives
------------------------

Any part of a request can be used to route and accept or reject the
request. Request parameters, encoded in a query string or a POST
request body, are an open field for richer requests -- and user error.
We can handle these with directives as well.

### Parameters Values

In HTTP, a parameter key can be specified multiple times with
different values. Unfiltered's base model for parameters is therefore
a string key to a sequence of string values in the order they were
supplied. This is easy to obtain in a directive.

```scala
import unfiltered.request._
import unfiltered.response._
import unfiltered.directives._, Directives._

unfiltered.jetty.Server(8080).plan(
  unfiltered.filter.Planify { Directive.Intent {
    case Path("/") =>
      for {
        in <- parameterValues("in")
      } yield ResponseString(in.toString)
  } }
).run()
```

You can try this service with multiple, one, or no parameters.

```sh
curl -v http://127.0.0.1:8080/ -d in=one -d in=two
```

Even with no parameters, the directive succeeds with an empty
sequence. Since it's not establishing control flow, we could have
accessed it just as easily through the [Params map][params]. But
typically, we do have requirements on our input.

[params]: Within+the+Parameters.html

### I'm thinking of a Number

Things get more interesting when require parameters to be in a
particular format. From here on out, we'll be working with
*interpreters* in the `unfiltered.directive.data`
package. Interpreters define abstract operations on data; we can
produce directives for a particular request parameter with `named`.

```scala
unfiltered.jetty.Server(8080).plan(
  unfiltered.filter.Planify { Directive.Intent {
    case Path("/") =>
      for {
        in <- data.as.Int named "in"
      } yield ResponseString(in.toString)
  } }
).run()
```

By testing this service you'll find that all requests to the root path
are accepted, and that the `in` value is bound to an `Option[Int]`. If
an `in` request parameter is not present or not a valid int, the value
is `None`. This is a directive, but it's still one that always
produceses a `Success` result.

> What about repeated parameters? The object `data.as.Int` is an
  interpreter from `String` to `Int`, but in HTTP we model parameter
  values as a sequence of strings. This gap is bridged by another
  interpreter `data.as.String`, which chooses the first in the
  sequence. It's applied implicitly when needed.

We can transform an interpreter that ignores failed interpretation
into one that produces a failure response by passing an error handler
to its `fail` method.

```scala
unfiltered.jetty.Server(8080).plan(
  unfiltered.filter.Planify { Directive.Intent {
    case Path("/") =>
      for {
        in <- data.as.Int.fail { (k,v) =>
          BadRequest ~> ResponseString(
            s"'\$v' is not a valid int for \$k"
          )
        } named "in"
      } yield ResponseString(in.toString)
  } }
).run()
```

The error handling function receives both the parameter name and given
value as parameters. This way, a directive used for more than one
parameter can have a specific error message for each.
