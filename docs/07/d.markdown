Required Parameters
-------------------

So far we've learned know how to define implicit interpreters from a
sequence of string to any type T and use them with
`data.as.Option[T]`. Now we'll see how to use the same interpreters
for required parameters.

### Your 'require' Function

The failure to supply a required parameter must produce an application
defined error response. We'll define a very simple one.

```scala
import unfiltered.request._
import unfiltered.response._
import unfiltered.directives._, Directives._

implicit def require[T] = data.as.Require[T].fail(name => 
  BadRequest ~> ResponseString(name + " is missing\n")
)
```

The name of the function is not important when used implicitly, but
call it `require` is a good convention since you may also want to use
it explicitly when defining interpreters inline.

With a require function in scope we can use it with any implicit
interpreters also in scope. The `data.as.String` interpreter is
imported from the `Directives` object, so we can use it immediately.

```scala
unfiltered.jetty.Http(8080).filter(
  unfiltered.filter.Planify { Directive.Intent {
    case Path("/") =>
      for {
        opt <- data.as.Option[String] named "opt"
        req <- data.as.Require[String] named "req"
      } yield ResponseString(
        s"opt: \$opt req: \$req"
      )
  } }
).run()
```

Let's examine the output from this one with curl:

```sh
\$ curl -v http://127.0.0.1:8080/ -d opt=hello -d req=world
opt: Some(hello) req: world
```

Since `req` is required to produce a success response, it's not
wrapped in `Option` or anything else; the type of the bound value is
whatever its interpreter produces.

### Using 'require' Explicitly

Most interpreters work with an option of the data so that they can be
chained together in support of both optional and required parameters.
Require is itself an interpreter which unboxes from the `Option`, so
it generally must be the last interpreter in a chain.

```scala
unfiltered.jetty.Http(8080).filter(
  unfiltered.filter.Planify { Directive.Intent {
    case Path("/") =>
      for {
        in <- data.as.BigInt ~> require named "in"
      } yield ResponseString(
        in % 10 + "\n"
      )
  } }
).run()
```

This service returns the last digit of the required provided
integer. Since we didn't provide a `fail` handler for
`data.as.BigInt`, it falls to `require` to produce a failure
response.

```sh
\$ curl http://127.0.0.1:8080/ -d in=1334534
4
\$ curl http://127.0.0.1:8080/ -d in=1334534a
in is missing
```

To be more specific, we can supply a failure to the integer
interpreter.

```scala
unfiltered.jetty.Http(8080).filter(
  unfiltered.filter.Planify { Directive.Intent {
    case Path("/") =>
      for {
        in <- data.as.BigInt.fail((k,v) => 
          BadRequest ~> ResponseString(s"'\$v' is not a valid int for \$k\n")
        ) ~> require named "in"
      } yield ResponseString(
        in % 10 + "\n"
      )
  } }
).run()
```

Now each failure condition produces a specific error.

```sh
\$ curl http://127.0.0.1:8080/ -d in=1334534a
'1334534a' is not a valid int for in
\$ curl http://127.0.0.1:8080/
in is missing
```
