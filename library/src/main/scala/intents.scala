package unfiltered

import unfiltered.request.HttpRequest
import unfiltered.response.{ResponseFunction,HttpResponse,Pass}

object Unfiltered {
  /** An intent is a partial function for handling requests.
   * It is abstract from any server implementation. For concrete
   * request handling, see the Plan type in any binding module. */
//  type Intent[A,B] = PartialFunction[HttpRequest[A], B]
}
