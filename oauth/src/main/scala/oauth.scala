package unfiltered.oauth

import unfiltered.request._
import unfiltered.response._
import unfiltered.request.{HttpRequest => Req}
import unfiltered.directives._, Directives._

object OAuth {
  val ConsumerKey = "oauth_consumer_key"
  val SignatureMethod = "oauth_signature_method"
  val Timestamp = "oauth_timestamp"
  val Nonce = "oauth_nonce"
  val Callback = "oauth_callback"
  val Sig = "oauth_signature"
  val TokenKey = "oauth_token"
  val Verifier = "oauth_verifier"
  val Version = "oauth_version"

  /** out-of-bounds callback value */
  val Oob = "oob"

  val XAuthorizedIdentity = "X-Authorized-Identity"

  /** Authorization: OAuth header extractor */
  object Header {
    import Encoding._
    val KeyVal = """[ ]?(\w+)="([\w|:|\/|.|%|-]+)" """.trim.r
    val keys = Set.empty + "realm" + ConsumerKey + TokenKey + SignatureMethod +
      Sig + Timestamp + Nonce + Callback + Verifier + Version

    def apply(hvals: Seq[String]) =
      Map((hvals map { _.trim.replace("OAuth ", "") } flatMap {
        case KeyVal(k, v) if(keys.contains(k)) => Seq((k -> Seq(decode(v))))
        case b => Nil
      }): _*)

    def unapply(hvals: Seq[String]) = Some(apply(hvals))
  }
}

/** Defines end points for oauth interation */
trait OAuthPaths {
  val RequestTokenPath: String
  val AuthorizationPath: String
  val AccessTokenPath: String
}

trait DefaultOAuthPaths extends OAuthPaths {
  val RequestTokenPath = "/request_token"
  val AuthorizationPath = "/authorize"
  val AccessTokenPath = "/access_token"
}

/** Formatting for validation messages */
trait Messages {
  def blankMsg(param: String): String
  def requiredMsg(param: String): String

  def required[T] = data.Requiring[T].fail(name =>
    BadParam(requiredMsg(name))
  )
  val nonEmptyString = data.as.String.nonEmpty.fail { (k, v) =>
    BadParam(blankMsg(k))
  }

  case class BadParam(msg: String) extends ResponseJoiner(msg)( messages =>
      BadRequest ~> ResponseString(messages.mkString(". "))
  )

  /** Combined header and parameter input */
  case class Inputs(request: HttpRequest[Any]) {
    val headers = Authorization(request) match {
       case Some(a) => OAuth.Header(a.split(","))
       case _ => Map.empty[String, Seq[String]]
    }
    val Params(params) = request
    val inputs = headers ++ params
    def named(name: String) = inputs.get(name).flatMap(_.headOption)
    def requiredNamed(name: String) =
      (nonEmptyString ~> required).named(name, named(name))

    val version =
      (data.as.String.named(OAuth.Version, named(OAuth.Version).toSeq) orElse
        failure(BadParam("invalid oauth version"))) filter (_.forall(_ == "1.0"))
  }
}

trait DefaultMessages extends Messages {
  def blankMsg(param: String) = "%s can not be blank" format param
  def requiredMsg(param: String) = "%s is required" format param
}

trait Protected extends OAuthProvider with unfiltered.filter.Plan {
  self: OAuthStores with Messages =>
  import unfiltered.filter.request.ContextPath
  import OAuth._

  def intent = Directive.Intent {
    case request =>
      val in = Inputs(request)

      for {
        ( oauth_consumer_key &
          oauth_signature_method &
          timestamp &
          nonce &
          token &
          signature &
          version &
          realm
        ) <-
          in.requiredNamed(ConsumerKey) &
          in.requiredNamed(SignatureMethod) &
          in.requiredNamed(Timestamp) &
          in.requiredNamed(Nonce) &
          in.requiredNamed(TokenKey) &
          in.requiredNamed(Sig) &
          in.version &
          (nonEmptyString named "realm")
      } yield {
        protect(request.method, request.underlying.getRequestURL.toString, in.inputs) match {
          case Failure(_, _) =>
            Unauthorized ~> WWWAuthenticate("OAuth realm=\"%s\"" format(realm match {
              case Some(value) => value
              case _ => request.underlying.getRequestURL.toString
            }))
          case Authorized(user) =>
            request.underlying.setAttribute(XAuthorizedIdentity, user)
            Pass
        }
      }
  }
}

trait OAuthed extends OAuthProvider with unfiltered.filter.Plan {
  self: OAuthStores with Messages with OAuthPaths =>
  import unfiltered.filter.request.ContextPath
  import OAuth._

