package infra.client.bitflyer

import infra.client.ProductCode

sealed trait BitFlyerProductCode extends ProductCode

object BitFlyerProductCode {

  case object BtcJpyFx extends BitFlyerProductCode

}
