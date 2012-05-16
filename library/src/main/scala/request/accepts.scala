package unfiltered.request

/** Accepts request header extractor */
object Accepts {

  trait Accepting {
    def contentType: String

    def ext: String

    def unapply[T](r: HttpRequest[T]) = {
      val pathSuffix = Path(r).split("[.]").lastOption
      r match {
        case Accept(values) =>
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

  /** Lenient matcher for application/javascript and text/javascript */
  object JavaScript extends Accepting {
    val contentType = "text/javascript"
    val ext = "js"

    override def unapply[T](r: HttpRequest[T]) =
      AppJavaScript.unapply(r) orElse {super.unapply(r)}
  }

  object AppJavaScript extends Accepting {
    val contentType = "application/javascript"
    val ext = "js"
  }

  /** Lenient matcher for application/json, application/javascript, and text/javascript */
  object Jsonp {
    def unapply[T](r: HttpRequest[T]) =
      Json.unapply(r) orElse {JavaScript.unapply(r)}
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
