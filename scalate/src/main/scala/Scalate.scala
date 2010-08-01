package unfiltered.scalate

import org.fusesource.scalate.TemplateEngine

/**
 * private object that holds the default engine
**/
private[scalate] object ScalateDefaults{
    implicit val engine = new TemplateEngine
}

import unfiltered.response.ResponseWriter
case class Scalate(template: String)(implicit engine: TemplateEngine = ScalateDefaults.engine) extends ResponseWriter{
    import java.io.PrintWriter
    import org.fusesource.scalate.DefaultRenderContext
    def write(writer: PrintWriter): Unit = {
        val scalateTemplate = engine.load(template)
        val context = new DefaultRenderContext(engine, writer)
        scalateTemplate.render(context)
    }
}