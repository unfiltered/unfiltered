package unfiltered.oauth2

trait Token {
  def value: String
  def clientId: String
  def redirectUri: String
  def owner: String
  def tokenType: String
  def refresh: Option[String]
  def expiresIn: Option[Int]
  def scopes: Option[String]
}

trait TokenStore {
  /** @return a refreshed or new token given a
   * valid access token */
  def refresh(other: Token): Token
  def token(code: String): Option[Token]
  /** query for Token by client */
  def refreshToken(refreshToken: String): Option[Token]
  /** create an access token given a code token */
  def generateAccessToken(codeToken: Token): Token
  /** @return an access token for a given client, not tied to a given resource owner */
  def generateClientToken(client: Client, scope: Option[String]): Token
  /** @return a short lived authorization code bound to a client
   *  and redirect uri for a given resource owner. */
  def generateCodeToken(owner: ResourceOwner, client: Client,
                        scope: Option[String], redirectUri: String): String
  /** @return a access token for an implicit client */
  def generateImplicitAccessToken(owner: ResourceOwner, client: Client,
                                  scope: Option[String], redirectUri: String): Token
}
