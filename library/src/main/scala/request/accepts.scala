package unfiltered.request

/** Accepts request header extractor */
object Accepts {
  
  trait Accepting {
    val contentType: String
    val sym: Symbol
    lazy val ext = sym.name
   
    def unapply(r: javax.servlet.http.HttpServletRequest) = {
      val pathSuffix = r.getRequestURI.substring(r.getContextPath.length).split("[.]").lastOption
      r match {
        case Accept(values, _) =>
          if(!values.filter(_.equalsIgnoreCase(contentType)).isEmpty) Some(sym)
          else if(!values.filter(_=="*/*").isEmpty && ext == pathSuffix.getOrElse("not")) Some(sym)
          else None
        case _ => pathSuffix match {
          case Some(pathSuffix) if(pathSuffix == ext) => Some(sym)
          case _ => None
        }
      }
    }
  }

  case object AcceptingJson extends Accepting {
    val contentType = "application/json"
    val sym = 'json
  }

  case object AcceptingXml extends Accepting {
    val contentType = "text/xml"
    val sym = 'xml
  }
  
  case object AcceptingHtml extends Accepting {
    val contentType = "text/html"
    val sym = 'html
  }
  
  case object AcceptingCsv extends Accepting {
    val contentType = "text/csv"
    val sym = 'csv
  }

  def unapply(r: javax.servlet.http.HttpServletRequest) = r match {
    case AcceptingJson(sym) => Some((sym, r))
    case AcceptingXml(sym) => Some((sym, r))
    case AcceptingHtml(sym) => Some((sym, r))
    case AcceptingCsv(sym) => Some((sym, r))
    case _ => None
  }
}
