# Unfiltered OAuth

A implementation of an [OAuth](http://oauth.net/) [final 1.0 version][oauth_1_0] Provider for [Unfiltered](http://github.com/n8han/unfiltered#readme) servers.

## Usage

From a high level

    object Main {
      def main(args: Array[String]) {
        val stores = new OAuthStores {
          // ...
        }
        unfiltered.jetty.Http(8080)
          .context("/oauth/") {
           // oauth dance
            _.filter(OAuth(stores))
          }.context("/api") {
           // auth filter
            _.filter(Protected(stores))
             .filter(new YourApi)
          }.run
      }
    }

### OAuthStores

`OAuthStores` defines an interface for querying and creating externally dependent objects within the follow types of stores

#### NonceStore: Storage for nonces

This trait defines one method a provider must implement
     /** @return true if this is a valid unique combination for a nonce, false otherwise */
     def put(consumer: String, timestamp: String, nonce: String): Boolean

#### TokenStore: Storage for Tokens (Unauthorized, Authorized, and Access)

This trait defines the following methods a provider must implement
       /** generate a new key and secret tuple */
       def generate: (String, String)
       /** generate a new oauth verifier */
       def generateVerifier: String
       /** store a token. */
       def put(token: Token): Token
       /** retrieve a token.
        * @return one of None, Some(RequestToken), Some(AuthorizedRequestToken), Some(AccessToken) */
       def get(tokenId: String): Option[Token]
       /** delete a token */
       def delete(tokenId: String): Uni

#### ConsumerStore: Storage for Consumers

This trait defines the following methods a provider must implement
     /** @return Some(Consumer) if available, None otherwise */
     def get(key: String): Option[Consumer]

#### UserHost: Host Application hooks and UI templates

This trait defines the following methods a provider must implement

      /** @return Some(user) if user is logged in, None otherwise */
      def current[T](r: HttpRequest[T]): Option[UserLike]
      /** @return true if app logic determines this request was accepted, false otherwise */
      def accepted[T](token: String, r: HttpRequest[T]): Boolean
      /** @return true if app logic determines this request was denied, false otherwise */
      def denied[T](token: String, r: HttpRequest[T]): Boolean
      /** @return a view asking the user to log in */
      def login(token: String): Html
      /** @return a view to show a user to provide a consumer with a verifier */
      def oobResponse(verifier: String): Html
      /** @return http response for confirming the user's denial was processed */
      def deniedConfirmation(consumer: Consumer): Html
      /** @return a view asking the user to authorize the provided consumer access */
      def requestAcceptance(token: String, consumer: Consumer): Html

## Implementation Notes

### n access_tokens per user + consumer
  The OAuth [final 1.0 version][oauth_1_0] spec does not specify the behavior for a provider in the case
  of the same consumer requesting multiple access_tokens for the same user. One option is to always return the same token. Another option is to delete previous access tokens. While these are both viable options, if a provider would choose to delete previous access tokens the user may be forced to reauthenticate if they were authenticaated with the same consumer in multiple locations. If the server always returned the same access token for the same consumer and user combination, the same consumer used a separate location would have to go through a few needless steps in requesting request_tokens adding additional overhead for the provider.

  This implementation opts to not stand in the way and allows for multiple access_tokens per user and consumer combination. This can be usefull in the case were a user may be authenticated under the same application in multiple locations, each location having its own access_token. This provides a little more flexibility with respect to providing the end user the capability to revoke access to a targetted consumer verses all at once. The only drawback to this approach is that the user host of the provider will be responsible for indicating that there can be multiple connections for a given user and consumer and being able to differentiate them. This decision base based on a discussion on the [twitter api mailing list](http://code.google.com/p/twitter-api/issues/detail?id=372).

# TODO

* work on non-oob testing
* support rsa sig method
* examples
* more tests

[oauth_1_0]: http://tools.ietf.org/html/rfc5849
