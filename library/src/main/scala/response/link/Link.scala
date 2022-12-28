package unfiltered.response.link

import unfiltered.response.{HttpResponse, Responder}

/** Link header implementation as specified in
    [[https://www.rfc-editor.org/rfc/rfc5988 rfc5988]]. */
object Link {
  def apply(refs: Ref*): Responder[Any] =
    (res: HttpResponse[Any]) => {
      res.header("Link", Ref.refClauses(refs: _*))
    }
}
