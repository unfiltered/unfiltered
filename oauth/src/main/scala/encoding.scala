package unfiltered.oauth

trait Encoding {
  import java.net.{URLEncoder, URLDecoder}
  import org.apache.commons.codec.binary.Base64.encodeBase64
  implicit val encoding = "UTF-8"
  
  def encode(raw: String)(implicit encoding: String) =
    URLEncoder.encode(raw, encoding) replace ("+", "%20") replace ("%7E", "~") replace ("*", "%2A")
  def decode(encoded: String)(implicit encoding: String) =
    URLDecoder.decode(encoded, encoding)
  def base64Encode(raw: Array[Byte]) = encodeBase64(raw)
  def bytes(str: String)(implicit encoding: String) = str.getBytes(encoding)
}
object Encoding extends Encoding
