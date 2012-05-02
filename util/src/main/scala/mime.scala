package unfiltered.util

case class MIMEType(major: String, minor: String, params: Map[String, String] = Map.empty) {
  def includes(mt: MIMEType) = {
     this match {
	   case MIMEType.All => true
	   case MIMEType(maj, "*", _) => mt.major == maj
	   case MIMEType(maj, min, _) => mt.major == maj && mt.minor == min
     }	
  }
  override def toString = "%s/%s".format(major, minor) + params.map{case (a, b) => "; %s=%s".format(a, b)}.mkString("")
}

object MIMEType {
  val All = MIMEType("*", "*")

  def apply(mt: String): Option[MIMEType] = {
    import javax.activation.MimeType
    import scala.collection.JavaConverters._
    try {
      val mimeType = new MimeType(mt)
      val names = mimeType.getParameters.getNames.asInstanceOf[java.util.Enumeration[String]].asScala
      val params = names.foldLeft(Map[String, String]())((acc, p) => acc + (p -> mimeType.getParameter(p)))
      Some(new MIMEType(mimeType.getPrimaryType, mimeType.getSubType, params))
    }
    catch {
      case _ => None
    }
  }

  def string2MIMEType(input: String) = apply(input).getOrElse(throw new IllegalArgumentException("Not a valid MIMEType"))
}
