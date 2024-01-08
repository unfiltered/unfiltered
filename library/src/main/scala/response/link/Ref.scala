package unfiltered.response.link

/** A `link-value` as described in
    [[https://www.rfc-editor.org/rfc/rfc5988#section-5 section-5]]. While `Rel` is
    is specified as a parameter,
    [[https://www.rfc-editor.org/rfc/rfc5988#section-3 section-3]] states that it
    is required and so it is implemented as a `Ref` member. */
final case class Ref private (uri: String, rel: Rel, params: List[Param]) {

  /** Yield "link-value" clause. */
  def refClause: String = s"<$uri>; $paramsClause"

  /** Yield a clause of all parameters given for a Ref. */
  def paramsClause: String =
    params.foldLeft(Ref.paramClause(rel)) { (a, b) =>
      a + "; " + Ref.paramClause(b)
    }
}

object Ref {

  /** Construct a `link-value` from a URI, its relation to the resource, and
      any additional parameters as `link-param` values. */
  def apply(uri: String, rel: Rel, params: Param*): Ref = {
    val (actualRel, _, actualParams) =
      params.foldLeft((rel, Set.empty[Param.Type], List.empty[Param])) {
        case ((r, pt, ps), p: Rel) =>
          (r :+ p, pt, ps)
        case ((r, pt, ps), Param.NonRepeatable(p)) =>
          if (!pt.contains(p.paramType))
            (r, pt + p.paramType, p :: ps)
          else
            (r, pt, ps)
        case ((r, pt, ps), p) =>
          (r, pt, p :: ps)
      }
    Ref(uri, actualRel, actualParams)
  }

  def refClauses(rs: Ref*): String = rs.map(_.refClause).mkString(", ")

  /** Yield "link-param" clause. */
  def paramClause(p: Param): String =
    s"""${p.paramType.name}="${p.value}""""
}
