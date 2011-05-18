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

Authorization requires interation with a `Container` server and stores with access to Clients, ResourceOwners and Access Grants. It exposes 2 points, `/authoization` and `/token` which represent a user authorization endpoint and token access endpoint. The paths for these endpoints are overridable.

The `Container` represents the abstract server containing the OAuthorization module.

The Container and stores are injected into the OAuthorization module as an `AuthorizationServer`. An AuthorizationServer respresents the actual authorization server logic while the OAuthorization module provides the extraction of data from Http requests.

An AuthorizationServer definition is

     trait AuthorizationServer {
        self: ClientStore with TokenStore with Container =>
     }

To implement your own, provide implementations of these traits and mix them together into an
AuthorizationServer that can be injected into the AuthorizationModule

     object YourAuthorizationServer extends AuthorizationServer
        with YourClients with YourTokens with YourContainer

And filter the server as a Plan.

     http.filter(Authorization(YourAuthorizationServer))

### Protection

Protection requires an `AuthSource` defined as

    trait AuthSource {
      def authenticateToken[T](token: AccessToken, request: HttpRequest[T]): Either[String, UserLike]
      def realm: Option[String] = None
    }

The `Protection` trait handles the OAuth protected access verification in front of other Plans.

After a a successfull authorization verification, your Plans can extract the resource owner's identity and access scopes from the requets with the `OAuthResourceOwner` extractor


    svr
     .filter(Protection(authSrc))
     .filter(Planify {
       case OAuthResourceOwner(idenitity, scopes) => ResponseString("authed as %s" format identity)
     })




### Implementation Notes

The current spec provides a liberal definition with how a provided `redirect_uri` should be
valided against one registered by a client. This implementation implements a simple `startsWith` validation. To implement your own override `validRedirectUri(provided: String, client: Client)` in your `AuthorizationServer`

    http.filter(Authorization(new YourAuthorizationServer() {
       override def validRedirectUri(provided: String, client: Client) = true // always
    }))
