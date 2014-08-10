Interpreter Reuse and Implicits
-------------------------------

### Explicit Interpreters

In a non-trival application you are likely to have more than one
parameter of one method that expects an integer. Instead of defining
interpreters inline, as in previous examples, you can define a general
interpreter once and use it many places.

```scala
import unfiltered.request._
import unfiltered.response._
import unfiltered.directives._, Directives._

val intValue = data.as.Int.fail { (k,v) =>
  BadRequest ~> ResponseString(
    s"'\$v' is not a valid int for \$k"
  )
}

unfiltered.jetty.Server(8080).plan(
  unfiltered.filter.Planify { Directive.Intent {
    case Path("/") =>
      for {
        a <- intValue named "a"
        b <- intValue named "b"
      } yield ResponseString(
        (a ++ b).sum + "\n"
      )
  } }
).run()
```

In the example above we explicitly reference and apply a single
interpreter to parameters "a" and "b", responding with their sum.

> Unclear on the summing step? Values `a` and `b` are both of the
  iterable type `Option[Int]`. We join these with `++` forming a
  sequence of integers that could be as long as 2 or as short as 0,
  depending on the input. The `sum` method on this sequence does what
  you'd expect, and finally we join with a string.

### Implicit Interpreters

Referencing an interpreter was fairly tidy operation, but as we'll be
using interpreters often and in different applications, naming and
recalling names for various types could become tedious. Let's try it
with an implicit.

```scala
implicit val implyIntValue =
  data.as.String ~> data.as.Int.fail { (k,v) =>
    BadRequest ~> ResponseString(
      s"'\$v' is not a valid int for \$k"
    )
  }

unfiltered.jetty.Server(8080).plan(
  unfiltered.filter.Planify { Directive.Intent {
    case Path("/") =>
      for {
        a <- data.as.Option[Int] named "a"
        b <- data.as.Option[Int] named "b"
      } yield ResponseString(
        (a ++ b).sum + "\n"
      )
  } }
).run()
```

The first thing you may notice is that `implyIntValue` is a bit
wordier than its predecessor. An implicit interpreter used for request
parameters **must interpret from Seq[String]**. Think of it as a full
interpretation from the input format to the output type. You may
define these for any output types you want, and import them into scope
wherever you want to use them. Interpreters like response functions
can be chained with `~>`, and so `data.as.String` is typically used at
the beginning of an interpreter to be used implicit.

If it seems like extra work to define implicit interpreters, keep
reading. The payoff comes with required parameters.
