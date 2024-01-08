package unfiltered.response.link

object Param {

  /** Predefined parameter types as specified in
      [[https://www.rfc-editor.org/rfc/rfc5988#section-5 section-5]]. Note that
      `rev` is omitted as it has been deprecated by the specification. */
  sealed abstract class Type(val name: String)
  case object Rel extends Type("rel")
  case object Anchor extends Type("anchor")
  case object Hreflang extends Type("hreflang")
  case object Media extends Type("media")
  case object Title extends Type("title")
  case object TitleStar extends Type("title*")
  case object ContentType extends Type("type")

  /** The extension type supporting `link-extension` parameters. */
  private[link] final case class ExtensionType(override val name: String) extends Type(name)

  /** Construct an extension parameter. */
  def extension(paramType: String): String => Extension =
    value => Extension(ExtensionType(paramType), value)

  /** Extractor for parameter types that cannot repeat within a `Ref`. */
  object NonRepeatable {
    def unapply(param: Param): Option[Param] =
      param.paramType match {
        case Rel | Media | Title | TitleStar | ContentType => Some(param)
        case _ => None
      }
  }
}

/** Root type for all implementations of `link-param` as specified in
    [[https://www.rfc-editor.org/rfc/rfc5988#section-5 section-5]].
    Predefined parameter values are specified in various documents linked or
    referred to in the [[https://www.rfc-editor.org/rfc/rfc5988 rfc5988]];
    see [[Media]] and [[Rel]].
    New parameter types can be added to `Link` headers as `link-extension`
    parameters. Extension parameters can be constructed
    via [[Param.extension]]. */
sealed abstract class Param(val paramType: Param.Type, val value: String)
final case class Anchor(uri: String) extends Param(Param.Anchor, uri)
final case class Hreflang(lang: String) extends Param(Param.Hreflang, lang)
final case class Title(title: String) extends Param(Param.Title, title)
final case class TitleStar(titleStar: String) extends Param(Param.TitleStar, titleStar)
final case class MediaType(typeName: String, subTypeName: String)
    extends Param(Param.ContentType, s"$typeName/$subTypeName")
final case class Extension private[link] (override val paramType: Param.ExtensionType, override val value: String)
    extends Param(paramType, value)

/** Target media types as described in
    [[https://www.rfc-editor.org/rfc/rfc5988#section-5.4 section-5.4]] The meaning
    and set of possible values for this parameter are specified in the
    [[https://www.w3.org/TR/html401/types.html#h-6.13 HTML 401 Types]] specification. */
sealed abstract class Media(val mediaType: String) extends Param(Param.Media, mediaType) {

  /** According to
      [[https://www.w3.org/TR/html401/types.html#h-6.13 HTML 401 Types]],
      `Media` is a monoid resulting in the accumulated media for a single
      `media` parameter. */
  def :+(that: Media) = CompositeMedia(this, that)

  final override def toString: String = s"Media($mediaType)"
}

private[link] final case class CompositeMedia(a: Media, b: Media) extends Media(a.mediaType + ", " + b.mediaType)
case object Screen extends Media("screen")
case object Tty extends Media("tty")
case object Tv extends Media("tv")
case object Projection extends Media("projection")
case object Handheld extends Media("handheld")
case object Print extends Media("print")
case object Braille extends Media("braille")
case object Aural extends Media("aural")
case object All extends Media("all")

/** A link relation type as described in
    [[https://www.rfc-editor.org/rfc/rfc5988#section-5.3 section-5.3]].
    The relation type is specified as link parameter for which a global set
    of possible values is catalogued at
    [[http://www.iana.org/assignments/link-relations/link-relations.xml]].
    The specification also permits extension types to
    be provided as absolute URLs (see the [[ExtensionRel]] type). */
sealed abstract class Rel(val relType: String) extends Param(Param.Rel, relType) {

  /** According to
      [[https://www.rfc-editor.org/rfc/rfc5988#section-5.5 section-5.5]], `Rel`
      is a monoid resulting in the accumulated relation types for a single
      `rel` parameter. */
  def :+(that: Rel) = CompositeRel(this, that)

  final override def toString: String = s"Rel($relType)"
}

private[link] final case class CompositeRel(a: Rel, b: Rel) extends Rel(a.relType + " " + b.relType)

/** Support for extension relation types as specified in
    [[https://www.rfc-editor.org/rfc/rfc5988#section-4.2 section-4.2]] */
final case class ExtensionRel(uri: String) extends Rel(uri)

/* The complete set of catalogued relation types. */
case object About extends Rel("about")
case object Alternate extends Rel("alternate")
case object Appendix extends Rel("appendix")
case object Archives extends Rel("archives")
case object Author extends Rel("author")
case object Bookmark extends Rel("bookmark")
case object Canonical extends Rel("canonical")
case object Chapter extends Rel("chapter")
case object Collection extends Rel("collection")
case object Contents extends Rel("contents")
case object Copyright extends Rel("copyright")
case object CreateForm extends Rel("create-form")
case object Current extends Rel("current")
case object Describedby extends Rel("describedby")
case object Describes extends Rel("describes")
case object Disclosure extends Rel("disclosure")
case object Duplicate extends Rel("duplicate")
case object Edit extends Rel("edit")
case object EditForm extends Rel("edit-form")
case object EditMedia extends Rel("edit-media")
case object Enclosure extends Rel("enclosure")
case object First extends Rel("first")
case object Glossary extends Rel("glossary")
case object Help extends Rel("help")
case object Hosts extends Rel("hosts")
case object Hub extends Rel("hub")
case object Icon extends Rel("icon")
case object Index extends Rel("index")
case object Item extends Rel("item")
case object Last extends Rel("last")
case object LatestVersion extends Rel("latest-version")
case object License extends Rel("license")
case object Lrdd extends Rel("lrdd")
case object Memento extends Rel("memento")
case object Monitor extends Rel("monitor")
case object MonitorGroup extends Rel("monitor-group")
case object Next extends Rel("next")
case object NextArchive extends Rel("next-archive")
case object Nofollow extends Rel("nofollow")
case object Noreferrer extends Rel("noreferrer")
case object Original extends Rel("original")
case object Payment extends Rel("payment")
case object PredecessorVersion extends Rel("predecessor-version")
case object Prefetch extends Rel("prefetch")
case object Prev extends Rel("prev")
case object Preview extends Rel("preview")
case object Previous extends Rel("previous")
case object PrevArchive extends Rel("prev-archive")
case object PrivacyPolicy extends Rel("privacy-policy")
case object Profile extends Rel("profile")
case object Related extends Rel("related")
case object Replies extends Rel("replies")
case object Search extends Rel("search")
case object Section extends Rel("section")
case object Self extends Rel("self")
case object Service extends Rel("service")
case object Start extends Rel("start")
case object Stylesheet extends Rel("stylesheet")
case object Subsection extends Rel("subsection")
case object SuccessorVersion extends Rel("successor-version")
case object Tag extends Rel("tag")
case object TermsOfService extends Rel("terms-of-service")
case object Timegate extends Rel("timegate")
case object Timemap extends Rel("timemap")
case object Type extends Rel("type")
case object Up extends Rel("up")
case object VersionHistory extends Rel("version-history")
case object Via extends Rel("via")
case object WorkingCopy extends Rel("working-copy")
case object WorkingCopyOf extends Rel("working-copy-of")
