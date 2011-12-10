## OAuth2

An [OAuth 2][oauth2] server implementation based on draft [version 22][version22]

## Usage

OAuth 2 splits [OAuth 1][oauth1]'s Provider model into two key abstractions: an `AuthorizationServer` and a `ResourceServer`.

The **AuthorizationServer** is responsible for responding to client requests to obtain an access grant.

The **ResourceServer** is responsible for responding to authorized client requests to access protected resources.

At a high level, your application configuration may looks something like the following

    object Server {
      def main(args: Array[String]) {
        jetty.Http(8080)
          .context("/oauth/") {
            _.filter(OAuthorization(...))
          }.context("/api") {
            _.filter(Protection(...))
             .filter(new YourAwesomeApi)
          }.run
      }
    }

### Authorization

An Authorization server requires interation with a `Service` which provides it access with data stores for OAuth Clients, ResourceOwners and Access Grants. The Authorizaton server exposes 2 points, `/authoization` and `/token` which represent a user authorization endpoint and token access endpoint respectively. The paths for these endpoints are overridable.

The `Service` represents some abstract server embedding the OAuthorization module.

The Service and stores are injected into the OAuthorization module as an `AuthorizationServer`. An AuthorizationServer respresents the actual authorization server logic while the OAuthorization module provides the extraction of data from Http requests.

The definition of an AuthorizationServer is

     trait AuthorizationServer {
        self: ClientStore with TokenStore with Container =>
     }

To implement your own Authorization, provide implementations of these traits and compose them together into an
AuthorizationServer that can be then injected into the OAuthorization module

     object YourAuthorizationServer extends AuthorizationServer
        with YourClients with YourTokens with YourContainer

And filter the server module as a `filter.Plan`.

     http.filter(Authorization(YourAuthorizationServer))

#### Authorization flows

##### [Authorization Code flow](http://tools.ietf.org/html/draft-ietf-oauth-v2-16#section-4.1)

"The authorization code grant type is suitable for clients capable of
   maintaining their client credentials confidential (for authenticating
   with the authorization server) such as a client implemented on a
   secure server."

1. authorization code request requires `response_type=code`, `client_id`, `redirect_uri` with optional parameters `scope` and `state`

This request will query the containing server for an authenticated resource owner.

If one is not available, the auth server will ask the containing server to authenticate a resource owner before requesting authorization. The containing server
should be able to repeat the initial request given a `RequestBundle` representing the original request

If the user authorizes the request the auth server will generate a token to store the access grant info and return a `code` parameter to the client in the form a query string
parameter appended to the `redirect_uri` provided

