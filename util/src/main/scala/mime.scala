package unfiltered.util

class MIMEType(val major: String,
               val minor: String,
               val params: Map[String, String]) {
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
  def unapply(mt: String): Option[MIMEType] = {
    import javax.activation.MimeType
    import scala.collection.JavaConversions._
    util.control.Exception.allCatch.opt {
      val mimeType = new MimeType(mt)
      val names = mimeType.getParameters.getNames
      val params = names.foldLeft(Map.empty[String, String]) {
        case (acc, p: String) =>
          acc + (p -> mimeType.getParameter(p.asInstanceOf))
      }
      new MIMEType(mimeType.getPrimaryType, mimeType.getSubType, params)
    }
  }
}
