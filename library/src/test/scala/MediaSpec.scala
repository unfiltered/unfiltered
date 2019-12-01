package unfiltered.response.link

import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class MediaSpec extends AnyPropSpec with ScalaCheckDrivenPropertyChecks with Matchers {
  property("Media formats are addable") {
    forAll(ParamGen.genNonEmptyMedia) { (media: List[Media]) =>
      val allTypes = media.map(_.mediaType).mkString(", ")
      val compositeType = media.reduceLeft(_ :+ _).mediaType
      allTypes should be(compositeType)
    }
  }
}
