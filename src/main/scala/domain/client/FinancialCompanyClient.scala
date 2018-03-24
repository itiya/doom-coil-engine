package domain.client

import domain.client.FinancialCompanyClient.ClientError
import domain.client.order.logic.OrderWithLogic


trait FinancialCompanyClient {
  // def getSingleOrders: HttpResponse[String]
  def getOrdersWithLogic: Either[String, Seq[Int]] // TODO: 注文時の価格以外の情報が必要になったら汎用的なドメインのcase classにする
  // def getCollateral: HttpResponse[String]
  def getBoard: Either[String, Int] // TODO: 板情報が平均価格以外も取れる必要ができたら汎用的なドメインのcase classにする

  // def postSingleOrder(singleOrder: SingleOrder): HttpResponse[String]
  def postOrderWithLogic(logic: OrderWithLogic): Either[ClientError, Unit]
  def postCancelAllOrders(productCode: String): Either[ClientError, Unit]
}

object FinancialCompanyClient {
  sealed trait ClientError { val responseBody: String }
  case class Timeout(responseBody: String) extends ClientError
  case class ErrorResponse(responseBody: String) extends ClientError
}
