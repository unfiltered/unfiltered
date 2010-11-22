# Unfiltered OAuth

A implementation of an [OAuth](http://oauth.net/) [final 1.0 version](http://tools.ietf.org/html/rfc5849) Provider for [Unfiltered](http://github.com/n8han/unfiltered#readme) servers.

## Usage

From a high level

    object Main {
      def main(args: Array[String]) {
        val stores = new OAuthStores {
          // ...
        }
        unfiltered.jetty.Http(8080).filter(OAuth(oauthStores)).filter(new YourApp).run
      }
    }
    
`OAuthStores` defines an interface for querying and creating externally dependent objects within the follow types of stores

* NonceStore: Storage for nonces
* TokenStore: Storage for Tokens (Unauthorized, Authorized, and Access)
* ConsumerStore: Storage for Consumers
* UserHost: Host Application hooks and UI templates


# TODO

* work on non-oob testing
* test for utf-8 all the way down
* decode params before ordering them in signature phases
* support plain text sig method
* support rsa sig method
* use QParam for `optional` param checking for oauth_version
* refactor user host support
* examples
* more tests