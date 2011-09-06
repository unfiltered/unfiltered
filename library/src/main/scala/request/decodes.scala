package unfiltered.request

/** http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.3 */
object Decodes {
  
  trait Decoding {
    def unapply[A](req: HttpRequest[A]) = req match {
      case AcceptEncoding(encs) =>
        if (encs.exists(acceptable)) Some(req) else None
      case _ => None
    }
    def acceptable(enc: String) =
      Decodes.acceptable(encoding)(enc)
    def encoding: String
  }
  
  def acceptable(encA: String)(encB: String) =
    encA.equalsIgnoreCase(encB) || encA == "*"
  
  def decoding(enc: String) =
    new Decoding { val encoding = enc }
  
  /* IANA encodings. See http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6. */
  val Chunked   = decoding("chunked")
  val Identity  = decoding("identity")
  val GZip      = decoding("gzip")
  val Compress  = decoding("compress")
  val Deflate   = decoding("deflate")
}
