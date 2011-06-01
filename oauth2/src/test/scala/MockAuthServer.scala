package unfiltered.oauth2

case class MockClient(id: String, secret: String, redirectUri: String) extends Client

case class MockResourceOwner(id:  String) extends ResourceOwner

case class MockToken(val value: String, val clientId: String,
                     val redirectUri: String, val owner: String)
     extends Token {
  def refresh = Some("refreshToken")
  def expiresIn = Some(10)
  def scopes = None
  def tokenType = "tokenType"
}

case class MockAuthServerProvider(cli: MockClient, owner: MockResourceOwner)
  extends AuthorizationProvider {

  trait MockClients extends ClientStore {
    def client(clientId: String, secret: Option[String]) =
      if(clientId == cli.id) {
        secret match {
          case Some(sec) =>
            if(sec == cli.secret) Some(cli)
            else None
          case _ => Some(cli)
        }
      } else None
  }

  trait MockTokens extends TokenStore {
    private val mock = MockToken("test", cli.id, cli.redirectUri, owner.id)
    def refresh(other: Token) = MockToken(
       other.value, other.clientId, other.redirectUri, other.owner
    )
    def token(code: String): Option[Token] = Some(mock)
    def refreshToken(refreshToken: String): Option[Token] = Some(mock)
    def accessToken(value: String): Option[Token] = Some(mock)

    def exchangeAuthorizationCode(other: Token): Token = mock

    def generateAuthorizationCode(owner: ResourceOwner, client: Client,
                          scope: Option[String], redirectURI: String) =
                            mock.value
    def generateClientToken(client: Client, scope: Option[String]) = mock
    def generateImplicitAccessToken(owner: ResourceOwner, client: Client,
                                    scope: Option[String], redirectURI: String) =
                                        mock
  }

  trait MockContainer extends Container {
    import unfiltered.request._
    import unfiltered.response._
    import unfiltered.request.{HttpRequest => Req}

    def errorUri(err: String) = None

    def login[T](bundle: RequestBundle[T]): ResponseFunction[Any] = {
      // would normally show login here
      Ok
    }

    def requestAuthorization[T](bundle: RequestBundle[T]): ResponseFunction[Any] = {
      // would normally prompt for auth here
      Ok
    }

    def invalidRedirectUri(uri: Option[String], client: Option[Client]) = {
      ResponseString("missing or invalid redirect_uri")
    }

    def invalidClient = ResponseString("invalid client")

    def resourceOwner[T](r: Req[T]): Option[ResourceOwner] = {
      // would normally look for a resource owners session here
      Some(owner)
    }

    def accepted[T](r: Req[T]) = {
      // would normally inspect the request for user approval here
      true
    }

    def denied[T](r: Req[T]) = {
      // would normally inspect the reuqest for user denial here
      false
    }

    def validScopes(scopes: Option[String]) = true

    def validScopes[T](owner: ResourceOwner, scopes: Option[String], req: Req[T]) = {
      // would normally validate that the scopes are valid for the owner here
      true
    }
  }

  object MockAuthorizationServer
     extends AuthorizationServer
     with MockClients with MockTokens with MockContainer

  val auth = MockAuthorizationServer
}
