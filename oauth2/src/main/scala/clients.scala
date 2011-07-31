package unfiltered.oauth2

/**
 * TODO: What about the designation of this client? WebApp, Native etc... 
 * these are mandated parts of client registration as the designtation
 * infers the grant type.
 *
 * When registering a client, the client developer:
 *
 * - Specifies the client type as described in Section 2.1,
 * - Provides its client redirection URIs as described in
 *   Section 3.1.2, and
 * - Includes any other information required by the authorization
 *   server (e.g. application name, website, description, logo image,
 *   the acceptance of legal terms).
 * 
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-2.2
 */
trait Client {
  /**
   * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-2.3
   */
  def id: String
  
  /**
   * TODO: Needs reference... isnt this generated? 
   */
  def secret: String
  
  /**
   * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-2.2
   */
  def redirectUri: String
}

/**
 * Locate a registered client. This could be from anywhere but assuming
 * its a database or other persistance store then the clientId should
 * be used as the key. 
 */
trait ClientStore {
  def client(clientId: String, secret: Option[String]): Option[Client]
}
