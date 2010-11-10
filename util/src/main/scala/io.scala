package unfiltered.util

trait IO {
  /** Manage the usage of some object that must be closed after use */
  def use[C <: { def close() }, T](c: C)(f: C => T): T = try {
    f(c)
  } finally {
    c.close()
  }
}

object IO extends IO