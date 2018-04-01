package domain.client

import domain.{Candle, Position}
import domain.client.FinancialCompanyClient.ClientError
import domain.client.order.OrderSetting
import domain.client.order.OrderSetting.DefaultOrderSetting
import domain.client.order.logic.OrderWithLogic
import domain.client.order.single.SingleOrder


trait FinancialCompanyClient {
  // def getSingleOrders: HttpResponse[String]
  def getOrdersWithLogic: Either[String, Seq[Int]] // TODO: 注文時の価格以外の情報が必要になったら汎用的なドメインのcase classにする
  def getOrders: Either[ClientError, Seq[SingleOrder]]
  def getPositions: Either[ClientError, Seq[Position]]

  // def getCollateral: HttpResponse[String]
  def getBoard: Either[String, Int] // TODO: 板情報が平均価格以外も取れる必要ができたら汎用的なドメインのcase classにする
  def getCandles(count: Int): Either[ClientError, Seq[Candle]]


  // def postSingleOrder(singleOrder: SingleOrder, setting: OrderSetting = DefaultOrderSetting): HttpResponse[String]
  def postOrderWithLogic(logic: OrderWithLogic, setting: OrderSetting = DefaultOrderSetting): Either[ClientError, Unit]
  def postCancelAllOrders(productCode: String): Either[ClientError, Unit]
}

object FinancialCompanyClient {
  sealed trait ClientError { val responseBody: String }
  case class Timeout(responseBody: String) extends ClientError
  case class ErrorResponse(responseBody: String) extends ClientError
  case class InvalidResponse(responseBody: String) extends ClientError
}
