package unfiltered.response

/** An Option-like Monad with 3 statess
 * Present - notion of an expected value being present
 * Absent - notion of an expected value being absent
 * Addressed - notion of a absent value, recognized and addressed
 */
sealed trait Contents[+A] {
  self =>
  
  /** @return true when Present, false otherwise*/
  def isEmpty: Boolean
  
  /** Opposite of isEmpty */
  def isDefined: Boolean = !isEmpty
  
  /** @return underlying value */
  def get: A
  
  /** @return an alterative Contents if not Present */
  def orElse[B >: A](alt: => Contents[B]): Contents[B] = 
     if (isEmpty) alt else this
     
  /** Applies function f to value when Present */
  def map[B](f: A => B): Contents[B] = if(isEmpty) Absent else Present(f(this.get))

   /** Applies function f to value when Present */
  def flatMap[B](f: A => Contents[B]): Contents[B] = if(isEmpty) Absent else f(this.get)

   /** Applies function f to value when Present */
  def foreach[U](f: A => U): Unit = { 
    if(!isEmpty) f(this.get) 
  }
  
  /** Filters underlying value when Present */
  def filter(f: A => Boolean): Contents[A] =
    if(isEmpty || f(this.get)) this else Absent
  
  /** Provides a delegate handler for calls to #withFilter */
  class WithFilter(p: A => Boolean) {
    def map[B](f: A => B): Contents[B] = self.filter(p).map(f)
    def flatMap[B](f: A => Contents[B]): Contents[B] = self.filter(p).flatMap(f)
    def foreach[U](f: A => U): Unit = self.filter(p).foreach(f)
    def withFilter(q: A => Boolean): WithFilter =
      new WithFilter(x => p(x) && q(x))
  }
  
  /** Called with conditional statement provided in for comprehension */
  def withFilter(p: A => Boolean): WithFilter = new WithFilter(p)
  
  /** Transforms a Content's state (by default, this does nothing) */
  def ~>[T](f: T): Contents[A] = this
}

/** Represents a value that is present */
case class Present[+A](x: A) extends Contents[A] {
  def isEmpty = false
  def get = x
}

/** Represents a value that is absent */
case object Absent extends Contents[Nothing] {
  def isEmpty = true
  def get = throw new NoSuchElementException("Absent.get")
  /** address an absent value with f */
  override def ~>[T](f: T): Contents[Nothing] = Addressed(f)
}

/** Represents an absent value addressed with a function */
case class Addressed[T](handler: T) extends Contents[Nothing] {
  type A = Nothing
  
  def isEmpty = true
  def get = throw new NoSuchElementException("Addressed.get, use instead Addressed.handler")
  
  /** Ignore f */
  override def map[B](f: A => B): Contents[B] = this

  /** Ignore f */
  override def flatMap[B](f: A => Contents[B]): Contents[B] = this

  /** Ignore f */
  override def foreach[U](f: A => U): Unit = this
}

object Contents {
  /** @return Absent if x is a seq of strings or null, Present otherwise */
  def apply[A](x: A): Contents[A] = x match {
    case s: Seq[String] => if(s.isEmpty) Absent else Present(x) 
    case _ => if(x == null) Absent else Present(x) 
  }
  // might want to define an implicit fns for determining what  present  meanms 
  //def apply[A](x: A)(f: PartialFunction[A, Boolean]): Contents[A] = if(f.isDefinedAt(x) && f(x)) Present(x) else Absent
}