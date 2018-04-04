package infra.client.zaif

import infra.client.ProductCode

sealed trait ZaifProductCode extends ProductCode

object ZaifProductCode {
  case object BtcJpyFx extends ZaifProductCode
}
