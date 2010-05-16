import unfiltered.request._
import unfiltered.response._

object AdminId extends scala.util.matching.Regex(
  "/admin/(%d)+"
)

class Test extends unfiltered.Handler ({
  case HTTPS(GET(Path(AdminId(id), req))) => Pass
})
