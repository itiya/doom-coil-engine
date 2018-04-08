package infra.financial_company.bitflyer

import domain.client.ProductCode

sealed trait BitFlyerProductCode extends ProductCode

object BitFlyerProductCode {

  case object BtcJpyFx extends BitFlyerProductCode

}
