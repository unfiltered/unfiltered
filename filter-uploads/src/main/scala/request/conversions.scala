package unfiltered.filter.util

object IteratorConversions {
  import org.apache.commons.{fileupload => fu}
  import fu.{FileItemIterator, FileItemStream}
  import java.util.{Iterator => JIterator}
  
  /** convert apache commons file iterator to scala iterator */
  implicit def acfi2si(i : FileItemIterator) = new FIIteratorWrapper(i)

  /** convert java iterator to scala iterator */
  implicit def ji2si[A](i : JIterator[A]) = new JIteratorWrapper[A](i)

  case class JIteratorWrapper[A](i: JIterator[A]) extends Iterator[A] {
    def hasNext: Boolean = i.hasNext
    def next(): A = i.next
  }

  case class FIIteratorWrapper(i: FileItemIterator) extends Iterator[FileItemStream] {
      def hasNext: Boolean = i.hasNext
      def next(): FileItemStream = i.next
  } 
}
