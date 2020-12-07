package unfiltered.request

/** [[https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.3]] */
object Decodes {
  def decoding(encB: String) =
    RequestExtractor.predicate(AcceptEncoding) { encs =>
      encs.exists { encA => encA.equalsIgnoreCase(encB) || encA == "*" }
    }
  
  /* IANA encodings. See [[https://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6]]. */
  val Chunked   = decoding("chunked")
  val Identity  = decoding("identity")
  val GZip      = decoding("gzip")
  val Compress  = decoding("compress")
  val Deflate   = decoding("deflate")

}
