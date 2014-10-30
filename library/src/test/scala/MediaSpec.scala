package unfiltered.response.link

import org.scalatest._
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class MediaSpec extends PropSpec with GeneratorDrivenPropertyChecks with Matchers {
  property("Media formats are addable") {
    forAll(ParamGen.genNonEmptyMedia) { (media: List[Media]) =>
      val allTypes = media.map(_.mediaType).mkString(", ")
      val compositeType = media.reduceLeft(_ :+ _).mediaType
      allTypes should be(compositeType)
    }
  }
}
