package unfiltered

import unfiltered.request.HttpRequest
import unfiltered.response.{ResponseFunction,HttpResponse,Pass}

object Unfiltered {
  /** An intent is a partial function for handling requests.
   * It is abstract from any server implementation. For concrete
   * request handling, see the Plan type in any binding module. */
  type Intent[T] = PartialFunction[HttpRequest[T], ResponseFunction]
}

/** Tries to apply an intent, returns Pass if no matching intent.
 * Does not use isDefined as that tests pattern matching expressions which
 * may (sadly!) have side effects. This trait may be useful in a Plan. */
trait PassingIntent[A] {
  def intent: Unfiltered.Intent[A]
  def attempt[B](request: HttpRequest[A]) =
    (try {
      intent(request)
    } catch {
      case m: MatchError => Pass
    }) 
}
