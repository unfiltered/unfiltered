
package unfiltered.scalate

import org.fusesource.scalate.{TemplateEngine, Binding, DefaultRenderContext, RenderContext}

import unfiltered.response.{ResponseWriter}
import unfiltered.request.HttpRequest

import java.io.Writer

/**
 * private object that holds the default engine
*/
private[scalate] object ScalateDefaults{
  val defaultTemplateDirs = List(new java.io.File("src/main/resources/templates"))
  implicit val engine = new TemplateEngine(defaultTemplateDirs)

  implicit def renderContext(req: HttpRequest[_], writer: Writer, engine: TemplateEngine) =
    new DefaultRenderContext(
      unfiltered.util.Optional(req.uri).getOrElse("").split('?')(0),
      engine, new java.io.PrintWriter(writer))
}

object Scalate {
  /* An arity-3 function that constructs an appropriate RenderContext. */
  type RenderContextBuilder[A, B] = (HttpRequest[A], Writer, TemplateEngine) => RenderContext
}

/**
 * This class will render the given template with the given attributes.
 * An implicit Engine and Bindings can be passed to the constructor via
 * its alternate parameters set.  This is described further in demo-scalate
*/
case class Scalate[A, B](request: HttpRequest[A], template: String, attributes:(String,Any)*)
  (
    implicit engine: TemplateEngine = ScalateDefaults.engine,
    contextBuilder: Scalate.RenderContextBuilder[A, B] = ScalateDefaults.renderContext _,
    bindings: List[Binding] = List[Binding](),
    additionalAttributes: List[(String, Any)] = List[(String, Any)]()
  ) extends ResponseWriter {

  def write(writer: Writer) {
    val scalateTemplate = engine.load(template, bindings)
    val context = contextBuilder(request, writer, engine)
    for(attr <- additionalAttributes) context.attributes(attr._1) = attr._2
    for(attr <- attributes) context.attributes(attr._1) = attr._2
    engine.layout(scalateTemplate, context)
  }
}
