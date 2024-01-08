package unfiltered.util

case class MIMEType(major: String, minor: String, params: Map[String, String] = Map.empty) {
  def includes(mt: MIMEType): Boolean = {
    (major, minor) match {
      case ("*", "*") => true
      case (maj, "*") => mt.major == maj
      case (maj, min) => mt.major == maj && mt.minor == min
    }
  }

  override def toString: String =
    "%s/%s%s".format(
      major,
      minor,
      params.map { case (a, b) =>
        "; %s=%s".format(a, b)
      }.mkString("")
    )
}

object MIMEType {
  val ALL: MIMEType = MIMEType("*", "*", Map.empty)

  private[this] val EqualPattern = "(?sm)(.*)=(.*)".r
  private[this] val MimeMatcher = "(?sm)([\\w-*]+)/([\\w-*+.]+);?(.*)?".r

  def parse(s: String): Option[MIMEType] = {
    s match {
      case MimeMatcher(major, minor, params) => {
        Some(MIMEType(major.toLowerCase, minor.toLowerCase(), if (params == null) Map.empty else parseParams(params)))
      }
      case _ => None
    }
  }

  private def parseParams(params: String): Map[String, String] = {
    val map = Map.newBuilder[String, String]
    val scanner = new java.util.Scanner(params).useDelimiter(";")
    while (scanner.hasNext) {
      val next = scanner.next
      next match {
        case EqualPattern(name, value) => map += name.trim -> (if (value == null) "" else value.trim)
        case _ =>
      }
    }
    map.result()
  }

  def unapply(mt: String): Option[MIMEType] = parse(mt)
}
