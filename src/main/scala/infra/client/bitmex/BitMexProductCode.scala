package infra.client.bitmex

import infra.client.ProductCode

sealed trait BitMexProductCode extends ProductCode

object BitMexProductCode {
  case object BtcUsdFx extends BitMexProductCode
}
