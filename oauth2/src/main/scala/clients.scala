package unfiltered.oauth2

trait Client {
  def id: String
  def secret: String
  def redirectUri: String
}

trait ClientStore {
  def client(clientId: String, secret: Option[String]): Option[Client]
}
