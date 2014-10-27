package unfiltered.jetty

case class RequestLogging(
  filename: String,
  extended: Boolean,
  dateFormat: String,
  timezone: String,
  retainDays: Int
)