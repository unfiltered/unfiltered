package unfiltered.request

abstract class Protocol(p: String) {
  def unapply[T](req: HttpRequest[T]) = 
    if (req.protocol.equalsIgnoreCase(p)) Some(req)
    else None
}
object HTTP_1_0 extends Protocol("HTTP/1.0")
object HTTP_1_1 extends Protocol("HTTP/1.1")