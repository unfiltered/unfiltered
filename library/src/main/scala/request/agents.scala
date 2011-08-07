package unfiltered.request

/**
 * http://en.wikipedia.org/wiki/User_agent#Format
 * Mozilla/[version] ([system and browser information]) [platform] ([platform details]) [extensions]
 * Mozilla/5.0 (iPad; U; CPU OS 3_2_1 like Mac OS X; en-us) AppleWebKit/531.21.10 (KHTML, like Gecko) Mobile/7B405
 * Mozilla/5.0 (X11; Linux i686) AppleWebKit/535.1 (KHTML, like Gecko) Ubuntu/11.04 Chromium/14.0.825.0 Chrome/14.0.825.0 Safari/535.1
 *
 * [name]/[version] [sys and browser]) [platform] [extensions]
 * Mozilla/5.0 (X11; U; Linux x86_64; pl-PL; rv:2.0) Gecko/20110307 Firefox/4.0
 */

object AgentIs {  
  
  /* name, version, system, platform, platform_details, extensions */
  val RFC1945Common   = """([\w ]*)/([\d\.]*) ?\((.*)\) (.*) \((.*)\) ?(.*)""".r
  /* name, version, system, platform, extensions */
  val RFC1945FireFox  = """([\w ]*)/([\d\.]*) \((.*)\) (\w*/[\d\.]*) ?(.*)""".r
  /* name, version, system */
  val RFC1945IE       = """([\w ]*)/([\d\.]*) \((.*)\)""".r
  /* name, version, os, system, platform, platform_details, extensions */
  val RFC1945Chrome   = """([\w ]*)/([\d\.]*) (.*) \((.*)\) (.*) \((.*)\) ?(.*)""".r
  
  /* An agent matcher is a partial String => Boolean. */
  type AM = PartialFunction[String, Boolean]
  
  def agent[A](pf: AM) = new {
    def unapply[A](req: HttpRequest[A]) =
      UserAgent.unapply(req)
        .flatMap(pf.lift)
        .flatMap(b => if(!b) None else Some(req))
  }
  
  /* Matchers for major agents. */
  val chromeAM: AM = {
    case RFC1945Common(_, _, _, _, pd, e)   => pd.contains("Chrome") || e.contains("Chrome")
    case RFC1945FireFox(_, _, _, _, e)      => e.contains("Chrome")
    case RFC1945Chrome(_, _, _, _, _, _, e) => e.contains("Chrome")
  }
  val safariAM: AM = {
    case RFC1945Common(_, _, _, p, _, e)    => (p.contains("AppleWebKit") || e.contains("Safari")) && ! e.contains("Chrome")
    case RFC1945FireFox(_, _, _, _, e)      => e.contains("Safari")
  }
  val firefoxAM: AM ={
    case RFC1945FireFox(_, _, _, _, e)      => e.contains("Firefox")
  }
  val ieAM: AM = {
    case RFC1945IE(n, v, s)                 => s.contains("MSIE")
  }

  /* Agent extractors. */
  val Chrome  = agent(chromeAM)
  val Safari  = agent(safariAM)
  val FireFox = agent(firefoxAM)
  val IE      = agent(ieAM)
    
}
