package unfiltered.util

import scala.util.control.Exception.allCatch
import unfiltered.util.control.NonFatal

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
  /** Tries to open a web browser session, returns Some(exception) on failure */
  def open(loc: String) =
    util.Try(
      java.awt.Desktop.getDesktop.browse(new java.net.URI(loc))
    ).toOption
}

/** Extractors that match on strings that can be converted to types. */
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
