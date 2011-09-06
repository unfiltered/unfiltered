package unfiltered.scalate

// specs
import org.specs._

// java
import java.io.{ByteArrayOutputStream, File}

// scalate
import org.fusesource.scalate.{TemplateEngine, Binding}
import org.fusesource.scalate.util.FileResourceLoader

// Unfiltered
import unfiltered.request.HttpRequest
import unfiltered.response.{HttpResponse,ResponseWriter}

class ScalateSpec extends Specification {
  
  import org.mockito.Mockito._
  
  val request = {
    val req = mock(classOf[HttpRequest[_]])
    when(req.uri).thenReturn("")
    req
  }
  def responseString(scalate: ResponseWriter) = {
    val res = mock(classOf[HttpResponse[Nothing]])
    val bos = new ByteArrayOutputStream
    when(res.outputStream).thenReturn(bos)
    when(res.charset).thenReturn(HttpResponse.UTF8)
    scalate.respond(res)
    new String(bos.toByteArray)
  }
  
  "A Template" should {
    "load" in {
      val scalate = Scalate(request,
                            "scalate/src/test/resources/hello.ssp")
      responseString(scalate) must_== ("<h1>Hello, World!</h1>")
    }
    "accept an implicit engine" in {
      implicit val myEngine = new TemplateEngine
      myEngine.resourceLoader = new FileResourceLoader(Some(
        new File("./scalate/src/test/resources/alternate/")))
      val scalate = Scalate(request, "another_test_template.ssp")
      responseString(scalate) must_== ("<h1>Another Template!</h1>")
    }
    
    "accept implicit bindings" in {
      implicit val bindings: List[Binding] =
        List(Binding(name = "foo", className = "String"))
      implicit val additionalAttributes = List(("foo", "bar"))
        
      val scalate = Scalate(request,
                            "scalate/src/test/resources/bindings.ssp")
        
      responseString(scalate) must_== ("bar")
    }
  }
  //sbt will not put the Scala compiler onto the classpath unless an explicit reference is made
  private def loadTheScalaCompilerOntoTheClasspath = {
    import scala.tools.nsc._
    new Global(new Settings)
  }
}
