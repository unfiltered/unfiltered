package unfiltered.request

object AgentIs {  
  // An agent matcher is a String => Boolean.
  type AM = String => Boolean
  
  // Matchers for major agents.
  val chromeAM:   AM = _.contains("Chrome")
  val firefoxAM:  AM = (ua) => ua.toLowerCase.contains("firefox") && !ua.contains("Opera")
  val operaAM:    AM = _.contains("Opera")
  val ieAM:       AM = (ua) => ua.contains("MSIE") && !ua.contains("Opera")
  val safariAM:   AM = (ua) => (ua.contains("AppleWebKit") || ua.contains("Safari")) && ! (chromeAM(ua) || operaAM(ua))
  // Mobile.
  val mobile:     AM = (ua) => MobileAgent.all.exists(ua.contains(_))
  
  def agent[A](pf: String => Boolean) = new AgentExtractor {
    val test = pf
  }
  
  // Agent extractors.
  val Chrome  = agent(chromeAM)
  val Safari  = agent(safariAM)
  val FireFox = agent(firefoxAM)
  val IE      = agent(ieAM)
  val Opera   = agent(operaAM)
  
  // Mobile extractors.
  val Mobile        = agent(mobile)
  val SafariMobile  = Safari & Mobile
  
}

object MobileAgent {
  val apple = List("iPhone", "iPod", "iPad")
  val all = apple
}
