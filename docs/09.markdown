Silly Store
-----------

### Opening the Store

Using the request matchers and response functions outlined over the
last couple of pages, we have everything we need to build a naive
key-value store.

```scala
import unfiltered.request._
import unfiltered.response._

object SillyStore extends unfiltered.filter.Plan {
  @volatile private var store = Map.empty[String, Array[Byte]]
  def intent = {
    case req @ Path(Seg("record" :: id :: Nil)) => req match {
      case GET(_) =>
        store.get(id).map(ResponseBytes).getOrElse {
          NotFound ~> ResponseString("No record: " + id)
        }
      case PUT(_) =>
        SillyStore.synchronized {
          store = store + (id -> Body.bytes(req))
        }
        Created ~> ResponseString("Created record: " + id)
      case _ =>
        MethodNotAllowed ~> ResponseString("Must be GET or PUT")
    }
  }
}
```

Go ahead and paste that into a [console](Try+Unfiltered.html). Then,
execute the plan with a server, adjusting the port if your system does
not have 8080 available.

```scala
unfiltered.jetty.Http.local(8080).filter(SillyStore).run()
```

The method `local`, like `anylocal`, binds only to the loopback
interface, for safety. SillyStore is not quite "web-scale".

### Curling the Store

The command line utility [cURL][curl] is great for testing HTTP
servers. First, we'll try to retrieve a record.

[curl]: http://curl.haxx.se/

    curl -i http://127.0.0.1:8080/record/my+file

The `-i` tells it to print out the response headers. Curl does a GET
by default; since there is no record by that or any other name it
prints out the 404 response with our error message. We have to PUT
something into storage.

    echo "Ta daa" | curl -i http://127.0.0.1:8080/record/my+file -T -

Curl's option `-T` is for uploading files with a PUT, and the hyphen
tells it to read the data piped in from echo. Now, we should have
better luck with a GET request:

    curl -i http://127.0.0.1:8080/record/my+file

That worked, right? We should also be able to replace items:

    echo "Ta daa 2" | curl -i http://127.0.0.1:8080/record/my+file -T -
    curl -i http://127.0.0.1:8080/record/my+file

And lastly, test the method error message:

    curl -i http://127.0.0.1:8080/record/my+file -X DELETE

405 Method Not Allowed. But it's a shame, really. DELETE support would
be easy to add. Why don't you give it a try?
