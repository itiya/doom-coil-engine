package infra.financial_company.bitflyer

import infra.financial_company.ProductCode

sealed trait BitFlyerProductCode extends ProductCode

object BitFlyerProductCode {

  case object BtcJpyFx extends BitFlyerProductCode

}
