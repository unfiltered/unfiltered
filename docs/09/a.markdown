Who's Who
---------

Out of the box HTTP provides you with [basic authentication][basic],
a simple way to specify a name and password for a request. These
credentials are transferred as an unencrypted request header, so
applications should secure both credentials and message bodies by
requiring HTTPS for any protected resources.

[basic]: http://en.wikipedia.org/wiki/Basic_access_authentication

Below, we define a *kit* that extracts a username and password via
basic HTTP authentication and verifies those credentials before
letting anyone through the gate. It presumes a `Users` service that
would validate the user's credentials.

```scala
trait Users {
  def auth(u: String, p: String): Boolean
}

import unfiltered.request._
import unfiltered.response._
import unfiltered.Cycle

case class Auth(users: Users) {
  def apply[A,B](intent: Cycle.Intent[A,B]) =
    Cycle.Intent[A,B] {
      case req@BasicAuth(user, pass) if(users.auth(user, pass)) =>
        Cycle.Intent.complete(intent)(req)
      case _ =>
        Unauthorized ~> WWWAuthenticate("""Basic realm="/"""")
    }
}
```

By applying this kit we can layer basic authentication around any
intent in a client application.

```scala
case class App(users: Users) extends
unfiltered.filter.Plan {
  def intent = Auth(users) {
    case _ => ResponseString("Shhhh!")
  }
}
```

Also, don't give the password to any newspaper reporters.
