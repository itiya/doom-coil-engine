package domain.client.order.single

import domain.client.order.OrderSetting.DefaultOrderSetting
import domain.client.order._

sealed trait SingleOrder extends Order {
  val side: Side
  val price: Option[Int]
  val size: Double
}

object SingleOrder {

  case class Market(side: Side, size: Double, setting: OrderSetting = DefaultOrderSetting) extends SingleOrder {
    override val price: Option[Int] = None
  }

  case class Limit(side: Side, _price: Int, size: Double, setting: OrderSetting = DefaultOrderSetting) extends SingleOrder {
    override val price: Option[Int] = Some(_price)
  }

  case class StopLimit(side: Side, _price: Int, trigger: Int, size: Double, setting: OrderSetting = DefaultOrderSetting) extends SingleOrder {
    override val price: Option[Int] = Some(_price)
  }
}
