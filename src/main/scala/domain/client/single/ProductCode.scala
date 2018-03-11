package domain.client.single

sealed trait ProductCode

object ProductCode {

  case object BtcJpyFx extends ProductCode

}
