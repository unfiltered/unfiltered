package unfiltered.jetty

import ch.qos.logback.access.jetty.RequestLogImpl
import org.eclipse.jetty.server.{NCSARequestLog, RequestLog}

trait RequestLogging {
  def requestLog: RequestLog
}

case class FileRequestLogging (
  filename: String,
  extended: Boolean,
  dateFormat: String,
  timezone: String,
  retainDays: Int
) extends RequestLogging {
  override def requestLog: RequestLog = {
    val requestLog = new NCSARequestLog(filename)
    requestLog.setRetainDays(retainDays);
    requestLog.setExtended(extended);
    requestLog.setLogTimeZone(timezone);
    requestLog
  }
}

case class LogbackRequestLogging(logbackConfigFileName: String) extends RequestLogging {
  override def requestLog: RequestLog = {
    val requestLog = new RequestLogImpl
    requestLog.setResource(s"/$logbackConfigFileName")
    requestLog.start()
    requestLog
  }
}