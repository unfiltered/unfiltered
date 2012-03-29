package unfiltered.util

import scala.util.control.Exception.allCatch

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

/** Extractors that match on strings that can be converted to types */
object Of {
  object Int {
    def unapply(str: String) = allCatch.opt { str.toInt }
  }
  object Long {
    def unapply(str: String) = allCatch.opt { str.toLong }
  }
  object Float {
    def unapply(str: String) = allCatch.opt { str.toFloat }
  }
  object Double {
    def unapply(str: String) = allCatch.opt { str.toDouble }
  }
}

/** not supporting Scala 2.7 any more */
@deprecated("use Option.apply") object Optional {
  def apply[T](x: T) = if(x == null) None else Some(x)
  def unapply[T](x: T) = apply(x)
}
