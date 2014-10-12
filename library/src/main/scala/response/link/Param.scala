package unfiltered.response.link

abstract class Param(val paramType: Param.Type, val value: String)

object Param {
  /** Parameter types as specified in
      http://tools.ietf.org/html/rfc5988#section-5. */
  sealed abstract class Type(val name: String) {
    override def toString = s"Type($name)"
  }
  case object Rel extends Type("rel")
  case object Anchor extends Type("anchor")
  case object Rev extends Type("rev")
  case object Hreflang extends Type("hreflang")
  case object Media extends Type("media")
  case object Title extends Type("title")
  case object TitleStar extends Type("title*")
  case object Type extends Type("type")
}