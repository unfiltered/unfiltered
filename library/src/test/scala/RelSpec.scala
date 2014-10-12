package unfiltered.response.link

import unfiltered.response.link.{Tag => TagRel}

import org.scalacheck.Gen
import org.scalatest._
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class RelSpec extends PropSpec with GeneratorDrivenPropertyChecks with Matchers {
  property("Relation types are addable") {
    forAll(genNonEmptyRels) { (rels: List[Rel]) =>
      val allTypes = rels.map(_.relType).mkString(" ")
      val compositeType = rels.reduceLeft(_ + _).relType
      allTypes should be(compositeType)
    }
  }

  property("Extension types are quoted when containing ; or ,") {
    forAll { (uri: String) =>
      whenever(uri.exists(c => c == ';' || c == ',' )) {
        Extension(uri).uri should be(s""""$uri"""")
      }
    }
  }

  val all = About :: Alternate :: Appendix :: Archives :: Author :: Bookmark ::
    Canonical :: Chapter :: Collection :: Contents :: Copyright ::
    CreateForm :: Current :: Describedby :: Describes :: Disclosure ::
    Duplicate :: Edit :: EditForm :: EditMedia :: Enclosure :: First ::
    Glossary :: Help :: Hosts :: Hub :: Icon :: Index :: Item :: Last ::
    LatestVersion :: License :: Lrdd :: Memento :: Monitor :: MonitorGroup ::
    Next :: NextArchive :: Nofollow :: Noreferrer :: Original :: Payment ::
    PredecessorVersion :: Prefetch :: Prev :: Preview :: Previous ::
    PrevArchive :: PrivacyPolicy :: Profile :: Related :: Replies ::
    Search :: Section :: Self :: Service :: Start :: Stylesheet ::
    Subsection :: SuccessorVersion :: TagRel :: TermsOfService :: Timegate ::
    Timemap :: Type :: Up :: VersionHistory :: Via :: WorkingCopy ::
    WorkingCopyOf :: Nil

  val genRel: Gen[Rel] = Gen.oneOf(all)
  val genNonEmptyRels: Gen[List[Rel]] = Gen.nonEmptyListOf(genRel)

}
