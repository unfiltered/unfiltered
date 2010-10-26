package unfiltered.scalate

import org.fusesource.scalate.{TemplateEngine, Binding}

/**
 * private object that holds the default engine
*/
private[scalate] object ScalateDefaults{
  val defaultTemplateDirs = List(new java.io.File("src/main/resources/templates"))
  implicit val engine = new TemplateEngine(defaultTemplateDirs)
}
 
import unfiltered.response.ResponseWriter
import unfiltered.request.HttpRequest
/**
 * This class will render the given template with the given attributes.
 * An implicit Engine and Bindings can be passed to the constructor via
 * its alternate parameters set.  This is described further in demo-scalate
*/
case class Scalate(request: HttpRequest[_], template: String, attributes:(String,Any)*)
  (
    implicit engine: TemplateEngine = ScalateDefaults.engine, 
    bindings: List[Binding] = List[Binding](),
    additionalAttributes: List[(String, Any)] = List[(String, Any)]()
  ) extends ResponseWriter{

  import java.io.PrintWriter
  def write(writer: PrintWriter): Unit = {
    import org.fusesource.scalate.DefaultRenderContext
    val scalateTemplate = engine.load(template, bindings)
    val context = new DefaultRenderContext(request.requestURI, engine, writer)
    for(attr <- additionalAttributes) context.attributes(attr._1) = attr._2
    for(attr <- attributes) context.attributes(attr._1) = attr._2 
    scalateTemplate.render(context)
  }
}
