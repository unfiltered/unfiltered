package unfiltered.request

/** Parser for json request bodies. Produces output from net.liftweb.json.JsonParser. */
object JsonBody {
  import net.liftweb.json.JsonParser._
  implicit val formats = net.liftweb.json.DefaultFormats

  /** @return Some(JsValue) if request contains a valid json body. */
  def apply[T](r: HttpRequest[T]) = try {
    Some(parse(Body.string(r)))
  } catch { case _ => None }
}

/** jsonp extractor(s). Useful for extracting a callback out of a request */
object Jsonp {
  import net.liftweb.json.JsonAST._
  import net.liftweb.json.JsonDSL._

  object Callback extends Params.Extract("callback", Params.first)

  trait Wrapper {
    def wrap(body: String): String
    def respond(json: => JValue): unfiltered.response.ComposeResponse[Any]
  }

  object EmptyWrapper extends Wrapper {
    def wrap(body: String) = body
    def respond(json: => JValue) = unfiltered.response.Json(json)
  }

  class CallbackWrapper(cb: String) extends Wrapper {
    def wrap(body: String) = "%s(%s)" format(cb, body)
    def respond(json: => JValue) = unfiltered.response.Json(json, cb)
  }

  /** @return if request accepts json, (callbackwrapper, req) tuple if a callback param
      is provided else (emptywrapper, req) tuple is no callback param is provided */
  object Optional {
    def unapply[T](r: HttpRequest[T]) = r match {
      case Accepts.Jsonp(Params(p)) =>
        Some(p match {
          case Callback(cb) => new CallbackWrapper(cb)
          case _ => EmptyWrapper
        })
      case _ => None
    }
  }

  /** @return (callbackwrapper, req) tuple if request accepts json and a callback
      param is provided  */
  def unapply[T](r: HttpRequest[T]) = r match {
    case Accepts.Jsonp(Params(Callback(cb))) => Some(new CallbackWrapper(cb))
    case _ => None
  }
}
