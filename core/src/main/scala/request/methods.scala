package unfiltered.request

class Method(method: String) {
  def unapply[T](req: HttpRequest[T]) = 
    if (req.method.equalsIgnoreCase(method)) Some(req)
    else None
}

object GET extends Method("GET")
object POST extends Method("POST")
object PUT extends Method("PUT")
object DELETE extends Method("DELETE")
object HEAD extends Method("HEAD")
object CONNECT extends Method("CONNECT")
object OPTIONS extends Method("OPTIONS")
object TRACE extends Method("TRACE")
object PATCH extends Method("PATCH")
object LINK extends Method("LINK")
object UNLINK extends Method("UNLINK")
