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
  extends AuthorizationServerProvider {

  trait MockClients extends ClientStore {
    def client(clientId: String, secret: Option[String]) = Some(cli)
  }

  trait MockTokens extends TokenStore {
    private val mock = MockToken("test", cli.id, cli.redirectUri, owner.id)
    def refresh(other: Token) = MockToken(
       other.value, other.clientId, other.redirectUri, other.owner
    )
    def token(code: String): Option[Token] = Some(mock)
    def clientToken(clientId: String): Option[Token] = Some(mock)
    def accessToken(code: String): Option[Token] = Some(mock)
    def generateAccessToken(other: Token): Token = mock
    def generateCodeToken(owner: ResourceOwner, client: Client,
                          scope: Option[String], redirectURI: String) =
                            mock.value
    def generateImplicitAccessToken(owner: ResourceOwner, client: Client,
                                    scope: Option[String], redirectURI: String) =
                                      mock
  }

  trait MockHost extends Host {
    import unfiltered.request._
    import unfiltered.response._
    import unfiltered.request.{HttpRequest => Req}

    def login(token: String): ResponseFunction[Any] = Ok

    def deniedConfirmation(consumer: Client): ResponseFunction[Any] = Ok

    def requestAcceptance(token: String, responseType: String,
                          client: Client, scope: Option[String]) = Ok

    def invalidRedirectUri(uri: String, client: Client) = Ok

    def resourceOwner: Option[ResourceOwner] = Some(owner)

    def accepted[T](r: Req[T]) = true

    def denied[T](r: Req[T]) = false

    def validScopes[T](owner: ResourceOwner, scopes: Option[String], req: Req[T]) = true
  }

  object MockAuthorizationServer
     extends AuthorizationServer
     with MockClients with MockTokens with MockHost

  val authSvr = MockAuthorizationServer
}
