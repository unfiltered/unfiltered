# Unfiltered

See the the [Unfiltered documentation](http://unfiltered.databinder.net/) for instructions on using the project.

## Modules

### library

The core application library for Unfiltered. This module provides interfaces and implementations of core request extractors and response combinators.

### filter

Binds the core library to filters in the servlet 2.3 API.

### filter-async

Provides asynchronous support for the filter module

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

## Community

Join the [Unfiltered mailing list on Google Groups](http://groups.google.com/group/unfiltered-scala/topics) or view the previous list on [Nabble](http://databinder.3617998.n2.nabble.com/Unfiltered-f5560764.html).

## Example Apps

There's an in-progress example app using Unfiltered made by [klaeufer](https://github.com/klaeufer), [unfiltered-example-bookmarks](https://github.com/webservices-cs-luc-edu/unfiltered-example-bookmarks). Also, most [giter8](https://github.com/n8han/giter8) templates for Unfiltered contain a bit of example code.

- [unfiltered-netty.g8](https://github.com/n8han/unfiltered-netty.g8) g8 template for netty webservers
- [unfiltered-war.g8](https://github.com/n8han/unfiltered-war.g8) g8 template configured with sbt war plugin
- [unfiltered-websockets.g8](https://github.com/softprops/unfiltered-websockets.g8) g8 template for websocket based chat app
- [unfiltered.g8](https://github.com/softprops/unfiltered.g8) g8 template example specs tests `QParams` validators and `org.clapper.avsl.Logger` logging configuration
- [unfiltered-gae.g8](https://github.com/softprops/unfiltered-gae.g8) g8 template for google app engine deployment
- [coffee-filter.g8](https://github.com/softprops/coffee-filter.g8) g8 template of unfiltered app using coffeescript and less css
- [unfiltered-oauth-server.g8](https://github.com/softprops/unfiltered-oauth-server.g8) g8 template of example unfiltered oauth server
