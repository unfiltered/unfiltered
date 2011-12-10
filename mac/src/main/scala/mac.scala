package unfiltered.mac

import unfiltered.request._
import unfiltered.response._

object Mac extends Signing {
  def challenge: ResponseFunction[Any] = challenge(None)
  def challenge(err: Option[String]) = Unauthorized ~> WWWAuthenticate("%s%s" format("MAC", err match {
    case Some(e) => """ error="%s"""" format e
    case _ => ""
  }))
}

/** MAC Authorization extractor
 *  See also http://tools.ietf.org/html/draft-ietf-oauth-v2-http-mac-00 */
object MacAuthorization {
  val Id = "id"
  val Nonce = "nonce"
  val BodyHash = "bodyhash"
  val Ext = "ext"
  val MacKey = "mac"

  object MacHeader {
    import QParams._
    val NonceFormat = """^(\d+)[:](\S+)$""".r
    val KeyVal = """(\w+)="([\w|=|:|\/|.|%|-|+]+)" """.trim.r
    val keys = Id :: Nonce :: BodyHash :: Ext :: MacKey :: Nil
    val headerSpace = "MAC" + " "

    def unapply(hvals: List[String]) = hvals match {
      case x :: xs if x startsWith headerSpace =>
        val map = Map(hvals map { _.replace(headerSpace, "") } flatMap {
          case KeyVal(k, v) if(keys.contains(k)) => Seq((k -> Seq(v)))
          case e =>
            Nil
        }: _*)
        val expect = for {
          id <- lookup(Id) is nonempty("id is empty") is required("id is required")
          nonce <- lookup(Nonce) is nonempty("nonce is empty") is required("nonce is required") is
            pred({NonceFormat.findFirstIn(_).isDefined}, _ + " is an invalid format")
          bodyhash <- lookup(BodyHash) is optional[String, String]
          ext <- lookup(Ext) is optional[String, String]
          mac <- lookup(MacKey) is nonempty("mac is nempty") is required("mac is required")
        } yield {
          Some(id.get, nonce.get, bodyhash.get, ext.get, mac.get)
        }
        expect(map) orFail { f =>
          None
        }
      case _ => None
    }
  }

  /** @return (id, nonce, Option[bodyhash], Option[ext], mac)*/
  def unapply[T](r: HttpRequest[T]) = r match {
    case Authorization(value) =>
      value.split(",").toList match {
        case MacHeader(id, nonce, bodyhash, ext, mac) =>
          Some(id, nonce, bodyhash, ext, mac)
        case p => None
      }
    case _ => None
  }
}

/** MAC request signing as defined by
 *  http://tools.ietf.org/html/draft-hammer-oauth-v2-mac-token-05
 *  The MAC protocol defines
 *  1. MAC key identifier (access token value)
 *  2. MAC key (access token secret)
 *  3. MAC algorithm - one of ("hmac-sha-1" or "hmac-sha-256")
 *  4. Issue time - time when credentials were issued to calculate the age
 */
trait Signing {
  import org.apache.commons.codec.binary.Base64.encodeBase64

  val HmacSha1 = "HmacSHA1"
  val HmacSha256 = "HmacSHA256"
  val charset = "UTF8"
  val MacAlgorithms = Map("hmac-sha-1" -> HmacSha1, "hmac-sha-256" -> HmacSha256)
  private val JAlgorithms = Map(HmacSha1 -> "SHA-1", HmacSha256 -> "SHA-256")

  implicit def s2b(s: String) = s.getBytes(charset)

  /** @return Either[String error, String hashed value] */
  def hash(data: Array[Byte])(algo: String) =
    JAlgorithms.get(algo) match {
      case Some(h) =>
        val msg = java.security.MessageDigest.getInstance(h)
        msg.update(data)
        Right(msg.digest)
      case unsup => Left("unsupported algorithm %s" format unsup)
    }

  /** @return Either[String error, String hashed value] */
  def macHash(alg: String, key: String)(body: String) =
    if(MacAlgorithms.isDefinedAt(alg)) {
       val macAlg = MacAlgorithms(alg)
       val mac = javax.crypto.Mac.getInstance(macAlg)
       mac.init(new javax.crypto.spec.SecretKeySpec(key, macAlg))
       Right(new String(encodeBase64(mac.doFinal(body)), charset))
    }
    else Left("unsupported mac algorithm %s" format alg)

  def bodyhash(body: Array[Byte])(alg: String) =
     hash(body)(alg).fold({ Left(_) }, { h => Right(new String(encodeBase64(h), charset)) })

  /** @return signed request for a given key, request, and algorithm */
  def sign[T](r: HttpRequest[T], nonce: String, ext: Option[String],
              bodyHash: Option[String], key: String, alg: String): Either[String, String] =
    requestString(r, alg, nonce, ext, bodyHash).fold({ Left(_) }, { rstr =>
       sign(key, rstr, alg)
    })

  /** @return Either[String error, String mac signed req] */
  def sign(key: String, request: String, alg: String): Either[String, String] =
    macHash(alg, key)(request)

  /** calculates the normalized the request string from a request */
  def requestString[T](r: HttpRequest[T], alg: String,
                       nonce: String, ext: Option[String], bodyHash: Option[String]):
                         Either[String, String] =
    MacAlgorithms.get(alg) match {
      case None => Left("unsupported mac algorithm %s" format alg)
      case Some(macAlg) =>
        r match {
          case HostPort(hostname, port) & r =>
            bodyHash match {
              case Some(bh) => // calculate bodyhash
                 val body = Body.bytes(r)
                 bodyhash(body)(macAlg).fold({ Left(_) }, { bhash =>
                   Right(requestString(nonce, r.method, r.uri,
                                    hostname, port,
                                    bhash,
                                    ext.getOrElse("")))
                 })
              case _ => // don't calculate bodyhash
                Right(requestString(nonce, r.method, r.uri,
                                   hostname, port, "", ext.getOrElse("")))
            }
       }
    }

  /** calculates the normalized request string from parts of a request */
  def requestString(nonce: String, method: String, uri: String, hostname: String,
                    port: Int, bodyhash: String, ext: String): String =
                      nonce :: method :: uri :: hostname :: port ::
                      bodyhash :: ext :: Nil mkString("","\n","\n")
}
