import unfiltered.request._
import unfiltered.response._

class Test extends unfiltered.Handler ({
  case HTTPS(GET(Path("/admin", req))) => Pass
})
