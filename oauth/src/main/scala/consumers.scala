package unfiltered.oauth

trait Consumer {
  val key: String
  val secret: String
}

trait ConsumerStore {
  def get(key: String): Option[Consumer]
}
