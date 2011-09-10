Just Kitting
------------

Unfiltered gives you many extractors to facilitate pattern matching,
and it might be helpful to create some extractors specific to an
application, but often you want to factor-out broader functionality.
That's where kits come in.

### The GZip Kit

We first had the idea for kits when considering how best to support
GZip response encodings. An extractor to match an `Accept-Encoding`
header for gzip would help, and a `ResponseFunction` to compress the
response stream, but these would have to be repeated for every case
expression.

The GZip *kit* is a higher level abstraction that can be applied at
once to full intent function. The kit will examine the request first,
and if appropriate, prepends a compressing function to the response
function chain provided by the intent.

#### GZip Kit Definition

This part is already done for you in `unfiltered-libary`, but in case
you are curious this is how the GZip kit is defined.

```scala
object GZip extends unfiltered.kit.Prepend {
  def intent = Cycle.Intent[Any,Any] {
    case Decodes.GZip(req) =>
      ContentEncoding.GZip ~> ResponseFilter.GZip
  }
}
```

Unlike a plan's intent function, this one defines the conditions for
which its response function is prepended another intent's. It sets
a header and a `FilterOutputStream` for the response.

### GZip Usage

This is a very simple plan that will compress its responses if the
user-agent supports it:

```scala
object EchoPlan extends unfiltered.filter.Plan {
  def intent = unfiltered.kit.GZip {
    case Path(path) => ResponseString(path)
  }
}
```

### Do Kit Yourself

The higher level abstraction provided by kits can be applied to
problems specific to an application just as well as for general
problems. Don't be afraid to experiment, and if you happen to make
something that does solve a general problem, please share it!
