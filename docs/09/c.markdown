## Chunked Requests

If you're handling HTTP POSTs, it's very possible that your browser or
non-browser clients will send requests using
[chunked transfer encoding][chunked]. This part of HTTP 1.1 is great
for keeping messages short, but not so great for short short and
simple NIO servers.

[chunked]: https://en.wikipedia.org/wiki/Chunked_transfer_encoding

### An Inconvenient Party Line

The challenge for your server, as it is handling many concurrent
requests, is to maintain the state of a message that is split into
many chunks. For example if the request body contains url-encoded
parameters, you must assemble all the chunks together to have uniform
access to any parameter.

Netty provides a simple solution for cases like this: its
[HttpChunkAggregator][agg] assembles chunks into a single message. The
caveat is that the full message is necessarily loaded into memory, and
it could be arbitrarily large. The interface therefore requires you to
chose a limit for the aggregated message size; any chunked request
that exceeds this limit will raise a TooLongFrameException.

Unfiltered's Netty server-builder provides a convenient `chunked`
interface for adding aggregating handlers to your pipeline:

[agg]: http://docs.jboss.org/netty/3.2/api/org/jboss/netty/handler/codec/http/HttpChunkAggregator.html

```scala
unfiltered.netty.Http(8080).chunked(1048576).plan(hello).run()
```

If you want quick, easy, and limited support for chunked requests,
don't forget to call this method on your server builder.
