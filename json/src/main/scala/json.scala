package unfiltered.request

/** Extractor for json request bodies */
object JsonBody {
  import javax.servlet.http.{HttpServletRequest => Req}
  import dispatch.json._
  import dispatch.json.Js._
  
  /** @return Some(JsValue, req) if request accepts json and contains a valid json body */
  def unapply(r: Req) = r match {
    case Accepts(fmt, Bytes(body, _)) => fmt match {
      case 'json => try{ Some(JsValue.fromString(new String(body)), r) } catch { case _ => None }
      case _ => None
    }
    case _ => None
  } 
}

/** jsonp extractor(s) */
object Jsonp {
  import javax.servlet.http.{HttpServletRequest => Req}
  
  trait Wrapper {
    def wrap(body: String): String
  }
  
  object EmptyWrapper extends Wrapper {
    def wrap(body: String) = body
  }
  
  class CallbackWrapper(cb: String) extends Wrapper {
    def wrap(body: String) = "%s(%s)" format(cb, body)
  }
  
  /** @return if request accepts json, (callbackwrapper, req) tuple if a callback param
      is provided else (emptywrapper, req) tuple is no callback param is provided */
  object Optional {
    def unapply(r: Req) = r match {
      case Accepts(fmt, Params(p, _)) => fmt match {
        case 'json => Some(p("callback") match {
          case Seq(cb, _*) => new CallbackWrapper(cb)
          case _ => EmptyWrapper
        }, r)
        case _ => None
      }
      case _ => None
    }
  }
  
  /** @return (callbackwrapper, req) tuple if request accepts json and a callback 
      param is provided  */
  def unapply(r: Req) = r match {
    case Accepts(fmt, Params(p, _)) => fmt match {
      case 'json => p("callback") match {
        case Seq(cb, _*) => Some(new CallbackWrapper(cb), r)
        case _ => None
      }
      case _ => None
    }
    case _ => None
  }
}