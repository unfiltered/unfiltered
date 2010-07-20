package unfiltered.response

import javax.servlet.http.HttpServletResponse

object ResponsePackage {
  // make a package object when 2.7 support is dropped
  type ResponseFunction = HttpServletResponse => HttpServletResponse
  
  implicit def crf2rf(cf: unfiltered.response.Contents[ResponseFunction]): ResponseFunction =
     cf match {
       case a: Addressed[ResponseFunction] => a.handler
       case p: Present[ResponseFunction] => p.get
       case Absent => BadRequest
     }
  
  case class OptionHolder[A](o: Option[A]) extends ResponseFunction {
   def apply(res: HttpServletResponse) = res
  }
  
  implicit def o2rm[A](o: Option[A]): ResponseMonad[OptionHolder[A]] = 
    ResponseMonad(OptionHolder(o), List[String]())

  implicit def r2rm[R <: ResponseFunction](r: R): ResponseMonad[R] = 
    ResponseMonad(r, List[String]())

  case class ResponseMonad[A <: ResponseFunction](r: A, errors: List[String]) {
    self =>
 
    /** evaluate f if and only if there are no existing errors */
    //def map[B <: ResponseFunction](f: A => B)  = 
    //  if(errors.isEmpty) ResponseMonad(f(r), errors) else this
      
    /** evaluate f if and only if there are no existing errors */
    def map[B <: ResponseFunction](f: A => ResponseMonad[B]) =
      if(errors.isEmpty) f(r) else this

    /** accumulate errors */
    def flatMap[B <: ResponseFunction](f: A => ResponseMonad[B]) = f(r)

    /** evaluate f if and only if there are no existing errors */
    def orFail[B <: ResponseFunction](f: List[String] => B) = 
      if(errors.isEmpty) this else ResponseMonad(f(errors), List[String]())

    /** accumulate errors */
    def withFail(msg: String) = ResponseMonad(r, msg :: errors)

    /** ignore non-generating for loop */
    def foreach[U](f: A => U): Unit = { }

    /** should responseFn's be filterable? */
    def filter(f: A => Boolean) = this

    /** Provides a delegate handler for calls to #withFilter */
    class WithFilter(p: A => Boolean) {
      //def map[B <: ResponseFunction](f: A => B) = self.filter(p).map(f)
      def map[B <: ResponseFunction](f: A => ResponseMonad[B]) = self.filter(p).map(f)
      def flatMap[B <: ResponseFunction](f: A => ResponseMonad[B]) = self.filter(p).flatMap(f)
      def foreach[U](f: A => U): Unit = self.filter(p).foreach(f)
      def withFilter(q: A => Boolean): WithFilter =
        new WithFilter(x => p(x) && q(x))
     }
    /** Called with conditional statement provided in for comprehension */
    def withFilter(p: A => Boolean): WithFilter = new WithFilter(p)
  }
}
import ResponsePackage._

/** Pass on to the next servlet filter */
object Pass extends ResponseFunction {
  def apply(res: HttpServletResponse) = res
}

trait Responder extends ResponseFunction {
  def apply(res: HttpServletResponse) = { 
    respond(res)
    res
  }
  def respond(res: HttpServletResponse)
  def ~> (that: ResponseFunction) = new ChainResponse(this andThen that)
}
class ChainResponse(f: ResponseFunction) extends Responder {
  def respond(res: HttpServletResponse) = f(res)
}