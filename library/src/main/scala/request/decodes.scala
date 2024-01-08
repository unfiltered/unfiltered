package unfiltered.request

/** [[https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.3]] */
object Decodes {
  def decoding(encB: String): RequestExtractor.Predicate[List[String]] =
    RequestExtractor.predicate(AcceptEncoding) { encs =>
      encs.exists { encA => encA.equalsIgnoreCase(encB) || encA == "*" }
    }

  /* IANA encodings. See [[https://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6]]. */
  val Chunked: RequestExtractor.Predicate[List[String]] = decoding("chunked")
  val Identity: RequestExtractor.Predicate[List[String]] = decoding("identity")
  val GZip: RequestExtractor.Predicate[List[String]] = decoding("gzip")
  val Compress: RequestExtractor.Predicate[List[String]] = decoding("compress")
  val Deflate: RequestExtractor.Predicate[List[String]] = decoding("deflate")

}
