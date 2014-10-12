package unfiltered.response.link

/** A link relation type as described in
    [[https://tools.ietf.org/html/rfc5988#section-5.3 section-5.3]].
    The relation type is specified as link parameter for which a global set
    of possible values exist. The specification also permits extension types to
    be provided as absolute URLs (see the [[Extension]] type). */
sealed abstract class Rel(val relType: String) extends Param(Param.Rel, relType) {
  /** According to [[https://tools.ietf.org/html/rfc5988#section-5.5 section-5.5]],
      Rel is a monoid whose addition results in accumulated relation types for a
      single `rel` parameter. */
  def +(that: Rel) = CompositeRel(this, that)

  final override def toString = s"Rel($relType)"
}

private [link] final case class CompositeRel (a: Rel, b: Rel) extends Rel(a.relType + " " + b.relType)

/** Support for extension relation types as specified in
    [[https://tools.ietf.org/html/rfc5988#section-4.2 section-4.2]] */
final case class Extension (uri: String) extends Rel(uri) // TODO dbl quote when ; or , present

/* Relation types as catalogued at
   http://www.iana.org/assignments/link-relations/link-relations.xml. */
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