# Unfiltered Scalate Integration

## Getting Started

Integrating [Scalate](http://scalate.fusesource.org/) templates into your Unfiltered app is dead simple with the Unfiltered Scalate Module.  Here is a quick and dirty example.

    import unfiltered.request._
    import unfiltered.response._
    import unfiltered.jetty._
    import unfiltered.scalate._
    object Server{
      def main(args: Array[String]){
        val server = Http(8080).filter(unfiltered.filter.Planify {
          case req => Ok ~> Scalate(req, "hello.ssp")
        }).run
      }
    }

and the template looks like, and must be placed in src/main/resources/templates:

    <%@ var name: String = "Deron Williams" %>
    <%@ var city: String = "Salt Lake City" %>
    <h1>Hello, ${name} from ${city}</h1>

To replace the name and city variables with your own, you pass tuples of (String, Any) in the Scalate object

    Scalate(req, "hello.ssp", ("name", "Dustin"), ("city", "Paris"))

Next you probably want to serve static assets, like images, css, and javascript files.  To do that, using Jetty as an example, you need to setup a context, and the code would look something like:

    object Server{
      def main(args: Array[String]){
        Http(8080).context("/public"){ ctx: ContextBuilder =>
          ctx.resources(new java.net.URL("file:///Users/molecule/development/scalate_demo/src/main/resources/public"))
        }.filter(unfiltered.filter.Planify {
          case req => Ok ~> Scalate(req, "hello.ssp")
        }).run
      }
    }

now you can reference static assets from your template like /public/main.css or /public/images/logo.png

...and that's about it!

## Further Configuration and Usage

### Custom Template Engine
If you wish to move your templates somewhere else, or you want to configure the org.fusesource.scalate.TemplateEngine for production use, you can create your own TemplateEngine and pass it to the secondary parameters set manually:

    import unfiltered.request._
    import unfiltered.response._
    import unfiltered.jetty._
    import unfiltered.scalate.Scalate
    import org.fusesource.scalate.TemplateEngine

    object Server{
      def main(args: Array[String]){
    
        val templateDirs = List(new java.io.File("/my/own/template/dirs"))
        val scalateMode = "production"
        val engine = new TemplateEngine(templateDirs, scalateMode)
    
        Http(8080).context("/public"){ ctx: ContextBuilder =>
          ctx.resources(new java.net.URL("file:///Users/molecule/development/scalate_demo/src/main/resources/public"))
        }.filter(unfiltered.filter.Planify {
          case req => Ok ~> Scalate(req, "hello.ssp")(engine)
        }).run
      }
    }

or the TemplateEngine can be implicit:

    implicit val engine = new TemplateEngine(templateDirs, scalateMode)

    Http(8080).context("/public"){ ctx: ContextBuilder =>
      ctx.resources(new java.net.URL("file:///Users/molecule/development/scalate_demo/src/main/resources/public"))
    }.filter(unfiltered.filter.Planify {
      case req => Ok ~> Scalate(req, "hello.ssp")
    }).run

### Default Bindings and Attributes

Also, you can include defaultBindings.  This is if you expect something will be needed on all pages:

    import unfiltered.request._
    import unfiltered.response._
    import unfiltered.jetty._
    import unfiltered.scalate.Scalate
    import org.fusesource.scalate.{TemplateEngine, Binding}

    object Server{
      def main(args: Array[String]){
    
        val templateDirs = List(new java.io.File("/my/own/template/dirs"))
        val scalateMode = "production"
        val engine = new TemplateEngine(templateDirs, scalateMode)
    
        val bindings: List[Binding] = List(Binding(name = "foo", className = "String"))
        val additionalAttributes = List(("foo", "bar"))
    
        Http(8080).context("/public"){ ctx: ContextBuilder =>
          ctx.resources(new java.net.URL("file:///Users/molecule/development/scalate_demo/src/main/resources/public"))
        }.filter(unfiltered.filter.Planify {
          case req => Ok ~> Scalate(req, "hello.ssp")(engine, bindings, additionalAttributes)
        }).run
      }
    }

And the bindings and attributes can be implicit as well:

    object Server{
      def main(args: Array[String]){
    
        val templateDirs = List(new java.io.File("/my/own/template/dirs"))
        val scalateMode = "production"
        implicit val engine = new TemplateEngine(templateDirs, scalateMode)
    
        implicit val bindings: List[Binding] = List(Binding(name = "foo", className = "String"))
        implicit val additionalAttributes = List(("foo", "bar"))
    
        Http(8080).context("/public"){ ctx: ContextBuilder =>
          ctx.resources(new java.net.URL("file:///Users/molecule/development/scalate_demo/src/main/resources/public"))
        }.filter(unfiltered.filter.Planify {
          case req => Ok ~> Scalate(req, "hello.ssp")
        }).run
      }
    }