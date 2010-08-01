package unfiltered.scalate

// scalatest
import org.scalatest.FlatSpec
import org.scalatest.matchers.MustMatchers

// java
import java.io.{StringWriter, PrintWriter, File}

// scalate
import org.fusesource.scalate.{TemplateEngine, Binding}
import org.fusesource.scalate.support.FileResourceLoader

class ScalateSpec extends FlatSpec with MustMatchers {
	
	"A Template" should "load" in {
        val scalate = Scalate("scalate/src/test/resources/hello.ssp")
        
        val buffer = new StringWriter
        val writer = new PrintWriter(buffer)
        scalate.write(writer)
        
        buffer.toString must equal ("<h1>Hello, World!</h1>")
    }
    
    it should "accept an implicit engine" in {
        implicit val myEngine = new TemplateEngine
        myEngine.resourceLoader = new FileResourceLoader(Some(new File("./scalate/src/test/resources/alternate/")))
        val scalate = Scalate("another_test_template.ssp")
        
        val buffer = new StringWriter
        val writer = new PrintWriter(buffer)
        scalate.write(writer)
        
        buffer.toString must equal ("<h1>Another Template!</h1>")
    }
    
    it should "accept implicit bindings" in {
        implicit val bindings: List[Binding] = List(Binding(name = "foo", className = "String"))
        implicit val additionalAttributes = List(("foo", "bar"))
        
        val scalate = Scalate("scalate/src/test/resources/bindings.ssp")
        
        val buffer = new StringWriter
        val writer = new PrintWriter(buffer)
        scalate.write(writer)
        
        buffer.toString must equal ("bar")
    }

}
