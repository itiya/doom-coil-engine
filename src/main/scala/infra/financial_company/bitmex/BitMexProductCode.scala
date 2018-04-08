package infra.financial_company.bitmex

import domain.client.ProductCode

sealed trait BitMexProductCode extends ProductCode

object BitMexProductCode {
  case object BtcUsdFx extends BitMexProductCode
}
