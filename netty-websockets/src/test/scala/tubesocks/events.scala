package tubesocks

sealed trait Event
case class Message(text: String, socket: Socket) extends Event
case class Open(socket: Socket) extends Event
case class Close(socket: Socket) extends Event 
case class Error(exception: Throwable) extends Event
case class Fragment(text: String) extends Event
case class EOF(text: String) extends Event
