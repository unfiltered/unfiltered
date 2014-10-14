package unfiltered.response.link

import org.scalatest.{ Matchers, PropSpec }
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class RelSpec extends PropSpec with GeneratorDrivenPropertyChecks with Matchers {
  property("Relation types are addable") {
    forAll(ParamGen.genNonEmptyRels) { (rels: List[Rel]) =>
      val allTypes = rels.map(_.relType).mkString(" ")
      val compositeType = rels.reduceLeft(_ :+ _).relType
      allTypes should be(compositeType)
    }
  }
}
