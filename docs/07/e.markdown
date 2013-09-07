Independent Failure
-------------------

A request that is mapped through a directive can produce either a
failure or success response. Usually, this works the way you
want. When a `PUT` request is received for an endpoint that only
supports `POST`, the service can respond with a `405 MethodNotAllowed`
status without inspecting parameters any request parameters.

But among the request parameters themselves, it may be desirable to
respond with multiple error messages when there are multiple
parameters in error. This can be accomplished by combining directives
into a single directive which knows how to bundle the error responses.

### Normalizing Error Responses

Even if you don't intend to bundle errors right away, it's a good idea
to generate error responses in a consistent way. This allows you to
factor out the status code generation and to put error messages in
context. One way to do this is with a case class.

```scala
import unfiltered.request._
import unfiltered.response._
import unfiltered.directives._, Directives._

case class OneBadParam(msg: String) extends Responder[Any] {
  def respond(res: HttpResponse[Any]): Unit = 
    (BadRequest ~> ResponseString(msg + "\n"))(res)
}
```

We could use this class with a "required" function.

```scala
implicit def required[T] = data.Requiring[T].fail(name => 
  OneBadParam(name + " is missing")
)
```

This cuts out a bit of boiler plate, but things get more interesting
when we define a smarter case class.

### Joinable Responses

If we want Unfiltered to combine error responses from multiple
directives, we need to specify exactly how that should work. This can
be done with a simple variation of the case class defined above.

```scala
case class BadParam(msg: String) extends ResponseJoiner(msg)(
  msgs =>
    BadRequest ~> ResponseString(msgs.mkString("","\n","\n"))
)
```

Instances of this class are still defined for a single error message
`msg`, but they know how to format a response for multiple messages of
the same type. This allows the toolkit to combine many BadParam
instances into a single error response, using the response function
defined on any one of the instances.

> The type system guarantees that error messages are of the same type
  and that any instance can produce a response from them, but it is
  not guaranteed which instance's error handler will be used to
  produce the error response. You should use the same case class, or
  the same error handling function, for all of your response error
  instances.

We can redefine "required" with this improved error responder.

```scala
implicit def required[T] = data.Requiring[T].fail(name => 
  BadParam(name + " is missing")
)
```

### Joining and Splitting Directives

Now that we have joinable error responses issued from our required
interpreter, we can use the `&` method of `Directive` to join them, as
well as an unapply method of `unfiltered.request.&` to split them.

```scala
unfiltered.jetty.Http(8080).filter(
  unfiltered.filter.Planify { Directive.Intent {
    case Path("/") =>
      for {
        (a & b & c) <-
          (data.as.Required[String] named "a") &
          (data.as.Required[String] named "b") &
          (data.as.Required[String] named "c")
      } yield ResponseString(
        s"a: \$a b: \$b c: \$c"
      )
  } }
).run()
```

In a failure case, the errors objects are combined and returned on
separate lines. On success, the combined directive produces nested
tuples of the success cases which `&` extracts in the order produced.

```sh
\$ curl http://127.0.0.1:8080/
a is missing
b is missing
c is missing
```
