package unfiltered.request

trait AgentExtractor {
  val test: String => Boolean
  def &(e: AgentExtractor) = new AgentExtractor {
    val test: String => Boolean = (ua) => AgentExtractor.this.test(ua) && e.test(ua)
  }
  def unapply[A](req: HttpRequest[A]) =
    UserAgent.unapply(req)
      .flatMap(ua => if(test(ua)) Some(req) else None)
}

object AgentIs {  
  
  /*
   * User agent strings are an unspecified abyss; anything goes. Wikipedia
   * states (http://en.wikipedia.org/wiki/User_agent#Format) that many vendors
   * use a non-standard derivative of RFC1945. Correcting this "spec" with some
   * real-world examples, regex RFC1945Common should match a large number of
   * these agents. From the pool of agent strings that should match, but instead
   * deviate from the pattern in some small way, the subsequent regexes have
   * been derived. Their names come from the agent that seems to supply the
   * most strings that match.
   */
  // name, version, system, platform, platform_details, extensions
  val RFC1945Common   = """([\w ]*)/([\d\.]*) ?\((.*)\) (.*) \((.*)\) ?(.*)""".r
  // name, version, system, platform, extensions
  val RFC1945FireFox  = """([\w ]*)/([\d\.]*) ?\((.*)\) (\w*[/\d\.]*) ?(.*)""".r
  // name, version, system
  val RFC1945IE       = """([\w ]*)/([\d\.]*) \((.*)\)""".r
  // name, version, os, system, platform, platform_details, extensions
  val RFC1945Chrome   = """([\w ]*)/([\d\.]*) (.*) \((.*)\) (.*) \((.*)\) ?(.*)""".r
  
  // An agent matcher is a String => Boolean.
  type AM = String => Boolean
  
  // Matchers for major agents.
  val chromeAM: AM  = _.contains("Chrome")
  val firefoxAM: AM = _.toLowerCase.contains("firefox")
  val operaAM: AM   = _.contains("Opera")
  val ieAM: AM      = (ua) => ua.contains("MSIE") && !ua.contains("Opera")
  val safariAM: AM  = (ua) => (ua.contains("AppleWebKit") || ua.contains("Safari")) && ! ua.contains("Chrome")
  
  def agent[A](pf: String => Boolean) = new AgentExtractor { val test = pf }
  
  // Agent extractors.
  val Chrome  = agent(chromeAM)
  val Safari  = agent(safariAM)
  val FireFox = agent(firefoxAM)
  val IE      = agent(ieAM)
  val Opera   = agent(operaAM)
  // Mobile.
  val Mobile  = agent(ua => MobileAgent.all.exists(ua.contains(_)))
  val SafariMobile = Safari & Mobile
  
}

object MobileAgent {
  val appleMobile = List("iPhone", "iPod", "iPad")
  val all = appleMobile
}
