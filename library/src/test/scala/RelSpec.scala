package unfiltered.response.link

import org.scalatest._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class RelSpec extends PropSpec with ScalaCheckDrivenPropertyChecks with Matchers {
  property("Relation types are addable") {
    forAll(ParamGen.genNonEmptyRels) { (rels: List[Rel]) =>
      val allTypes = rels.map(_.relType).mkString(" ")
      val compositeType = rels.reduceLeft(_ :+ _).relType
      allTypes should be(compositeType)
    }
  }
}
