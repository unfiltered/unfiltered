package unfiltered.response.link

import unfiltered.response.link.{Tag => TagRel}
import org.scalacheck.Gen

object ParamGen {
  val AllRels = About :: Alternate :: Appendix :: Archives :: Author :: Bookmark ::
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

  val AllMedia = Screen :: Tty :: Tv :: Projection :: Handheld :: Print ::
    Braille :: Aural :: All :: Nil

  val genRel: Gen[Rel] = Gen.oneOf(AllRels)
  val genNonEmptyRels: Gen[List[Rel]] = Gen.nonEmptyListOf(genRel)

  val genMedium: Gen[Media] = Gen.oneOf(AllMedia)
  val genNonEmptyMedia: Gen[List[Media]] = Gen.nonEmptyListOf(genMedium)

  val genTitle = Gen.alphaStr.map(Title.apply)

  val genTitleStar = Gen.alphaStr.map(TitleStar.apply)

  val genMediaType = for {
    main <- Gen.alphaStr
    sub <- Gen.alphaStr
  } yield MediaType(main, sub)

  val genParam: Gen[Param] =
    for {
    rel <- Gen.oneOf(AllRels)
    media <- Gen.oneOf(AllMedia)
    title <- genTitle
    titleStar <- genTitleStar
    mediaType <- genMediaType
    param <- Gen.oneOf(rel, media, title, titleStar, mediaType)
  } yield param
  val genParams: Gen[List[Param]] = Gen.listOf(genParam)


}
