# Unfiltered

Unfiltered embraces the HTTP protocol, Scala, type-safety, and minimalism. It enables applications to handle HTTP requests as partially applied functions that take requests and return functions to respond to them. Typically, applications [pattern-match](http://en.wikipedia.org/wiki/Pattern_matching) requests against nested extractor objects. HTTP responses are defined with response [combinator functions](http://en.wikipedia.org/wiki/Combinatory_logic).

The request response cycle reduces to a pattern matching clause similar to message handling in the Scala [actors](http://www.scala-lang.org/node/242) library.

    object Server {
      def main(args: Array[String]) {
        unfiltered.jetty.Http(8080).filter(unfiltered.filter.Planify {
          case _ => Ok ~> ResponseString("Hello there")
        }).run
      }
    }

The above example starts an embedded web server at `http://localhost:8080` and responds to all requests with a "Hello there" message. It runs as a standalone Java application. You can also define filter classes, to be added by configuration to a servlet container like Tomcat or Google App Engine.

## Intents

Intents are the core library's interface for handling requests and responses. Specifically:

    type Intent[T] = PartialFunction[HttpRequest[T], ResponseFunction]

Intent functions pattern match against incoming requests and return a function that produces the desired response. Type parameters allow the client or extension library to directly access the underlying requests and response objects though the `underlying` method of `HttpRequest` and `HttpResponse`.

## Plans

Plans assign an Intent to particular request and response
bindings. For example, the trait `unfiltered.filter.Plan` defines a
`javax.servlet.Filter` that delegates to its`intent` method. The class `unfiltered.netty.Plan` defines a channel handler similarly. A future Plan trait might define a Servlet. Plan is a convention (and not currently a common interface) to apply an Intent to any request handling framework.

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

These response functions are the expected return values of Intents.

A restful api for a given resource might look something like

    unfiltered.filter.Planify {
      case GET(Path(Seg("resource" :: id :: Nil), r)) => Store(id) match {
        case Some(resource) => ResponseString(render(resource).as(r match {
          case Accepts.Json(_) => 'json
          case Accepts.Xml(_) => 'xml
          case _ => 'html
        }) ~> Ok 
        case _ => NotFound
      }
      case POST(Path(Seg("resource" :: id :: Nil), Bytes(body, r))) => Store(id, body) match {
        case Some(id) => ResponseString(render("resource created").as(r match {
          case Accepts.Json(_) => 'json
          case Accepts.Xml(_) => 'xml
          case _ => 'html
        }) ~> Created
        case _ => BadRequest
      }
      case PUT(Path(Seg("resource" :: id :: Nil), Bytes(body, r))) => Store(id, body) match {
        case Some(id) => ResponseString(render("resource updated").as(r match {
          case Accepts.Json(_) => 'json
          case Accepts.Xml(_) => 'xml
          case _ => 'html
        }) ~> Ok
        case _ => BadRequest
      }
      case DELETE(Path(Seg("resource" :: id :: Nil),_)) => Store.delete(id) match {
        case Some(id) => Ok
        case _ => Gone
      }
    }


## Modules

### library

The core application library for Unfiltered. This module provides interfaces and implementations of core request extractors and response combinators.

### filter

Binds the core library to filters in the servlet 2.3 API.

### jetty

Provides an embedded web server abstraction for serving filters.

### jetty-ajp

An embedded server that adheres to the ajp protocol.

### netty

Binds the core library to a Netty channel handler and provides an embedded server.

### spec

Provides helpers for testing Intents with [specs](http://code.google.com/p/specs/).

### uploads

Provides extractors for multipart posts using the servlet API.

### json

Provides extractors for working with jsonp and transforming json request bodies.

### scalate

[Scalate][scalate] template support.

[scalate]: http://scalate.fusesource.org/

### websockets

A minimal server websocket interface build on netty

## Install

Unfiltered is a [cross built](http://code.google.com/p/simple-build-tool/wiki/CrossBuild) project, currently for the following Scala versions

    2.7.7, 2.8.0, 2.8.1.RC1
    
### via sbt

For standalone projects,  you'll want `unfiltered-jetty` as well as a
binding module:

    import sbt._
    class Project(info) extends DefaultProject(info) {
      val uf = "net.databinder" %% "unfiltered-jetty" % "0.2.0"
      val uf = "net.databinder" %% "unfiltered-filter" % "0.2.0"
    }
    
To specify individual modules, specify the module name in the dependency.

    import sbt._
    class Project(info) extends DefaultProject(info) {
      val ufx = "net.databinder" %% "unfiltered-{module}" % "0.2.0"
    }
    
See the [template](http://github.com/n8han/Unfiltered/tree/master/demo/) application for an example of a basic Unfiltered application.

## Community

Join the [Unfiltered mailing list on Nabble](http://databinder.3617998.n2.nabble.com/Unfiltered-f5560764.html).

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

The work around is good software design. Break up your problems into parts and put them in separate filters. Don't give one Plan too much responsibility.
