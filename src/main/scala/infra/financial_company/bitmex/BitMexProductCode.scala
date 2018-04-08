package infra.financial_company.bitmex

import infra.financial_company.ProductCode

sealed trait BitMexProductCode extends ProductCode

object BitMexProductCode {
  case object BtcUsdFx extends BitMexProductCode
}
