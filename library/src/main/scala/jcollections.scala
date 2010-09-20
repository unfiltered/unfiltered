package unfiltered

/** Dress a Java Enumeration in Scala Iterator clothing */
class JEnumerationIterator[T](e: java.util.Enumeration[T]) extends Iterator[T] {
  def hasNext: Boolean =  e.hasMoreElements()
  def next: T = e.nextElement()
}

class JIteratorIterator[T](e: java.util.Iterator[T]) extends Iterator[T] {
  def hasNext: Boolean =  e.hasNext()
  def next: T = e.next()
}
