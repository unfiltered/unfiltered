package unfiltered.jetty.ajp

@deprecated("Use unfiltered.jetty.Server", since="0.8.1")
case class Ajp(port: Int) extends unfiltered.jetty.JettyBase {
  val conn = new org.eclipse.jetty.ajp.Ajp13SocketConnector()
  def ports = port :: Nil
  conn.setPort(port)
  val url = "http://%s:%d/" format(conn.getHost(), port)
  underlying.addConnector(conn)
}
