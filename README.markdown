# Unfiltered

[![Join the chat at https://gitter.im/unfiltered/unfiltered](https://badges.gitter.im/unfiltered/unfiltered.svg)](https://gitter.im/unfiltered/unfiltered?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

See the [Unfiltered documentation](https://unfiltered.ws) for instructions on using the project.

## Modules

### library

The core application library for Unfiltered. This module provides interfaces and implementations of core request extractors and response combinators.

### filter

Binds the core library to filters in the servlet 3.0 API.

### filter-async

Provides asynchronous support for the filter module

### jetty

Provides an embedded web server abstraction for serving filters.

### netty

Binds the core library to a Netty channel handler and provides an embedded server.

### netty-uploads

Provides extractors for multipart posts using netty.

### specs2

Provides helpers for testing Intents with [specs2](https://etorreborre.github.io/specs2/).

### uploads

Provides extractors for multipart posts using the servlet API.

### json4s

Provides extractors for working with jsonp and transforming json request bodies.

### websockets

A minimal server websocket interface build on netty

## Community

Join the [Unfiltered mailing list on Google Groups](https://groups.google.com/g/unfiltered-scala) or view the previous list on [Nabble](http://databinder.3617998.n2.nabble.com/Unfiltered-f5560764.html).

## Example Apps

There are some [giter8](https://github.com/foundweekends/giter8) templates for Unfiltered contain a bit of example code.

- [unfiltered-netty.g8](https://github.com/unfiltered/unfiltered-netty.g8) g8 template for netty webservers
- [unfiltered-war.g8](https://github.com/unfiltered/unfiltered-war.g8) g8 template configured with sbt war plugin
- [unfiltered-websockets.g8](https://github.com/unfiltered/unfiltered-websockets.g8) g8 template for websocket based chat app
- [unfiltered.g8](https://github.com/unfiltered/unfiltered.g8) basic g8 template example

