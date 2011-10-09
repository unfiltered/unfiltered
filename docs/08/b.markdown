Remembrance of Things Past
--------------------------

Basic authentication is a lightweight solution to general
authentication, but what if we need to remember a little more
information about a user's session? That's where
[cookies](http://en.wikipedia.org/wiki/HTTP_cookie) come in.

Let's build on our authenticated application and add support for simple cookie handling.

```scala
import unfiltered.Cookie

case class App(users: Users)
  extends unfiltered.filter.Plan {

  def intent = Auth(users) {
    case Path("/") & Cookies(cookies) =>
      ResponseString(cookies("pref") match {
        case Some(Cookie(_, pref, _, _, _, _)) =>
          "you pref %s, don't you?" format pref
        case _ => "no preference?"
      })
    case Path("/prefer") & Params(p) =>
       // let's store it on the client
       ResponseCookies(Cookie("pref", p("pref")(0))) ~>
         Redirect("/")
    case Path("/forget") =>
       ResponseCookies(Cookie("pref", "")) ~>
         Redirect("/")
  }
}
```

Now that we have a slightly more sophisitcated basic application let's mount it with a user named `jim` and a password of `j@m`.

```scala
import unfiltered.jetty._

object Main {
  def main(args: Array[String]) {
    jetty.Http(8080).filter(App(new Users {
      def authentic(u: String, p: String) =
       u == "jim" && p == "j@m"
    })).run
  }
}
```

In your browser, open the url `http://localhost:8080/` and you should
be greeted with its native authentication dialog. Enter `jim` and
`j@m`, if you are feeling authentic.

Once authenticated you should see simple text questioning your
preferences. Why is this? Well, you have yet to tell the server what
you prefer. In your url bar, enter the address

    http://localhost:8080/prefer?pref=kittens

or whatever else you have a preference for. Now, every time you
request `http://localhost:8080/` the server has remembered your
preference for you. This is a cookie at work!

If you change your mind you can always hit the `prefer` path with a
new pref or just tell the server to forget it by entering the address
`http://localhost:8080/forget`.
