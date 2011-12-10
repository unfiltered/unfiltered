# Unfiltered Mac Auth

[Mac](http://tools.ietf.org/html/draft-hammer-oauth-v2-mac-token-05) request authentication

Mac is a generic HTTP Authentication based on request signatures. It may be used to access protected resource in the OAuth2 protocol
or in other HTTP based protocols using the Set-Cookie response header and thus is defined in it's own module

The 3 components of a mac that should be provided by a server are

     1 MAC key identifier (OAuth2 access_token)
     2 MAC key - used as the key along with the algoritm as the key of the hmac sig (OAuth2 access_token secret)
     3 MAC algorithm - one of hmac-sha-1 or hmac-sha-256
     4 Issue time - used to calculate the age of a set of credentials


## usage

This module provides an extractor and mac signature verification utility


     def intent: Intent[Any, Any] = {
       case MacAuthorization(id, nonce, bodyhash, ext, mac) & req =>
         tokenSecret(id) match {
           case Some(key) =>
             // compare a signed request with the signature provided
             Mac.sign(req, nonce, ext, bodyhash, key, algorithm).fold({ err =>
               // error signing request...
             }, { sig =>
               if(sig == mac) // request is trust worthy...
               else           // request is untrusted...
             })
           case _ => // could not find token for the provided id
        }
      }
    

## TODO

  * Non-oauth2 Cookie stuff
  * More tests (header parsing regex, body hashing, ect)
