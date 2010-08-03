//scalatest
import org.scalatest.FlatSpec
import org.scalatest.matchers.MustMatchers
import org.fusesource.scalate._

class ScalateSpec extends FlatSpec with MustMatchers {
	
	"A Scalate Template" should "load" in {
        val engine = new TemplateEngine
        val template = engine.load("scalate/src/test/resources/hello.ssp")
        val buffer = new java.io.StringWriter()
        val context = new DefaultRenderContext(engine, new java.io.PrintWriter(buffer))
        template.render(context)
        buffer.toString must equal("<h1>Hello, World!</h1>")
    }
}
