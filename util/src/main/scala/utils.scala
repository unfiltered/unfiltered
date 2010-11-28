package unfiltered.util

object Port {
  /** Finds any available port and returns it */
  def any = {
    val s = new java.net.ServerSocket(0)
    val p = s.getLocalPort
    s.close()
    p
  }
}

object Browser {
  /** Tries to open a browser window, returns Some(exception) on failure */
  def open(loc: String) =
    try {
      import java.net.URI
      val dsk = Class.forName("java.awt.Desktop")
      dsk.getMethod("browse", classOf[URI]).invoke(
        dsk.getMethod("getDesktop").invoke(null), new URI(loc)
      )
      None
    } catch { case e => Some(e) }
}

/** Shim for what scala 2.8 Option#apply does so 2.7 can play too */
object NonNull {
  def apply[T](x: T) = if(x == null) None else Some(x)
  def unapply[T](x: T) = apply(x)
}
