Let's wrap this up, Ada
-----------------------

We've seen how to use directives in general, and how to use
interpreters to make our own parameter directives, and even how to
make our own interpreters. Our closing example will be one that you've
had with you all along: the default Unfiltered giter8 template.

### Setting up the Template

If you don't have the template project already, skip
[back to the beginning][try] and follow the directions up until the
*Consoled* section.

[try]: Try+Unfiltered.html

Instead of entering a Scala console this time, we want to examine and
use the Scala sources included in the project. Go ahead and compile
and run them:

```sh
sbt run
```

The main class in the project will start an HTTP server on some
available port, and it will attempt to open the root document on that
server in your default browser. If you see a big ugly form, you're
all set.

### Understanding the Source

Take a look at the source for the server, located here:

    src/main/scala/Example.scala

You should be able to understand most of what's happening, but there
are a few things worth mentioning. Unlike previous examples in this
chapter, this is a web server intended for browser clients. Our output
is HTML and our input for the POST is a form serialized by the browser.

A simple `view` function is defined for displaying the page in both
its initial, error, and success states. The function takes a map of
parameters so that it can serve back the form inputs exactly as they
were submitted, without being affected by any interpreters. We can get
this separately from our parameter directives, using the trusty old
`Params` extractor.

> Since we're just calling out snippets of the code, you won't be able
  to copy and paste these into a console as you can with most sections
  of this documentation. Feel free to play around with the template
  project source though, that's what it's there for.

```scala
    case POST(Params(params)) =>
      case class BadParam(msg: String)
      extends ResponseJoiner(msg)( messages =>
          view(params)(<ul>{
            for (message <- messages)
            yield <li>{message}</li>
          }</ul>)
      )
```

Also worth noting is that we defined our `BadParam` case class inside
the match expression, since it needs a reference to `params` to build
its error response. Another option would have been to take the
`params` as a constructor parameter for the class.

Finally, since we're dealing with user input via a serialized form
rather than a programmatic web service, our expectations for errors
are different. An "empty" field in the form is still submitted as a
parameter -- an empty string. Since this will be a common user error,
we should handle it much like we would if the parameter were not
submitted at all.

```scala
val inputString = data.as.String ~>
  data.as.String.trimmed ~>
  data.as.String.nonEmpty.fail(
    (key, _) => BadParam(s"\$key is empty")
  )
```

But of course, we still need to define a `required` function since it
is possible that some client will fail to submit a parameter.

```scala
implicit def required[T] = data.Requiring[T].fail(name =>
  BadParam(name + " is missing")
)
```

Finally, in this case the code keeps the logic of the conditional
interpreter separate from the implementation, which is inline.

```scala
(inputString ~> data.Conditional(palindrome).fail(
  (_, value) => BadParam(s"'\$value' is not a palindrome")
) ~> required named "palindrome")
...
def palindrome(s: String) = s.toLowerCase.reverse == s.toLowerCase
```

This is just to show the variety of what's possible, it's up to you to
decide how to organize and apply your own interpreters. Good luck, and
don't be shy about contributing back interpreters of standard library
types back into the Unfiltered directives source!
