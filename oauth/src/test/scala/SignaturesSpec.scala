package unfiltered.oauth

import org.specs._

object SignaturesSpec extends Specification with Encoding {
  "Signatures" should {
    val consumerKey = "dpf43f3p2l4k3l03"
    val consumerSecret = "kd94hf93k423kf44"
    val params =  Map(
      "realm" -> Seq("Photos"),
      "oauth_callback" -> Seq("http%3A%2F%2Fprinter.example.com%2Fready"),
      "oauth_consumer_key" -> Seq(consumerKey),
      "oauth_nonce" -> Seq("1234"),
      "oauth_timestamp" -> Seq("1294511237"),
      "oauth_version" -> Seq("1.0"),
      "size" -> Seq("original"),
      "file" -> Seq("vacation.jpg")
    )

    "should verify HMAC-SHA1 signature" in {
      Signatures.verify(
        "POST",
        "http://photos.example.net/photos",
        params
          + ("oauth_signature_method" -> Seq("HMAC-SHA1"))
          + ("oauth_signature" -> Seq("Lg3Zbxn7JBCc8FavDkihFCYDWtk%3D")),
        consumerSecret,
        "") must beTrue
    }
    "should very PLAINTEXT signature" in {
      Signatures.verify(
        "POST",
        "http://photos.example.net/photos",
        params
          + ("oauth_signature_method" -> Seq("PLAINTEXT"))
          + ("oauth_signature" -> Seq("%s&%s".format(consumerSecret, ""))),
        consumerSecret,
        ""
      ) must beTrue
    }
  }
}
