package unfiltered

case class Cookie(name: String, value: String, domain: Option[String] = None,path: Option[String] = None, maxAge: Option[Int] = None, secure: Option[Boolean] = None) {
  @deprecated("use copy(domain = Some(d))")
  def domain(d: String): Cookie = copy(domain = Some(d))
  @deprecated("use copy(path=Some(p))")
  def path(p: String): Cookie = copy(path = Some(p))
  @deprecated("use copy(maxAge = Some(a))")
  def maxAge(a: Int): Cookie = copy(maxAge = Some(a))
  @deprecated("use copy(secure = Some(s))")
  def secure(s: Boolean): Cookie = copy(secure = Some(s))
}
