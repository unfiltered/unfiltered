package unfiltered.request

/** Accepts request header extractor */
object Accepts {
  
  trait Accepting {
    val contentType: String

    val ext: String

    def unapply[T](r: HttpRequest[T]) = {
      val pathSuffix = r.requestURI.substring(r.contextPath.length).split("[.]").lastOption
      r match {
        case Accept(values, _) =>
          if(values.exists { _.equalsIgnoreCase(contentType) })
            Some(r)
          else if (values.exists { _ == "*/*" } && pathSuffix.exists { ext == _ })
            Some(r)
          else None
        case _ => pathSuffix match {
          case Some(pathSuffix) if(pathSuffix == ext) => Some(r)
          case _ => None
        }
      }
    }
  }

  object Json extends Accepting {
    val contentType = "application/json"
    val ext = "json"
  }

  object JavaScript extends Accepting {
    val contentType = "text/javascript"
    val ext = "js"
  }

  /** Lenient matcher for application/json and text/javascript */
  object Jsonp {
    def unapply[T](r: HttpRequest[T]) = 
      Json.unapply(r).orElse(JavaScript.unapply(r))
  }

  object Xml extends Accepting {
    val contentType = "text/xml"
    val ext = "xml"
  }
  
  object Html extends Accepting {
    val contentType = "text/html"
    val ext = "html"
  }
  
  object Csv extends Accepting {
    val contentType = "text/csv"
    val ext = "csv"
  }
}