If the user denies authorization, the auth server will send a redirect to the `redirect_uri` containing an query string parameter `error=access_denied`. If the request shold fail for other reasons, please refer to the error codes in the [spec](http://tools.ietf.org/html/draft-ietf-oauth-v2-16#section-4.1.2.1)

2. access token request requires `grant_type=authorization_code`, `client_id`, `code`, `redirect_uri` (which matches the one passed into the the previous request)

If valid, the server will return a json encoded response string containing the `access_token` and optional `refresh_token` and `expires_in` properties.
If not valid, the server will return an error response indicated by the [spec](http://tools.ietf.org/html/draft-ietf-oauth-v2-16#section-5.2)


##### [Implicit flow](http://tools.ietf.org/html/draft-ietf-oauth-v2-16#section-4.2)

" The implicit grant type is suitable for clients incapable of
   maintaining their client credentials confidential (for authenticating
   with the authorization server) such as client applications residing
   in a user-agent, typically implemented in a browser using a scripting
   language such as JavaScript."

1. access token request requires `response_type=token`, `client_id`, `redirect_uri` with optional parameters `scope` and `state`

This request will query the containing server for an authenticated resouce ownder

If one is not available, the auth server wil ask the containing server to authenticate a source owner before requesting authorization. The containing server should be able
to reqpest the intial request given a `RequestBundle` respresenting the original request

If the user authorizes the request, the auth server will generate an access token and return it to the client in the form of a redirect to the `redirect_uri` with the `access_token` appended as part of a url fragment along with optional `expires_in`, `scope`, and `state` parameters


##### [Resource Owner flow](http://tools.ietf.org/html/draft-ietf-oauth-v2-16#section-4.3)

"The resource owner password credentials grant type is suitable in
   cases where the resource owner has a trust relationship with the
   client, such as its computer operating system or a highly privileged
   application."

This is a "redirectionless" flow.

1. access token request requires `grant_type=password`, `client_id`, `client_secret`, `username`, `password` and optional `scope` parameter

If the authorization fails the client will recieve an error indicated in the [spec](http://tools.ietf.org/html/draft-ietf-oauth-v2-16#section-5.2)
If the authorization is accepted the auth server will return a json encoded response string with the `access_token` and optional properties `refresh_token` and `expires_in`

##### [Client Credentials flow](http://tools.ietf.org/html/draft-ietf-oauth-v2-16#section-4.4)

This is a "redirectionless" flow.

1.access token request requires `grant_type=client_credentials`, `client_id`, `client_secret` and optional `scope` parameter
If authorization validation fails, the client will recieve an error indicated in the [spec](http://tools.ietf.org/html/draft-ietf-oauth-v2-16#section-5.2)
If authroization validation passes, the client will recieve a json encoded response string with the `access_token` and optional properties `refresh_token` and `expires_in`


#### [Grant type extentions](http://tools.ietf.org/html/draft-ietf-oauth-v2-16#section-4.5)

Not supported

#### [Refreshing access tokens](http://tools.ietf.org/html/draft-ietf-oauth-v2-16#section-6)

Only available to Authorization Code, Resource Owner, and Client credential flows

1. access token request requires `grant_type=refresh_token`, `client_id`, `client_secret`, `refresh_token` and optional parameter `scope`

Contains should assert that the scope must be equal or lesser than the scope originally granted by the resource owner, and if omitted is
treated as equal to the scope originally granted by the resource owner. This prevents the client from gaining additional access to resources without
first getting authorization from the resource owner.

If validation fails, the client will receive an error indicated in the [spec](http://tools.ietf.org/html/draft-ietf-oauth-v2-16#section-5.2)
If validation passes, the client to recieve a similar response to a valid access token request indicated in the [spec](http://tools.ietf.org/html/draft-ietf-oauth-v2-16#section-5.1)


#### Opting out of flows

You can opt out of the provided oauth flows by overriding callback functions associated with a given flow.


     server.context("/oauth") {
       _.filter(new OAuthorization(authServer) {
         override def onToken(...) = BadResponse
       })
     }


### Protection

Protection requires an `AuthSource` defined as

    trait AuthSource {
      def authenticateToken[T](token: AccessToken, request: HttpRequest[T]): Either[String, UserLike]
      def realm: Option[String] = None
    }

The `Protection` trait handles the OAuth protected access verification in front of other Plans.

After a a successful authorization verification, your Plans can extract the resource owner's and client's identity as well as the access scopes from the requets with the `OAuthIdentity` extractor

    svr
     .filter(Protection(authSrc))
     .filter(Planify {
       case OAuthIdentity(user, client, scopes) => ResponseString("authed as %s" format user)
     })

### Implementation Notes

The current spec provides a liberal definition with how a provided `redirect_uri` should be
valided against one registered by a client. This implementation implements a simple `startsWith` validation. To implement your own override `validRedirectUri(provided: String, client: Client)` in your `AuthorizationServer`

    http.filter(Authorization(new YourAuthorizationServer() {
       override def validRedirectUri(provided: String, client: Client) = true // always
    }))

### todo

Try spliting plan cases into named Intent objects

[oauth2]: http://oauth.net/2/
[version22]: http://tools.ietf.org/html/draft-ietf-oauth-v2-22
[oauth1]: http://tools.ietf.org/html/rfc5849
