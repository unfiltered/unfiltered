package unfiltered.request

object AgentIs {
  // An agent matcher is a String => Boolean.
  type AM = String => Boolean

  // OSes.
  val androidAM: AM = _.contains("Android")
  // Matchers for major agents.
  val chromeAM: AM = _.contains("Chrome")
  val firefoxAM: AM = ua => ua.toLowerCase.contains("firefox") && !operaAM(ua)
  val operaAM: AM = _.contains("Opera")
  val ieAM: AM = ua => ua.contains("MSIE") && !operaAM(ua)
  val safariAM: AM = ua =>
    (ua.contains("AppleWebKit") || ua.contains("Safari")) && !(chromeAM(ua) || operaAM(ua) || androidAM(ua))
  // Mobile.
  val mobile: AM = ua => MobileAgent.all.exists(ua.contains(_))

  def agent[A](pf: String => Boolean): AgentExtractor =
    new AgentExtractor { val test = pf }

  // OS extractors.
  val Android: AgentExtractor = agent(androidAM)
  // Agent extractors.
  val Chrome: AgentExtractor = agent(chromeAM)
  val Safari: AgentExtractor = agent(safariAM)
  val FireFox: AgentExtractor = agent(firefoxAM)
  val IE: AgentExtractor = agent(ieAM)
  val Opera: AgentExtractor = agent(operaAM)
  // Mobile extractors.
  val Mobile: AgentExtractor = agent(mobile)
  val SafariMobile: AgentExtractor = Safari & Mobile
}

object MobileAgent {
  val apple: List[String] = List("iPhone", "iPod", "iPad")
  val all: List[String] = "Android" :: apple
}
