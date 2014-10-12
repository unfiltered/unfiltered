package unfiltered.response.link

import unfiltered.response.{HttpResponse, Responder}

/** Link header implementation as specified in
    [[http://tools.ietf.org/html/rfc5988 rfc5988]]. */
object Link {
  def apply(refs: Ref*) =
    new Responder[Any] {
      def respond(res: HttpResponse[Any]) {
        res.header("Link", Ref.refClauses(refs:_*))
      }
    }
}