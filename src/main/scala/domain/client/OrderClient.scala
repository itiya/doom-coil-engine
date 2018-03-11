package domain.client

import domain.client.single.SingleOrder

import scalaj.http.HttpResponse

trait OrderClient {
  def getPermissions: HttpResponse[String]
  def getMarkets: HttpResponse[String]
  def getSingleOrders: HttpResponse[String]
  def getCollateral: HttpResponse[String]

  def postSingleOrder(singleOrder: SingleOrder): HttpResponse[String]
  def postCancelAllOrders(productCode: String): HttpResponse[String]
}
