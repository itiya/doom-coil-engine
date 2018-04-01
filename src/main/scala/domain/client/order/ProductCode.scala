package domain.client.order

sealed trait ProductCode

object ProductCode {

  case object BtcJpyFx extends ProductCode

  case object BtcUsdFx extends ProductCode

}
