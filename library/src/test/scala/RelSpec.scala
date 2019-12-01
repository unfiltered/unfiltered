package unfiltered.response.link

import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class RelSpec extends AnyPropSpec with ScalaCheckDrivenPropertyChecks with Matchers {
  property("Relation types are addable") {
    forAll(ParamGen.genNonEmptyRels) { (rels: List[Rel]) =>
      val allTypes = rels.map(_.relType).mkString(" ")
      val compositeType = rels.reduceLeft(_ :+ _).relType
      allTypes should be(compositeType)
    }
  }
}
