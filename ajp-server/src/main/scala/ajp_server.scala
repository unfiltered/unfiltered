package unfiltered.server

case class Ajp(port: Int) extends Server {
  val conn = new org.eclipse.jetty.ajp.Ajp13SocketConnector()
  conn.setPort(port)
  server.addConnector(conn)
}
