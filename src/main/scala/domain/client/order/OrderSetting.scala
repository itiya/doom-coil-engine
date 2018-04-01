package domain.client.order

sealed trait OrderSetting

object OrderSetting {

  case class SpecificOrderSetting(expireMinutes: Int, timeInForce: TimeInForce) extends OrderSetting

  object DefaultOrderSetting extends OrderSetting

}
