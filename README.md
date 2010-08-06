# Unfiltered

Unfiltered embraces the HTTP protocol, Scala, type-safety, and minimalism. It enables applications to handle HTTP requests as partially applied functions that take requests and return functions to respond to them. Typically, applications [pattern-match](http://en.wikipedia.org/wiki/Pattern_matching) requests against nested extractor objects. HTTP responses are defined with response [combinator functions](http://en.wikipedia.org/wiki/Combinatory_logic).

The request response cycle reduces to a pattern matching clause similar to message handling in the Scala [actors](http://www.scala-lang.org/node/242) library.

    object Server {
      def main(args: Array[String]) {
        unfilered.server.Http(8080).filter(unfilered.Planify {
          case _ => Ok ~> ResponseString("Hello there")
        })
      }
    }

The above example starts an embedded web server at `http://localhost:8080` and responds to all requests with a "Hello there" message. It runs as a standalone Java application. You can also define filter classes, to be added by configuration to a servlet container like Tomcat or Google App Engine.

## Plans

Plans are the core client interface for intercepting and requests. Plans pattern match on requests and return response functions.

## Request Extractors

A request extractor is an extractor that accepts an HTTP request and returns a tuple of something useful along with the request to chain other extractors with.

An example signature would might be

    def unapply(x: HttpServletRequest): (Y, HttpServletRequest)
    
Unfiltered provides a library of extractors for matching common most HTTP requests attributes.

At the most basic level...

    GET, POST, PUT, DELETE, HEAD // match request methods
    
    Path // matches request uris
    
    Seg // matches request path elements
    
You can compose your own patterns using these and other extractors:

    PUT(Path(Seg("a" :: b :: "c" :: d :: Nil), SomeOtherExactor(foo, request)))


## Response Combinators

A response combinator is a function that takes a another function as its argument. Using these combinators, applications compose a function that acts on the Java servlet primitives to respond to the request. In general, applications employ these built-in combinators to act on primitives rather than referencing them directly, but they are free to construct responses ad hoc if necessary.

    type ResponseFunction = HttpServletRequest => HttpServletRequest
    
Core response functions are implemented as responders which can be chained together with `~>`.

These response functions are the expected return values of `Plans`.

A restful api for a given resource might look something like

    unfiltered.Planify {
      case GET(Path(Seg("resource" :: id :: Nil), Accepts(fmt, _))) => Store(id) match {
        case Some(resource) => Ok ~> ResponseString(render(resource).as(fmt))
        case _ => NotFound
      }
      case POST(Path(Seg("resource" :: id :: Nil), Bytes(body, _))) => Store(id, body) match {
        case Some(id) => Created ~> ResponseString("resource created")
        case _ => BadRequest
      }
      case PUT(Path(Seg("resource" :: id :: Nil), Bytes(body, _))) => Store(id, body) match {
        case Some(id) => Ok ~> ResponseString("resource updated")
        case _ => BadRequest
      }
      case DELETE(Path(Seg("resource" :: id :: Nil),_)) => Store.delete(id) match {
        case Some(id) => Ok ~> ResponseString("resource deleted")
        case _ => Gone
      }
    }


## Modules

### ajp-server

An embedded server that adheres to the ajp protocol.

### demo

An example Unfiltered application that demonstrates some of the core features.

### library

The core application library for Unfiltered. This module provides interfaces and implementations of core request extractors and response combinators.

### server

Provides an embedded web server abstraction for serving filters.

### spec

Provides helpers for testing filters with [specs](http://code.google.com/p/specs/).

### uploads

Provides extractors for multipart posts.

## Install

Unfiltered is a [cross built](http://code.google.com/p/simple-build-tool/wiki/CrossBuild) project, currently for the following Scala versions

    2.8.0, 2.7.7, 2.7.6, 2.7.5
    
### via sbt

For standalone projects, including `unfiltered-server` as a dependency is sufficient.

    import sbt._
    class Project(info) extends DefaultProject(info) {
      val uf = "net.databinder" %% "unfiltered-server" % "1.3"
    }
    
To specify individual modules, specify the module name in the dependency.

    import sbt._
    class Project(info) extends DefaultProject(info) {
      val ufx = "net.databinder" %% "unfiltered-{module}" % "1.3"
    }
    
See the [demo](http://github.com/n8han/Unfiltered/tree/master/demo/) application for an example of a basic Unfiltered application.

## Troubleshooting

### overly complex extractors 
    
If you design your partially applied functions in such a way that they become overly complex you might run into the following exception
    
    Exception in thread "main" java.lang.Error: ch.epfl.lamp.fjbg.JCode$OffsetTooBigException: offset to
    o big to fit in 16 bits
      
This is an open [ticket](https://lampsvn.epfl.ch/trac/scala/ticket/1133) the the Scala library but this is not really a limitation of Scala so much as the jvm. 
  
As paulp put it
> Yes.  Like it says in the ticket, exponential space.  It is not the
> compiler which is angry, it is the jvm, which sets a hard limit on the
> size of a method.  The compiler's emotional state at that moment would
> be better characterized as pollyanna-esque.

The work around is good software design. Break up your problems into parts and put them in separate filters. Don't give one `unfiltered.Plan` too much responsibility.