# Unfiltered Scalatest

Helps creating feature tests for Unfiltered apps with [scalatest](http://www.scalatest.org).

## Usage

Here is a test written using this support. For a complete example see https://github.com/ppurang/unfiltered-scalatest-example

    class ExampleFeature extends FeatureSpec with unfiltered.scalatest.jetty.Served
                                                  with unfiltered.scalatest.Hosted 
                                                  with GivenWhenThen with Matchers {

      def setup = {
        _.plan(new App)
      }

      feature("rest app") {
        scenario("should validate integers") {
          Given("an invalid int and a valid palindrome as parameters")
            val params = Map("int" -> "x", "palindrome" -> "twistsiwt")
          When("parameters are posted")
            val response = httpx(req(host <<? params))
          Then("status is BAD Request")
            response.code should be(400)
          And("""body includes "x is not an integer" """)
            response.as_string should include("x is not an integer")
        }

        scenario("should validate palindrome") {
          Given("a valid int and an invalid palindrome as parameters")
            val params = Map("int" -> "1", "palindrome" -> "sweets")
          When("parameters are posted")
            val response = httpx(req(host <<? params))
          Then("status is BAD Request")
            response.code should be(400)
          And("""body includes "sweets is not a palindrome" """)
            response.as_string should include("sweets is not a palindrome")
        }
      }
    }


##Limitations

Only Feature type scalatests are supported.
