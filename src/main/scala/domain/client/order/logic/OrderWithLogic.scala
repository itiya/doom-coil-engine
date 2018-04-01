package domain.client.order.logic

import domain.client.order.{Order, Side}

sealed trait OrderWithLogic extends Order

object OrderWithLogic {
  case class IFD(preOrder: Order, postOrder: Order) extends OrderWithLogic
  case class OCO(order: Order, otherOrder: Order) extends OrderWithLogic
  case class IFO(preOrder: Order, postOrder: OCO) extends OrderWithLogic
  case class Stop(side: Side, price: Int, size: Double) extends OrderWithLogic
}
