package unfiltered.oauth2

case class MockClient(id: String, secret: String, redirectURI: String) extends Client

case class MockResourceOwner(id:  String) extends ResourceOwner

case object MockToken extends Token {
  def code = "code"
  def refresh = Some("refreshToken")
  def expiresIn = Some(10)
  def clientId = "clientid"
  def redirectURI = "..."
  def scopes = None
  def owner = "owner"
  def tokenType = "tokenType"
}

case class MockAuthProvider(mclient: MockClient, owner: MockResourceOwner)
  extends AuthorizationServerProvider {

  trait MockClients extends ClientStore {
    def client(clientId: String, secret: Option[String]) = Some(mclient)
  }

  trait MockTokens extends TokenStore {
    def token(code: String): Option[Token] = Some(MockToken)
    def clientToken(clientId: String): Option[Token] = Some(MockToken)
    def accessToken(code: String): Option[Token] = Some(MockToken)
    def generateAccessToken(other: Token): Token = MockToken
    def generateCodeToken(owner: ResourceOwner, client: Client, redirectURI: String): Token =
      MockToken
    def generateImplicitAccessToken(owner: ResourceOwner, client: Client, redirectURI: String): Token =
      MockToken
  }

  trait MockHost extends Host {
    import unfiltered.request._
    import unfiltered.response._
    import unfiltered.request.{HttpRequest => Req}

    def login(token: String): ResponseFunction[Any] = Ok
    def deniedConfirmation(consumer: Client): ResponseFunction[Any] = Ok
    def requestAcceptance(token: String, responseType: String, consumer: Client, scope: Option[String]): ResponseFunction[Any]
      = Ok
    def invalidRedirectUri(uri: String, client: Client): ResponseFunction[Any]
      = Ok
    def currentUser: Option[ResourceOwner] = Some(owner)
    def accepted[T](r: Req[T]) = true
    def denied[T](r: Req[T]) = false
    def validScopes[T](r: Req[T], scopes: Option[String]) = true
  }

  object MockAuthorizationServer extends AuthorizationServer
    with MockClients with MockTokens with MockHost

  val authSvr = MockAuthorizationServer
}
