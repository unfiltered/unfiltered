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
}
import ResponsePackage.ResponseFunction

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