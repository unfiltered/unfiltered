package unfiltered.oauth

trait NonceStore {
  /** @return true if unique, false otherwise */
  def put(consumer: String, timestamp: String, nonce: String): Boolean 
}
