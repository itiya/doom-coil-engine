package domain.client.order.special

import domain.client.order.{Order, TimeInForce}

sealed trait OrderWithLogic {
  val expireMinutes: Int
  val timeInForce: TimeInForce
}

object OrderWithLogic {
  case class IFD(expireMinutes: Int, timeInForce: TimeInForce, preOrder: Order, postOrder: Order) extends OrderWithLogic
  case class OCO(expireMinutes: Int, timeInForce: TimeInForce, order: Order, otherOrder: Order) extends OrderWithLogic
  case class IFO(expireMinutes: Int, timeInForce: TimeInForce, preOrder: Order, postOrder: OCO) extends OrderWithLogic
}
