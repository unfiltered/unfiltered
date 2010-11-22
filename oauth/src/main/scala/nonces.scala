package unfiltered.oauth

trait NonceStore {
  def put(consumer: String, timestamp: String, nonce: String): (String, Int)
}