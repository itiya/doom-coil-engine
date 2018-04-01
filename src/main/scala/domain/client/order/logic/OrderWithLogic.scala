package domain.client.order.logic

import domain.client.order.OrderSetting.DefaultOrderSetting
import domain.client.order.{Order, OrderSetting}

sealed trait OrderWithLogic extends Order

object OrderWithLogic {
  case class IFD(preOrder: Order, postOrder: Order, setting: OrderSetting = DefaultOrderSetting) extends OrderWithLogic
  case class OCO(order: Order, otherOrder: Order, setting: OrderSetting = DefaultOrderSetting) extends OrderWithLogic
  case class IFO(preOrder: Order, postOrder: OCO, setting: OrderSetting = DefaultOrderSetting) extends OrderWithLogic
}
