package unfiltered.response.link

/** A URI-Reference as described in
    [[https://tools.ietf.org/html/rfc5988#section-5 section-5]]. While `Rel` is
    is specified as a parameter,
    [[https://tools.ietf.org/html/rfc5988#section-3 section-3]] states that it
    is required and so it is implemented as a `Ref` member. */
final case class Ref(uri: String, rel: Rel, params: Param*) {
  /** Yield "link-value" clause. */
  def refClause = s"<$uri>; $paramsClause"

  /** Yield a clause of all parameters given for a Ref. */
  def paramsClause =
    params.foldLeft(Ref.paramClause(rel)) { (a, b) =>
      a + "; " + Ref.paramClause(b)
    }
}

object Ref {
  def refClauses(rs: Ref*) = rs.map(_.refClause).mkString(", ")

  /** Yield "link-param" clause. */
  def paramClause(p: Param) =
    s"""${p.paramType.name}="${p.value}""""
}