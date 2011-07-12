# Unfiltered Websockets

A minimal server [Websocket](http://en.wikipedia.org/wiki/WebSockets) interface for [Unfiltered](http://github.com/n8han/unfiltered#readme).

## Usage

The simplest way to embed a websocket server is within a `main` method.

    object Main {
      def main(args: Array[String]) {
        var sockets = new scala.collection.mutable.ListBuffer[WebSocket]()
        WebSocketServer("/", 8080) {
          case Open(s) => sockets += s
          case Message(s, Text(str)) => sockets foreach(_.send(str.reverse))
          case Close(s) => sockets -= s
          case Error(s, e) => println("error %s" format e.getMessage)
        }
      }
    }

Under the covers this generates a Netty handler that defines the following `PartialFunction`

    PartialFunction[netty.RequestBinding => PartialFunction[netty.websocket.SocketCallback => Unit]]

The above example is equivalent to a handler of the format

     netty.websockets.Planify({
       case GET(Path("/")) => {
         case Open(socket) => ...
         case Message(s, Text(str) => ..
         ...
       }
    })

A `WebSocketServer` responds to a PartialFunction on `SocketCallback` objects.

`SocketCallback` objects are an enumeration of the same types of functions a client would respond to.

* `Open(socket)` is fired when a browser connects the server and takes as its a web socket that can be written to
* `Message(socket, msg)` is fired when a browser sends a message to the server and takes as its arguments the socket that sent the message
 and the `Msg` itself
* `Close(socket)` is fired when a clients websocket connection is closed
* `Error(socket, err)` is fired when an exception occurred within the process of handling a websocket request and takes as its arguments the socket being served
 and the `Throwable` error that occurred

This interface doesn't require you respond to all messages.

An example of a subscription based service where clients receive msgs as they arrive from some remote source might look like

    object Main {
      def main(args: Array[String]) {
        val tweets = Twitter(Auth(...))
        WebSocketServer("/twttr", 8080) {
          case Open(s) => tweets onTweet { twt =>
            s.send(twt)
          }
        }
      }
    }

To mix in websockets in with a Netty HTTP server, use the full `Plan.Intent` function to build a `Plan` with `Planify`

    netty.Http(8080)
       .handler(netty.websockets.Planify({
          case Path("/foo") => {
             case Open(socket) =>
               socket.send("push!")
          }
        )
        // onPass overrides default connection closing on invalid websocket requests
        // and sends control flow  upstream to the next ChannelHandler
        .onPass(_.sendUpstream(_))
        .handler(netty.channel.Planify({
           case Path("/bar") =>
              ...
        })
