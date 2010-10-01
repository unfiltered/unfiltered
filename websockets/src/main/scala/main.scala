package unfiltered.websockets

/** just a tester. static html client can be found in test/resources/client.html */
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