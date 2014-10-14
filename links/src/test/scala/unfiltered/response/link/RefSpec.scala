package unfiltered.response.link

import org.scalacheck.Gen
import org.scalatest.{ Matchers, PropSpec }
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class RefSpec extends PropSpec with GeneratorDrivenPropertyChecks with Matchers {

  property(s"'${Param.Rel.name}' parameters don't persist as members of Ref#params")  {
    forAll(genRef) { ref =>
      ref.params.find(isRel) should be(empty)
    }
  }

  property(s"'${Param.Rel.name}' parameters are collapsed into Ref#rel")  {
    forAll(ParamGen.genRel, ParamGen.genParams) { (rel, params) =>
      whenever(params.exists(isRel)) {
        val ref = Ref("", rel, params:_*)
        val explicitRelTypes = params.filter(isRel).map(_.paramType)
        val computedRelStrings = ref.rel.relType.split(" ")
        explicitRelTypes.size should be(computedRelStrings.size - 1)
      }
    }
  }

  notMoreThanOnce(Param.Media)
  notMoreThanOnce(Param.Title)
  notMoreThanOnce(Param.TitleStar)
  notMoreThanOnce(Param.ContentType)

  def notMoreThanOnce(paramType: Param.Type): Unit = {
    property(s"'${paramType.name}' parameter occurs no more than once") {
      forAll(genRef) { ref =>
        val params = ref.params.filter(_.paramType == paramType)
        params.size should be < 2
      }
    }
  }

  def isRel: Param => Boolean = _.paramType == Param.Rel

  def genRef: Gen[Ref] =
    for {
      uri <- Gen.alphaStr
      rel <- ParamGen.genRel
      params <- ParamGen.genParams
    } yield Ref(uri, rel, params:_*)

}
