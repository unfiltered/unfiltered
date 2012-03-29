package unfiltered.request

import org.specs._
import java.util.Date

// ported testcases from netty cookie decoder tests
object FromCookiesSpec extends Specification {
  import unfiltered.Cookie 
  implicit val nameOrdering: Ordering[Cookie] = Ordering.by((_:Cookie).name.toLowerCase)

  "FromCookies" should {
    "parse a version 0 cookie" in {
      val cs = "myCookie=myValue;expires=XXX;path=/apathsomewhere;domain=.adomainsomewhere;secure;".replace(
        "XXX", DateFormatting.format(new Date(System.currentTimeMillis() + 50000))
      )
      val cookies = FromCookies(cs)

      cookies.size must_== 1
      val cookie  = cookies(0)
      cookie.value must_== "myValue"
      cookie.domain must beSome(".adomainsomewhere")
      cookie.maxAge must beSome(49) or beSome(50) or beSome(51)
      cookie.path must beSome("/apathsomewhere")
      cookie.secure must beSome(true)
      cookie.version must_== 0
      //assertNull(cookie.getComment())
      //assertNull(cookie.getCommentUrl())
      //assertFalse(cookie.isDiscard())
      //assertTrue(cookie.getPorts().isEmpty())
    }

    "parse a single version 0 cookie ignoring extra params" in {
      val cs = "myCookie=myValue;max-age=50;path=/apathsomewhere;domain=.adomainsomewhere;secure;comment=this is a comment;version=0;commentURL=http://aurl.com;port=\"80,8080\";discard;"
      val cookies = FromCookies(cs)
      cookies.size must_== 1
      val cookie = cookies(0)
      cookie.value must_== "myValue"
      cookie.domain must beSome(".adomainsomewhere")
      cookie.maxAge must beSome(50)
      cookie.path must beSome("/apathsomewhere")
      cookie.secure must beSome(true)
      cookie.version must_== 0
      //assertNull(cookie.getComment())
      //assertNull(cookie.getCommentUrl())
      // assertFalse(cookie.isDiscard())
      // assertTrue(cookie.getPorts().isEmpty())
    }

    "parse a simple version 0 cookie" in {
      val cs = "myCookie=myValue;max-age=50;path=/apathsomewhere;domain=.adomainsomewhere;secure;comment=this is a comment;version=1;"
      val cookies = FromCookies(cs)
      cookies.size must_== 1
      val cookie = cookies(0)
      cookie.value must_== "myValue" 
      cookie.domain must beSome(".adomainsomewhere")
      cookie.maxAge must beSome(50)
      cookie.path must beSome("/apathsomewhere")
      cookie.secure must beSome(true)
      cookie.version must_== 1
      // cookie.comment must_== "this is a comment"
      //assertTrue(cookie.getPorts().isEmpty());
      //assertFalse(cookie.isDiscard());
      //assertNull(cookie.getCommentUrl());
    }

    "parse a single version 1 cookie w/ extra params ignored" in {
      val cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;domain=.adomainsomewhere;secure;comment=this is a comment;version=1;commentURL=http://aurl.com;port='80,8080';discard;"
      val cookies = FromCookies(cookieString)
      cookies.size must_== 1
      val cookie = cookies(0)
      cookie.value must_== "myValue"
      cookie.domain must beSome(".adomainsomewhere")
      cookie.maxAge must beSome(50)
      cookie.path must beSome("/apathsomewhere")
      cookie.secure must beSome(true)
      cookie.version must_== 1
      //assertEquals("this is a comment", cookie.getComment());
      //assertNull(cookie.getCommentUrl());
      //assertFalse(cookie.isDiscard());
      //assertTrue(cookie.getPorts().isEmpty());
    }

    "parse a single version 2 cookie" in {
      val cs = "myCookie=myValue;max-age=50;path=/apathsomewhere;domain=.adomainsomewhere;secure;comment=this is a comment;version=2;commentURL=http://aurl.com;port=\"80,8080\";discard;"
      val cookies = FromCookies(cs)
      cookies.size must_== 1
      val cookie = cookies(0)
      cookie.value must_== "myValue"
      cookie.domain must beSome(".adomainsomewhere")
      cookie.maxAge must beSome(50)
      cookie.path must beSome("/apathsomewhere")
      cookie.secure must beSome(true)
      cookie.version must_== 2
      //assertEquals("this is a comment", cookie.getComment());
      //assertEquals("http://aurl.com", cookie.getCommentUrl());
      //assertTrue(cookie.isDiscard());
      //assertEquals(2, cookie.getPorts().size());
      //assertTrue(cookie.getPorts().contains(80));
      //assertTrue(cookie.getPorts().contains(8080));
    }

    "parse multiple cookies" in {
      val cs = 
        "myCookie=myValue;max-age=50;path=/apathsomewhere;domain=.adomainsomewhere;secure;comment=this is a comment;version=2;commentURL=\"http://aurl.com\";port='80,8080';discard;" ::
        "myCookie2=myValue2;max-age=0;path=/anotherpathsomewhere;domain=.anotherdomainsomewhere;comment=this is another comment;version=2;commentURL=http://anotherurl.com;" ::
        "myCookie3=myValue3;max-age=0;version=2;" ::
        Nil mkString("")

      val cookies = FromCookies(cs)
      cookies.size must_== 3

      val c1 = cookies(0)
      c1.value must_== "myValue"
      c1.domain must beSome(".adomainsomewhere")
      c1.maxAge must beSome(50)
      c1.path must beSome("/apathsomewhere")
      c1.secure must beSome(true)
      c1.version must_== 2
      //assertEquals(2, cookie.getPorts().size())
      //assertEquals("this is a comment", cookie.getComment())
      //assertEquals("http://aurl.com", cookie.getCommentUrl())
      //assertTrue(cookie.getPorts().contains(80));
      //assertTrue(cookie.getPorts().contains(8080));
      //assertTrue(cookie.isDiscard());

      val c2 = cookies(1)
      c2.value must_== "myValue2"
      c2.domain must beSome(".anotherdomainsomewhere")
      c2.maxAge must beSome(0)
      c2.path must beSome("/anotherpathsomewhere")
      c2.secure must beNone
      c2.version must_== 2
      //assertFalse(cookie.isDiscard());
      //assertEquals("this is another comment", cookie.getComment());
      //assertEquals("http://anotherurl.com", cookie.getCommentUrl());

      val c3 = cookies(2)
      c3.value must_== "myValue3"
      c3.domain must beNone
      c3.maxAge must beSome(0)
      c3.path must beNone
      c3.secure must beNone
      c3.version must_== 2
      //assertTrue(cookie.getPorts().isEmpty())
      //assertTrue(cookie.getPorts().isEmpty());
      //assertFalse(cookie.isDiscard());
      //assertNull( cookie.getComment());
      //assertNull(cookie.getCommentUrl());
    }

    "parsing quoted cookies" in {
      val source =
        "a=\"\"," +
        "b=\"1\"," +
        "c=\"\\\"1\\\"2\\\"\"," +
        "d=\"1\\\"2\\\"3\"," +
        "e=\"\\\"\\\"\"," +
        "f=\"1\\\"\\\"2\"," +
        "g=\"\\\\\"";

      val cookies = FromCookies(source).iterator

      val a = cookies.next
      a.name must_== "a"
      a.value must_== ""

      val b = cookies.next
      b.name must_== "b"
      b.value must_== "1"

      val c = cookies.next
      c.name must_== "c"
      c.value must_== "\"1\"2\""

      val d = cookies.next
      d.name must_== "d"
      d.value must_== "1\"2\"3"

      val e = cookies.next
      e.name must_== "e"
      e.value must_== "\"\""

      val f = cookies.next
      f.name must_== "f"
      f.value must_== "1\"\"2"

      val g = cookies.next
      g.name must_== "g"
      g.value must_== "\\"

      cookies.hasNext must beFalse
    }

    "parse google analytics cookie" in {
      val source =
        "ARPT=LWUKQPSWRTUN04CKKJI; " ::
        "kw-2E343B92-B097-442c-BFA5-BE371E0325A2=unfinished furniture; " ::
        "__utma=48461872.1094088325.1258140131.1258140131.1258140131.1; " ::
        "__utmb=48461872.13.10.1258140131; __utmc=48461872; " ::
        "__utmz=48461872.1258140131.1.1.utmcsr=overstock.com|utmccn=(referral)|utmcmd=referral|utmcct=/Home-Garden/Furniture/Clearance,/clearance,/32/dept.html" :: Nil mkString("")
      val cookies = FromCookies(source).sorted.iterator

      val a = cookies.next
      a.name must_== "__utma"
      a.value must_== "48461872.1094088325.1258140131.1258140131.1258140131.1"

      val b = cookies.next
      b.name must_== "__utmb"
      b.value must_== "48461872.13.10.1258140131"

      val c = cookies.next
      c.name must_== "__utmc"
      c.value must_== "48461872"

      val d = cookies.next
      d.name must_== "__utmz"
      d.value must_== "48461872.1258140131.1.1.utmcsr=overstock.com|utmccn=(referral)|utmcmd=referral|utmcct=/Home-Garden/Furniture/Clearance,/clearance,/32/dept.html"

      val e = cookies.next
      e.name must_== "ARPT"
      e.value must_== "LWUKQPSWRTUN04CKKJI"

      val f = cookies.next
      f.name must_== "kw-2E343B92-B097-442c-BFA5-BE371E0325A2"
      f.value must_== "unfinished furniture"

      cookies.hasNext must beFalse
    }
  }
}
