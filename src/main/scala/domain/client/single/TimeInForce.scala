package domain.client.single

sealed trait TimeInForce

object TimeInForce {
  case object GTC extends TimeInForce
  case object IOC extends TimeInForce
  case object FOK extends TimeInForce
}
