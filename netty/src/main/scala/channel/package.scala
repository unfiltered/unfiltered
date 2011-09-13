package unfiltered.netty

package object channel {
  @deprecated("use unfiltered.netty.async package")
  type Plan = async.Plan
  @deprecated("use unfiltered.netty.async package")
  val Plan = async.Plan
  @deprecated("use unfiltered.netty.async package")
  val Planify = async.Planify
}
package channel {
  @deprecated("use unfiltered.netty.async package")
  class Planify(val intent: Plan.Intent)
  extends Plan with ServerErrorResponse
}
