package unfiltered.oauth

trait Combining { this: Encoding =>
  def combine(strs: String*) = strs map { encode } mkString("&")
  def combine(parts: Map[String, String]) = parts map { case (k, v) => "%s=%s" format(encode(k), encode(v)) } mkString "&"
}