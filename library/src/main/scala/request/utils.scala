package unfiltered.request

object & { def unapply[A](a: A) = Some(a, a) }
