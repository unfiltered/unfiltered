package unfiltered.util

import scala.util.control.Exception.allCatch
import scala.util.control.NonFatal

object Port {

  /** Finds any available port and returns it */
  def any: Int = {
    val s = new java.net.ServerSocket(0)
    val p = s.getLocalPort
    s.close()
    p
  }
}

object Browser {

  /** Tries to open a web browser session, returns Some(exception) on failure */
  def open(loc: String): Option[Throwable] =
    try {
      java.awt.Desktop.getDesktop.browse(new java.net.URI(loc))
      None
    } catch {
      case NonFatal(e) => Some(e)
    }
}

/** Extractors that match on strings that can be converted to types. */
object Of {
  object Int {
    def unapply(str: String): Option[Int] = allCatch.opt { str.toInt }
  }
  object Long {
    def unapply(str: String): Option[Long] = allCatch.opt { str.toLong }
  }
  object Float {
    def unapply(str: String): Option[Float] = allCatch.opt { str.toFloat }
  }
  object Double {
    def unapply(str: String): Option[Double] = allCatch.opt { str.toDouble }
  }
}
