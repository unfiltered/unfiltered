package unfiltered.filter.util

object IteratorConversions {
  import org.apache.commons.{fileupload => fu}
  import fu.{FileItemIterator, FileItemStream}
  import java.util.{Iterator => JIterator}

  /** convert java iterator to scala iterator */
  implicit final class JIteratorWrapper[A](i: JIterator[A]) extends Iterator[A] {
    def hasNext: Boolean = i.hasNext
    def next(): A = i.next
  }

  /** convert apache commons file iterator to scala iterator */
  implicit final class FIIteratorWrapper(i: FileItemIterator) extends Iterator[FileItemStream] {
    def hasNext: Boolean = i.hasNext
    def next(): FileItemStream = i.next
  }
}
