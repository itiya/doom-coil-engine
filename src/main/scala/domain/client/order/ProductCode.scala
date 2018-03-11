package domain.client.order

sealed trait ProductCode

object ProductCode {

  case object BtcJpyFx extends ProductCode

}
