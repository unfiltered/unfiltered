package unfiltered.oauth2

/**
 * The access token provides an abstraction layer, replacing different
 * authorization constructs (e.g. username and password) with a single
 * token understood by the resource server.  This abstraction enables
 * issuing access tokens more restrictive than the authorization grant
 * used to obtain them, as well as removing the resource server's need
 * to understand a wide range of authentication methods.
 *
 * Access tokens can have different formats, structures, and methods of
 * utilization (e.g. cryptographic properties) based on the resource
 * server security requirements.  Access token attributes and the
 * methods used to access protected resources are beyond the scope of
 * this specification and are defined by companion specifications.
 *
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-1.3
 */
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

/**
 * The token store controls token-orientated operations. Specifically
 * anything that needs to happen with a token is the responsibility
 * of the incumbant TokenStore as typically it will require interacting
 * with the some kind of storage
 */
trait TokenStore {
  /** 
   * 
   * @see AuthorizationServer
   * @see RefreshTokenRequest
   * @notes http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-6
   * @return Gives a refreshed or new token given a valid access token
   */
  def refresh(other: Token): Token

  /** 
   * 
   * query for Token by client 
   * @see AuthorizationServer
   * @see RefreshTokenRequest
   * @notes http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-6
   * @return Given the a refresh token gives a new access token
   */
  def refreshToken(refreshToken: String): Option[Token]
  
  /**
   * 
   * @see AuthorizationServer
   * @see AccessTokenRequest
   * @notes http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.1.3
   * @return Given a "code" return a resource access token
   */
  def token(code: String): Option[Token]

  /** 
   * 
   * @see AuthorizationServer
   * @see AccessTokenRequest
   * @notes http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.1.3
   * @return Create an access token given a code token 
   */
  def exchangeAuthorizationCode(codeToken: Token): Token
  
  /** 
   * 
   * @see AuthorizationServer
   * @see AuthorizationCodeRequest
   * @notes http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.1
   * @return a short lived authorization code bound to a client
   * and redirect uri for a given resource owner. 
   */
  def generateAuthorizationCode(
        owner: ResourceOwner, client: Client,
        scope: Option[String], redirectUri: String): String
  
  /** 
   * 
   * @see AuthorizationServer
   * @see ImplicitAuthorizationRequest
   * @notes http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.2
   * @return an access token for an implicit client
   */
  def generateImplicitAccessToken(owner: ResourceOwner, client: Client,
                                  scope: Option[String], redirectUri: String): Token

  /** 
   * 
   * @see AuthorizationServer
   * @see ClientCredentialsRequest
   * @notes http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.4
   * @return an access token for a given client, not tied to 
   * a given resource owner
   */
  def generateClientToken(client: Client, scope: Option[String]): Token
}
