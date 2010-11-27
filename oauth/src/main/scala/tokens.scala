package unfiltered.oauth

trait Token {
  val key: String
  val secret: String
}

case class RequestToken(key: String, secret: String, consumerKey: String, callback: String) extends Token

case class AuthorizedRequestToken(key: String, secret: String, consumerKey: String, user: String, verifier: String) extends Token

case class AccessToken(key: String, secret: String, user: String, consumerKey: String) extends Token

trait TokenStore {
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
  def delete(tokenId: String): Unit
}

/** Provides random generation of token attributes  */
trait TokenGenerator {
  val generatorLength = 42
  def generate = (gen(generatorLength), gen(generatorLength))
  def generateVerifier = gen(generatorLength)
  implicit val tokenChars = ((0 to 9) ++ ('a' to 'z') ++ ('A' to 'Z'))
  /** gen an n length string or random chars 0-9,a-z or provided set of seq characters */
  protected def gen(n: Int)(implicit tokenChars: Seq[AnyVal]) = 
   (((List[AnyVal](), tokenChars.toList, new java.util.Random) /: (0 until n)) ((a,e) => 
    (a._2(a._3.nextInt(a._2.size)) :: a._1, a._2, a._3))
   )._1.mkString
}

trait DefaultTokenStore extends TokenStore with TokenGenerator
