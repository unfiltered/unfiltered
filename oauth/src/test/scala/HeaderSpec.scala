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
      val extracted = extractedOpt.get.map {
        // Seq equivalence seems broken in 2.7.7, just unSeq
        case (k, v) => k -> v(0)
      }
      extracted must havePairs(
        "realm" -> "Example",
        ConsumerKey -> "0685bd9184jfhq22",
        TokenKey -> "ad180jjd733klru7",
        SignatureMethod -> "HMAC-SHA1",
        Sig -> "wOJIO9A2W5mFwDgiDvZbTSMK%2FPY%3D",
        Timestamp -> "137131200",
        Nonce -> "4572616e48616d6d65724c61686176",
        Callback -> "oob",
        Verifier -> "asdfasdfasd",
        Version -> "1.0"
      )
      extracted must notHavePair(ConsumerKey, "malformed")
      extracted must notHaveKey("non_protocol_param")
    }
  }
}
