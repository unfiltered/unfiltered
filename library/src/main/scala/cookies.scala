package unfiltered

case class Cookie(name: String, value: String, domain: Option[String], path: Option[String], maxAge: Option[Int], secure: Option[Boolean]) {
  def domain(d: String): Cookie = Cookie(name, value, Some(d), path, maxAge, secure)
  def path(p: String): Cookie = Cookie(name, value, domain, Some(p), maxAge, secure)
  def maxAge(a: Int): Cookie = Cookie(name, value, domain, path, Some(a), secure)
  def secure(s: Boolean): Cookie = Cookie(name, value, domain, path, maxAge, Some(s))
}

object Cookie {
  def apply(name: String, value: String) = new Cookie(name, value, None, None, None, None)
}