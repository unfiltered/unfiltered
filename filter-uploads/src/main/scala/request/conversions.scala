package unfiltered.filter.util

object IteratorConversions {
  import org.apache.commons.{fileupload => fu}
  import fu.FileItemIterator
  import fu.FileItemStream

  /** convert apache commons file iterator to scala iterator */
  implicit final class FIIteratorWrapper(i: FileItemIterator) extends Iterator[FileItemStream] {
    def hasNext: Boolean = i.hasNext
    def next(): FileItemStream = i.next
  }
}
