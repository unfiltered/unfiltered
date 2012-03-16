package unfiltered.response

import org.specs._

object ToCookiesSpec extends Specification {
  import unfiltered.Cookie
  import unfiltered.request.DateFormatting
  import java.util.Date

  "ToCookies" should {
    "serialize a single version 0 cookie" in {
      val expected = "myCookie=myValue;Expires=(.+);Path=/apathsomewhere;Domain=.adomainsomewhere;Secure"
      val cookie = Cookie("myCookie", "myValue",
        domain = Some(".adomainsomewhere"),
        maxAge = Some(50),
        path   = Some("/apathsomewhere"),
        secure = Some(true)
      )
      ToCookies(cookie) must beMatching(expected)
    }

    "serialize a single version 1 cookie" in {
      val expected = "myCookie=myValue;Max-Age=50;Path=\"/apathsomewhere\";Domain=.adomainsomewhere;Secure;Version=1";
      val cookie = Cookie("myCookie", "myValue",
        version = 1,
        domain  = Some(".adomainsomewhere"),
        maxAge  = Some(50),
        path    = Some("/apathsomewhere"),
        secure  = Some(true)
      )
      ToCookies(cookie) must_== expected
    }

    "serialize a single version 2 cookie" in {
      val expected = "myCookie=myValue;Max-Age=50;Path=\"/apathsomewhere\";Domain=.adomainsomewhere;Secure;Version=2";
      val cookie = Cookie("myCookie", "myValue",
        version = 2,
        domain  = Some(".adomainsomewhere"),
        maxAge  = Some(50),
        path    = Some("/apathsomewhere"),
        secure  = Some(true)
      )
      ToCookies(cookie) must_== expected
    }

    "serialize multiple cookies" in {
      val c1 = "myCookie=myValue;Max-Age=50;Path=\"/apathsomewhere\";Domain=.adomainsomewhere;Secure;Version=1;"
      val c2 = "myCookie2=myValue2;Path=\"/anotherpathsomewhere\";Domain=.anotherdomainsomewhere;Version=1;"
      val c3 = "myCookie3=myValue3;Version=1"

      val cookie = Cookie("myCookie", "myValue",
        version = 1,
        domain = Some(".adomainsomewhere"),
        maxAge = Some(50),
        path = Some("/apathsomewhere"),
        secure = Some(true)          
      )

      val cookie2 = Cookie("myCookie2", "myValue2",
        version = 1,
        domain  = Some(".anotherdomainsomewhere"),
        path    = Some("/anotherpathsomewhere"),
        secure  = Some(false)
      )
       
      val cookie3 = Cookie("myCookie3", "myValue3", version = 1)

      ToCookies(cookie, cookie2, cookie3) must_== (c1 + c2 + c3)
    }
    
    "seralize no cookies" in {
      ToCookies() must_== ""
    }
  }    
}
