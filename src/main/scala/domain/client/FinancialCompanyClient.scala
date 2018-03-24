package domain.client

import domain.client.order.logic.OrderWithLogic
import domain.client.order.single.SingleOrder

import scalaj.http.HttpResponse

trait FinancialCompanyClient {
  def getSingleOrders: HttpResponse[String]
  def getOrderWithLogic: Either[String, Seq[Int]]
  def getCollateral: HttpResponse[String]
  def getBoard: Either[String, Int]

  def postSingleOrder(singleOrder: SingleOrder): HttpResponse[String]
  def postOrderWithLogic(logic: OrderWithLogic): HttpResponse[String]
  def postCancelAllOrders(productCode: String): HttpResponse[String]
}
