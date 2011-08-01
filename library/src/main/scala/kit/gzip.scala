package unfiltered.kit

import unfiltered.request._
import unfiltered.response._
import unfiltered.Cycle

object GZip {
  /** Inserts ResponseFilter.GZip and GZip header into the output
   *  stream if this encoding seems to be supported by the client.
   *  The produced intent is defined for all inputs, is Pass
   *  where the given intent parameter is not defined.
   */
  def apply[A,B](intent: unfiltered.Cycle.Intent[A,B]) = {
    val completed = Cycle.Intent.complete(intent)
    Cycle.Intent[A,B] {
      case Decodes.GZip(req) => completed(req) match {
        case Pass => Pass
        case rf => ContentEncoding.GZip ~> ResponseFilter.GZip ~> rf
      }
      case req => completed(req)
    }
  }
}
