package unfiltered.oauth

import unfiltered._
import unfiltered.request._
import unfiltered.response._
import unfiltered.request.{HttpRequest => Req}

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
    val KeyVal = """(\w+)="([\w|:|\/|.|%|-]+)" """.trim.r
    val keys = Set.empty + "realm" + ConsumerKey + TokenKey + SignatureMethod +
      Sig + Timestamp + Nonce + Callback + Verifier + Version

    def apply(hvals: Seq[String]) =
      Map((hvals map { _.replace("OAuth ", "") } flatMap {
        case KeyVal(k, v) if(keys.contains(k)) => Seq((k -> Seq(v)))
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
}

trait DefaultMessages extends Messages {
  def blankMsg(param: String) = "%s can not be blank" format param
  def requiredMsg(param: String) = "%s is required" format param
}

trait Protected extends OAuthProvider with unfiltered.filter.Plan {
  self: OAuthStores with Messages =>
  import unfiltered.filter.request.ContextPath
  import QParams._
  import OAuth._

  def intent = {
    case Params(params) & request =>
      val headers = Authorization(request) match {
         case Some(a) => OAuth.Header(a.split(","))
         case _ => Map.empty[String, Seq[String]]
      }
      val expected = for {
        oauth_consumer_key <- lookup(ConsumerKey) is
          nonempty(blankMsg(ConsumerKey)) is required(requiredMsg(ConsumerKey))
        oauth_signature_method <- lookup(SignatureMethod) is
          nonempty(blankMsg(SignatureMethod)) is required(requiredMsg(SignatureMethod))
        timestamp <- lookup(Timestamp) is
          nonempty(blankMsg(Timestamp)) is required(requiredMsg(Timestamp))
        nonce <- lookup(Nonce) is
          nonempty(blankMsg(Nonce)) is required(requiredMsg(Nonce))
        token <- lookup(TokenKey) is
          nonempty(blankMsg(TokenKey)) is required(requiredMsg(TokenKey))
        signature <- lookup(Sig) is
          nonempty(blankMsg(Sig)) is required(requiredMsg(Sig))
        version <- lookup(Version) is
          pred ( _ == "1.0", "invalid oauth version " + _ ) is
          optional[String,String]
        realm <- lookup("realm") is optional[String, String]
      } yield {
        protect(request.method, request.underlying.getRequestURL.toString, params ++ headers) match {
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

      expected(params ++ headers) orFail { errors =>
        BadRequest ~> ResponseString(errors.map { _.error } mkString(". "))
      }

  }
}

trait OAuthed extends OAuthProvider with unfiltered.filter.Plan {
  self: OAuthStores with Messages with OAuthPaths =>
  import unfiltered.filter.request.ContextPath
  import QParams._
  import OAuth._

  def intent = {
    case POST(ContextPath(_, RequestTokenPath) & Params(params)) & request =>
       val headers = Authorization(request) match {
         case Some(a) => OAuth.Header(a.split(","))
         case _ => Map.empty[String, Seq[String]]
      }
      val expected = for {
        consumer_key <- lookup(ConsumerKey) is
          nonempty(blankMsg(ConsumerKey)) is required(requiredMsg(ConsumerKey))
        oauth_signature_method <- lookup(SignatureMethod) is
          nonempty(blankMsg(SignatureMethod)) is required(requiredMsg(SignatureMethod))
        timestamp <- lookup(Timestamp) is
          nonempty(blankMsg(Timestamp)) is required(requiredMsg(Timestamp))
        nonce <- lookup(Nonce) is
          nonempty(blankMsg(Nonce)) is required(requiredMsg(Nonce))
        callback <- lookup(Callback) is
          nonempty(blankMsg(Callback)) is required(requiredMsg(Callback))
        signature <- lookup(Sig) is
          nonempty(blankMsg(Sig)) is required(requiredMsg(Sig))
        version <- lookup(Version) is
          pred ( _ == "1.0", "invalid oauth version " + _ ) is
          optional[String,String]
      } yield {
        // TODO how to extract the full url and not rely on underlying
        requestToken(request.method, request.underlying.getRequestURL.toString, params ++ headers) match {
          case Failure(status, msg) => fail(status, msg)
          case resp: OAuthResponseWriter => resp ~> FormEncodedContent
        }
      }

      expected(params ++ headers) orFail { errors =>
        BadRequest ~> ResponseString(errors.map { _.error } mkString(". "))
      }

    case ContextPath(_, AuthorizationPath) & Params(params) & request =>
      val expected = for {
        token <- lookup(TokenKey) is
          nonempty(blankMsg(TokenKey)) is required(requiredMsg(TokenKey))
      } yield {
        authorize(token.get, request) match {
          case Failure(code, msg) => fail(code, msg)
          case HostResponse(resp) => Ok ~> resp
          case AuthorizeResponse(callback, token, verifier) => callback match {
            case OAuth.Oob => users.oobResponse(verifier)
            case _ => Redirect("%s%soauth_token=%s&oauth_verifier=%s" format(
              callback, if(callback.contains("?")) "&" else "?", token, verifier))
          }
        }
      }

      expected(params) orFail { errors =>
        BadRequest ~> ResponseString(errors.map { _.error } mkString(". "))
      }

    case request @ POST(ContextPath(_, AccessTokenPath) & Params(params)) =>
      val headers = Authorization(request) match {
         case Some(a) => OAuth.Header(a.split(","))
         case _ => Map.empty[String, Seq[String]]
      }
      val expected = for {
        consumer_key <- lookup(ConsumerKey) is
          nonempty(blankMsg(ConsumerKey)) is required(requiredMsg(ConsumerKey))
        oauth_signature_method <- lookup(SignatureMethod) is
          nonempty(blankMsg(SignatureMethod)) is required(requiredMsg(SignatureMethod))
        timestamp <- lookup(Timestamp) is
          nonempty(blankMsg(Timestamp)) is required(requiredMsg(Timestamp))
        nonce <- lookup(Nonce) is
          nonempty(blankMsg(Nonce)) is required(requiredMsg(Nonce))
        token <- lookup(TokenKey) is
          nonempty(blankMsg(TokenKey)) is required(requiredMsg(TokenKey))
        verifier <- lookup(Verifier) is
          nonempty(blankMsg(Verifier)) is required(requiredMsg(Verifier))
        signature <- lookup(Sig) is
          nonempty(blankMsg(Sig)) is required(requiredMsg(Sig))
        version <- lookup(Version) is
          pred ( _ == "1.0", "invalid oauth version " + _ ) is
          optional[String,String]
      } yield {
        accessToken(request.method, request.underlying.getRequestURL.toString, params ++ headers) match {
          case Failure(code, msg) => fail(code, msg)
          case resp@AccessResponse(_, _) =>
            resp ~> FormEncodedContent
        }
      }

      expected(params ++ headers) orFail { fails =>
        BadRequest ~> ResponseString(fails.map { _.error } mkString(". "))
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
         tokens.put(RequestToken(key, secret, consumer.key, java.net.URLDecoder.decode(p(Callback)(0), "utf-8")))
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
