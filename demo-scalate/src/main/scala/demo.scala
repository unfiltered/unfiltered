package unfiltered.demo

import unfiltered.request._
import unfiltered.response._
import unfiltered.scalate._
import org.fusesource.scalate.{Binding, TemplateEngine}
import org.fusesource.scalate.support.FileResourceLoader

class MyUtils{
    def log(message: String) = println(message)
}

object MyBindings{
    implicit val bindings: List[Binding] = 
        List(
            Binding(
                name = "myUtils", 
                className = "unfiltered.demo.MyUtils", 
                importMembers = true
            )
        )
    implicit val additionalAttributes = List(("myUtils", new MyUtils))
}

object MyEngine{
    import java.io.File
    implicit val engine: TemplateEngine = 
        new TemplateEngine(
            rootDir = Some(new File("./demo-scalate/src/main/templates/")),
            mode = "production"
        )
}

case class User(username: String, firstName: String, lastName: String, age: Int)

class PlanDemo extends unfiltered.Plan {
    def filter = {
        case GET(Path("/", req)) =>
            /*
            This is a very standard example.  
            You pass a template and tuples of name value pairs of the arguments you want passed to the view.
            Below, we're passing an instance of User to the view by the name "user", and an instance of String by the name "quote"
            */
            Scalate(
                template = "demo-scalate/src/main/templates/demo.ssp", 
                attributes = ("user", User("heliocentrist", "Galileo", "Galilei", 77)), ("quote", "It is surely harmful to souls to make it a heresy to believe what is proved.")
            )
        case GET(Path("/my-engine", req)) =>
            /*
            This example shows that you can implicitly include your own org.fusesource.scalate.TemplateEngine if you wish settings other than
            the defaults.  The one I've declared above in MyEngine, gives a default directory for templates (notice the template variable in
            the constructor does not include a path), and puts the engine in "production" mode.  Once you've declared it as implicit, 
            you only need to include the declaration before constructing your Scalate instance, as shown below.
            */
            import MyEngine._
            Scalate(
                template = "my-engine.ssp", 
                attributes = ("user", User("voltaire", "François-Marie", "Arouet", 83)), ("quote", "Monsieur l'abbé, I detest what you write, but I would give my life to make it possible for you to continue to write.")
            )
        case GET(Path("/implicit-bindings", req)) =>
            /*
            This example is just like the first, but we're adding implicit bindings.  Say you wanted some utilities, like logging,
            available in all of your views, but you do not like explicitly passing the utils to the view via arguments, like
            ("myUtils", new MyUtils), in every call, and you do not like declaring them in your templates, like
            <%@ val myUtils: MyUtils %>. Instead, you can use implicit bindings.  
            
            Above I've delcared a class called MyUtils that has a function called log.  In MyBindings, I've declared a 
            List[org.fusesource.scalate.Binding], with a binding of "myUtils" to the class name "MyUtils", that imports the methods, so I 
            can call log("message") instead of myUtils.log("message") from the view.  Then I've declared an implicit List[(String, Any)] 
            called additionalAttributes, that includes an instance of MyUtils mapped with the name "myUtils" - this will be 
            added along with the attributes I declared.  When this is done, I simply need to import those bindings/attributes before
            creating the Scalate class, as shown below.  Notice in the template that there is a call to log("we're running....")
            */
            import MyBindings._
            Scalate(
                template = "demo-scalate/src/main/templates/implicit-bindings.ssp", 
                attributes = ("user", User("oldskool", "Socrates", "", 71)), ("quote", "If you offered to let me off this time on condition I am not any longer to speak my mind... I should say to you, 'Men of Athens, I shall obey the Gods rather than you.'")
            )
        case GET(Path("/explicit", req)) =>
            /*
            This example shows that you need not import all of the implicits, but you can explicitly call them yourself in the secondary argument set.
            */
            Scalate(
                template = "explicit.ssp", 
                attributes = ("user", User("jst3w", "John Stuart", "Mill", 66)), ("quote", "We can never be sure that the opinion we are endeavoring to stifle is a false opinion; and if we were sure, stifling it would be an evil still.")
            )(
                engine = MyEngine.engine,
                bindings = MyBindings.bindings,
                additionalAttributes = MyBindings.additionalAttributes
            )
    }
}

object DemoServer {
  def main(args: Array[String]) {
    unfiltered.server.Http(8080).filter(new PlanDemo).context("/2") { _.filter(new PlanDemo) }.run()
  }
}