  def intent = Directive.Intent {
    case POST(ContextPath(_, RequestTokenPath) & Params(params)) & request =>
      val in = Inputs(request)

      for {
        ( consumer_key &
          oauth_signature_method &
          timestamp &
          nonce &
          callback &
          signature &
          version
        ) <-
          in.requiredNamed(ConsumerKey) &
          in.requiredNamed(SignatureMethod) &
          in.requiredNamed(Timestamp) &
          in.requiredNamed(Nonce) &
          in.requiredNamed(Callback) &
          in.requiredNamed(Sig) &
          in.version
      } yield {
        // TODO how to extract the full url and not rely on underlying
        requestToken(request.method, request.underlying.getRequestURL.toString, in.inputs) match {
          case Failure(status, msg) => fail(status, msg)
          case resp: OAuthResponseWriter => resp ~> FormEncodedContent
        }
      }

    case ContextPath(_, AuthorizationPath) & Params(params) & request =>
      for {
        token <- nonEmptyString ~> required named TokenKey
      } yield {
        authorize(token, request) match {
          case Failure(code, msg) => fail(code, msg)
          case HostResponse(resp) => Ok ~> (resp.asInstanceOf[ResponseFunction[Any]])
          case AuthorizeResponse(callback, token, verifier) => callback match {
            case OAuth.Oob => users.oobResponse(verifier)
            case _ => Redirect("%s%soauth_token=%s&oauth_verifier=%s" format(
              callback, if(callback.contains("?")) "&" else "?", token, verifier))
          }
        }
      }

    case request @ POST(ContextPath(_, AccessTokenPath) & Params(params)) =>
      val in = Inputs(request)

      for {
        ( consumer_key &
          oauth_signature_method &
          timestamp &
          nonce &
          token &
          verifier &
          signature &
          version
        ) <-
          in.requiredNamed(ConsumerKey) &
          in.requiredNamed(SignatureMethod) &
          in.requiredNamed(Timestamp) &
          in.requiredNamed(Nonce) &
          in.requiredNamed(TokenKey) &
          in.requiredNamed(Verifier) &
          in.requiredNamed(Sig) &
          in.version
      } yield {
        accessToken(request.method, request.underlying.getRequestURL.toString, in.inputs) match {
          case Failure(code, msg) => fail(code, msg)
          case resp@AccessResponse(_, _) =>
            resp ~> FormEncodedContent
        }
      }
  }

  def fail(status: Int, msg: String) =
    Status(status) ~> ResponseString(msg)
}

/** Configured OAuthed class that satisfies requirements for OAuthStores */
case class OAuth(stores: OAuthStores) extends OAuthed
     with OAuthStores with DefaultMessages with DefaultOAuthPaths {
  val nonces = stores.nonces
  val tokens = stores.tokens
  val consumers = stores.consumers
  val users = stores.users
}

case class Protection(stores: OAuthStores) extends Protected
    with OAuthStores with DefaultMessages {
  val nonces = stores.nonces
  val tokens = stores.tokens
  val consumers = stores.consumers
  val users = stores.users
}

trait OAuthProvider { self: OAuthStores =>
  import OAuth._

  def protect(method: String, url: String, p: Map[String, Seq[String]]): OAuthResponse =
    if(nonceValid(p(ConsumerKey)(0), p(Timestamp)(0), p(Nonce)(0))) (for {
      consumer <- consumers.get(p(ConsumerKey)(0))
    } yield {
      tokens.get(p(TokenKey)(0)) match {
        case Some(AccessToken(tokenKey, tokenSecret, user, consumerKey)) =>
          if(consumerKey == consumer.key) {
            if(Signatures.verify(method, url, p, consumer.secret, tokenSecret)) {
              Authorized(user)
            } else challenge(400, "invalid signature")
          } else challenge(400, "invalid token")
        case _ => challenge(400, "invalid token")
      }
    }) getOrElse challenge(400, "invalid consumer")
    else challenge(400, "invalid nonce")

  def requestToken(method: String, url: String, p: Map[String, Seq[String]]): OAuthResponse =
    if(nonceValid(p(ConsumerKey)(0), p(Timestamp)(0), p(Nonce)(0))) (for {
      consumer <- consumers.get(p(ConsumerKey)(0))
    } yield {
      if(Signatures.verify(method, url, p, consumer.secret, "")) {
         val (key, secret) = tokens.generate
         tokens.put(RequestToken(key, secret, consumer.key, p(Callback)(0)))
         TokenResponse(key, secret, true)
      } else challenge(400, "invalid signature")
    }) getOrElse challenge(400, "invalid consumer")
    else challenge(400, "invalid nonce")

  def authorize[T](tokenKey: String, request: Req[T]): OAuthResponse =
    tokens.get(tokenKey) match {
      case Some(RequestToken(key, secret, consumerKey, callback)) =>
        consumers.get(consumerKey) match {
          case Some(consumer) =>
            users.current(request) match {
              case Some(user) =>
                if(users.accepted(tokenKey, request)) {
                  val verifier = tokens.generateVerifier
                  tokens.put(AuthorizedRequestToken(key, secret, consumerKey, user.id, verifier))
                  AuthorizeResponse(callback, key, verifier)
                } else if(users.denied(tokenKey, request)) {
                  tokens.delete(tokenKey)
                  HostResponse(users.deniedConfirmation(consumer))
                } else HostResponse(users.requestAcceptance(tokenKey, consumer))
              case _ =>
                // ask user to sign in
                HostResponse(users.login(tokenKey))
            }
          case _ => challenge(400, "invalid consumer")
        }
      case _ => challenge(400, "invalid token")
    }

  def accessToken(method: String, url: String, p: Map[String, Seq[String]]): OAuthResponse =
    if(nonceValid(p(ConsumerKey)(0), p(Timestamp)(0), p(Nonce)(0))) (for {
      consumer <- consumers.get(p(ConsumerKey)(0))
      token <- tokens.get(p(TokenKey)(0))
    } yield {
      token match {
        case AuthorizedRequestToken(key, secret, consumerKey, user, verifier) =>
          if(verifier == p(Verifier)(0))
            if(Signatures.verify(method, url, p, consumer.secret, token.secret)) {
              val (key, secret) = tokens.generate
              tokens.delete(token.key)
              tokens.put(AccessToken(key, secret, user, consumer.key))
              AccessResponse(key, secret)
            } else challenge(400, "invalid signature")
          else challenge(400, "invalid verifier")
        case _ => challenge(400, "invalid token")
      }
    }) getOrElse challenge(400, "invalid consumer or token")
    else challenge(400, "invalid nonce")

  def challenge(status: Int, msg: String): OAuthResponse = Failure(status, msg)

  def nonceValid(consumer: String, timestamp: String, nonce: String) =
    nonces.put(consumer, timestamp, nonce)
}
