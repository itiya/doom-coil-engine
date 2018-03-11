package domain.client.order.single

import domain.client.order.TimeInForce.GTC
import domain.client.order.{Order, ProductCode, Side, TimeInForce}

sealed trait SingleOrder extends Order {
  val productCode: ProductCode
  val side: Side
  val price: Option[Int]
  val size: Double
  val expireMinutes: Int
  val timeInForce: TimeInForce
}

object SingleOrder {
  val defaultExpireMinutes = 43200

  case class Market(productCode: ProductCode, side: Side, size: Double) extends SingleOrder {
    override val price: Option[Int] = None
    override val expireMinutes: Int = defaultExpireMinutes
    override val timeInForce: TimeInForce = GTC
  }

  case class Limit(productCode: ProductCode, side: Side, _price: Int, size: Double) extends SingleOrder {
    override val price: Option[Int] = Some(_price)
    override val expireMinutes: Int = defaultExpireMinutes
    override val timeInForce: TimeInForce = GTC
  }
}