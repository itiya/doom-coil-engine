package domain

import domain.client.order.Side

case class Position(side: Side, size: Double, price: Double)
