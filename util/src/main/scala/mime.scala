package unfiltered.util

case class MIMEType(major: String, minor: String, params: Map[String, String]) {
  def includes(mt: MIMEType) = {
    (major, minor) match {
      case ("*", "*") => true
      case (maj, "*") => mt.major == maj
      case (maj, min) => mt.major == maj && mt.minor == min
    }
  }

  override def toString =
    "%s/%s%s".format(major,
      minor,
      params.map {
        case (a, b) => "; %s=%s".format(a, b)
      }.mkString(""))
}

object MIMEType {

  private val EqualPattern = "(?sm)(.*)=(.*)".r
  private val MimeMatcher = "(?sm)([\\w-*]+)/([\\w-*+.]+);?(.*)?".r

  def parse(s: String): Option[MIMEType] = {
    s match {
      case MimeMatcher(major, minor, params) => {
        Some(MIMEType(major, minor, if (params == null) Map.empty else parseParams(params)))
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
