package unfiltered.oauth

import org.specs._

object HeaderSpec extends Specification {
  import OAuth._

  "An oAuth Header" should {
    "extract only expected protocol from OAuth Authorization header values" in {
      val values = "OAuth realm=\"Example\"" ::
        "oauth_consumer_key=\"0685bd9184jfhq22\"" ::
        "oauth_consumer_key:\"malformed\"" ::
        "oauth_token=\"ad180jjd733klru7\"" ::
        "oauth_signature_method=\"HMAC-SHA1\"" ::
        "oauth_signature=\"wOJIO9A2W5mFwDgiDvZbTSMK%2FPY%3D\"" ::
        "oauth_timestamp=\"137131200\"" ::
        "oauth_nonce=\"4572616e48616d6d65724c61686176\"" ::
        "oauth_callback=\"oob\"" ::
        "oauth_verifier=\"asdfasdfasd\"" ::
        "oauth_version=\"1.0\"" ::
        "non_protocol_param=\"bogus\"":: Nil
      val extractedOpt = OAuth.Header.unapply(values)
      extractedOpt must beSomething
      val extracted = extractedOpt.get
      extracted must havePair(("realm", Seq("Example")))
      extracted must notHavePair((ConsumerKey, Seq("malformed")))
      extracted must havePair((ConsumerKey, Seq("0685bd9184jfhq22")))
      extracted must havePair((TokenKey, Seq("ad180jjd733klru7")))
      extracted must havePair((SignatureMethod, Seq("HMAC-SHA1")))
      extracted must havePair((Sig, Seq("wOJIO9A2W5mFwDgiDvZbTSMK%2FPY%3D")))
      extracted must havePair((Timestamp, Seq("137131200")))
      extracted must havePair((Nonce, Seq("4572616e48616d6d65724c61686176")))
      extracted must havePair((Callback, Seq("oob")))
      extracted must havePair((Verifier, Seq("asdfasdfasd")))
      extracted must havePair((Version, Seq("1.0")))
      extracted must notHavePair(("non_protocol_param", Seq("bogus")))
    }
  }
}
