## OAuth2

An OAuth 2 server implementation.

## Usage

OAuth 2 splits the OAuth 1's Provider model into two abstractions: an AuthorizationServer and a ResourceServer.

The **AuthorizationServer** is responsible for responding to client requests to obtain an access grant.

The **ResourceServer** is responsible for responding to authorized client requests to access protected resources.

At a high level, your application configuration may looks something like the following

    object Main {
      def main(args: Array[String]) {
        unfiltered.jetty.Http(8080)
          .context("/oauth/") {
            _.filter(OAuthorization(...))
          }.context("/api") {
            _.filter(Protection(...))
             .filter(new YourAwesomeApi)
          }.run
      }
    }

### Authorization

Authorization requires interation with a `Container` or context server and access to stores with access to Clients, ResourceOwners and Tokens

The `Container` represents the abstract server containing the Authorization module.

The Host and stores are injected into the Authorization as an `AuthorizationServer`. An AuthorizationServer respresents the actual authorization server logic while the Authorization module provides the extraction of data from the Http request.

An AuthorizationServers definition is

     trait AuthorizationServer {
        self: ClientStore with TokenStore with Container =>
     }

To implement your own, provide implementations of these traits and mix them together into an
AuthorizationServer that can be injected into the AuthorizationModule

     object YourAuthorizationServer
        extends AuthorizationServer
        with YourClients with YourTokens with YourContainer

An execute as a Plan.

     http.filter(Authorization(YourAuthorizationServer))

### Protection

Protection requires an `AuthSource` defined as

    trait AuthSource {
      def authenticateToken[T](token: AccessToken, request: HttpRequest[T]): Either[String, UserLike]
      def realm: Option[String] = None
    }

### Implementation Notes

The current spec provides a liberal definition with how a provided `redirect_uri` should be
valided against one registered by a client. This implementation implements a simple `startsWith` validation. To implement your own override `validRedirectUri(provided: String, client: Client)` in your `AuthorizationServer`

    http.filter(Authorization(new YourAuthorizationServer() {
       override def validRedirectUri(provided: String, client: Client) = true // always
    }))
