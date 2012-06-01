# Unfiltered Netty Uploads

Provides extractors for working with multipart file uploads in your netty async and cycle plans.

*Note: This feature should be considered experimental as it currently relies on the [Netty v4](https://github.com/netty/netty) codebase.*

## Usage

Add netty-uploads to your project, for example:

```scala
libraryDependencies += "net.databinder" %% "unfiltered-netty-uploads" % "0.6.1-SNAPSHOT"
```

There are a couple of different ways you can use the multipart extractors depending on your plan structure.

### For netty async or cycle plans with a chunk aggregator

You can use the multipart extractors in your plan, you just need to import them with `import unfiltered.netty.request._`. With a chunk aggregator you'll be limited to a maximum file upload size of `Int.MaxValue` bytes (approx. 2GB).

A simple example using a cycle plan which extracts the file uploads as `DiskFileWrapper`s which can then be worked with and written to disk.

```scala
import unfiltered.request._
import unfiltered.response._
import unfiltered.netty._

import unfiltered.netty.request._

object App {
  def main(a: Array[String]) {
    Http(8080).chunked().handler(cycle.Planify{
      case POST(Path("/cycle/disk") & MultiPart(req)) =>
        val disk = MultiPartParams.Disk(req)
        (disk.files("f"), disk.params("p")) match {
          case (Seq(f, _*), p) =>
            ResponseString(
              "cycle disk read file f named %s with content type %s and param p %s" format(
                f.name, f.contentType, p))
          case _ =>  ResponseString("what's f?")
        }
    }).run
  }
}
```

If you try to use the multipart extractors in a regular netty plan without the chunk aggregator then you'll get errors.

By default the `maxContentLength` used by the chunk aggregator is `1048576` (1MB). You can pass an `Int` value to change this. For example, if you wanted to allow file uploads up to 5MB in size, you could specify:

```scala
Http(8080).chunked(5242880).handler(...)
```

### For netty async or cycle plans without a chunk aggregator

If you want to handle uploads more efficiently and/or handle uploads > 2GB then you can swap your netty async or cycle plan for a `MultiPartDecoder` plan. This means you don't need a chunk aggregator in the handler pipeline as the plan takes care of it for you.

For example:

```scala
import unfiltered.request._
import unfiltered.response._
import unfiltered.netty._

import unfiltered.netty.request._

object App {
  def main(a: Array[String]) {
    Http(8080).handler(cycle.MultiPartDecoder({
      case POST(Path("/cycle/disk") & MultiPart(req)) => {
        case Decode(binding) =>
          val disk = MultiPartParams.Disk(binding)
          (disk.files("f"), disk.params("p")) match {
            case (Seq(f, _*), p) =>
              ResponseString(
                "cycle disk read file f named %s with content type %s and param p %s" format(
                  f.name, f.contentType, p))
            case _ => ResponseString("what's f?")
          }
      }
    })).run
  }
}
```

## Extractors

Before you can work with the uploaded params and files, you must make sure that you have a POST request:

```scala
case POST(Path("/some/path")) ...
```

And that it contains some multipart encoded data:

```scala
... & MultiPart(req) => { ...
```

There are three extractors to choose from depending on the environment your app is running in and what you want to do with the uploaded files.

### MultiPartParams.Disk

Extracts uploaded files to `DiskFileWrapper`s which can be written to disk. For example:

```scala
Decode(binding) =>
  MultiPartParams.Disk(binding).files("f") match {
    case Seq(f, _*) => f.write(new java.io.File("/tmp/" + f.name))
    ... 
  }
```

You can also get the content as an `Array[Byte]` with `f.bytes`. The file name with `f.name`, size with `f.size` and content type with `f.contentType`.

### MultiPartParams.Streamed

Extracts uploaded files to `StreamedFileWrapper`s which expose a stream to read the content, as well as being able to write the content to disk.

### MultiPartParams.Memory

Extracts uploaded files to `MemoryFileWrapper`s which have the same properties as `StreamedFileWrapper`s except writing to disk is not possible. This is useful in environments such as GAE where writing to disk is prohibited.