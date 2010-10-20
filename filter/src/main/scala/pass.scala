package unfiltered.filter

import unfiltered.request._
import unfiltered.response._
import javax.servlet.http.{HttpServletRequest,HttpServletResponse, HttpServletResponseWrapper}

case class PassAndThenResponseWrapper(underlying:HttpServletResponse) extends HttpServletResponseWrapper(underlying) {
    val output = new java.io.CharArrayWriter
    override def toString = output.toString
    override def getWriter = new java.io.PrintWriter(output)
}

/** Pass on the the next filter then execute `later` after */
case class PassAndThen(later: unfiltered.Roundtrip.Intent[HttpServletRequest]) 
     extends ResponseFunction  {
  def apply[T](res: HttpResponse[T]) = res
  def then(req: HttpRequest[HttpServletRequest]) = later.orElse[HttpRequest[HttpServletRequest], ResponseFunction] { case _ => Pass } (req)
}

/** Companion of PassAndThen(later). Return this in plans to execute a fn later */
object PassAndThen {
  def after[T](later: unfiltered.Roundtrip.Intent[HttpServletRequest]) = 
    PassAndThen(later)
}
