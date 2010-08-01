package unfiltered.scalate

import org.fusesource.scalate.{TemplateEngine, Binding}

/**
 * private object that holds the default engine
**/
private[scalate] object ScalateDefaults{
    implicit val engine = new TemplateEngine
}

import unfiltered.response.ResponseWriter
case class Scalate
        (
            template: String, 
            attributes:(String,Any)*
        )
        (
            implicit engine: TemplateEngine = ScalateDefaults.engine, 
            bindings: List[Binding] = List[Binding](),
            additionalAttributes: List[(String, Any)] = List[(String, Any)]()
        ) extends ResponseWriter{

    import java.io.PrintWriter
    import org.fusesource.scalate.DefaultRenderContext
    def write(writer: PrintWriter): Unit = {
        val scalateTemplate = engine.load(template, bindings)
        val context = new DefaultRenderContext(engine, writer)
        for(attr <- additionalAttributes) context.attributes(attr._1) = attr._2
        for(attr <- attributes) context.attributes(attr._1) = attr._2 
        scalateTemplate.render(context)
    }
}