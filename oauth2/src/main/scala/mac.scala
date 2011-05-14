package unfiltered.oauth2

import unfiltered.request._
import unfiltered.response._

object Mac {
  val HmacSha1 = "HmacSHA1"
  val HmacSha256 = "HmacSHA256"
  val charset = "utf8"
  val Algorithms = Map(HmacSha1 -> "SHA-1", HmacSha256 -> "SHA-256")
  implicit def s2b(s: String) = s.getBytes(charset)
  def base64enc(data: Array[Byte]) =
    new sun.misc.BASE64Encoder().encode(data)

  def hash(str: String)(algo: String) =
    Algorithms.get(algo) match {
      case Some(h) =>
        val msg = java.security.MessageDigest.getInstance(h)
        msg.update(str.getBytes(charset))
        Right(msg.digest)
      case unsup => Left("unsupported algorithm %s" format unsup)
    }

  def macHash(alg: String, key: String)(body: String) =
    if(Algorithms.isDefinedAt(alg)) {
       val mac = javax.crypto.Mac.getInstance(alg)
       mac.init(new javax.crypto.spec.SecretKeySpec(key, alg))
       mac.doFinal(body)
    }

  def bodyhash(body: String)(alg: String) =
     hash(body)(alg).fold({ Left(_) }, { h => Right(base64enc(h)) })

  def sign[T](r: HttpRequest[T], age: Int) = r match {
    case Host(host)  =>
     val payload = scala.io.Source.fromInputStream(r.inputStream)
      payload
      /*val nonce = age :: System.nanoTime.toString :: Nil mkString(":")
      val requestStr = nonce :: r.method.toUpperCase ::
        r.requestURI ? qs ::
        host :: port ::
        bodyHash(payload)("alg").getOrElse("") ::
        ext :: Nil mkString("\n")
      macHash("alg", "mackey")(requestStr)*/
  }
}
