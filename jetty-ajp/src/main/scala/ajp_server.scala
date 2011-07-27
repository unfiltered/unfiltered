package unfiltered.jetty.ajp

case class Ajp(port: Int) extends unfiltered.jetty.Server {
  val conn = new org.eclipse.jetty.ajp.Ajp13SocketConnector()
  conn.setPort(port)
  val url = "http://%s:%d/" format(conn.getHost(), port)
  underlying.addConnector(conn)
}
