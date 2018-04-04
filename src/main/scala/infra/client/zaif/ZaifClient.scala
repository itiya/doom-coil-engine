package infra.client.zaif

import domain.Position
import domain.candle.{Candle, CandleSpan}
import domain.client.FinancialCompanyClient
import domain.client.order.OrderSetting
import domain.client.order.logic.OrderWithLogic
import infra.client.{BaseClient, Method}
import play.api.libs.json.{JsObject, Json}
import sun.reflect.generics.reflectiveObjects.NotImplementedException

import scalaj.http.HttpResponse

class ZaifClient(zaifApiKey: String, zaifApiSecret: String, override protected[this] val productCode: ZaifProductCode) extends FinancialCompanyClient with BaseClient {

  override protected[this] val baseUrl: String = "https://api.zaif.jp"

  def getOrdersWithLogic: Either[String, Seq[Int]] = {
    throw new NotImplementedException()
  }

  def getOrders: Either[FinancialCompanyClient.ClientError, Seq[OrderWithLogic]] = {
    println(callPrivateApi(Method.Post, "/tlapi", "get_positions", "type=margin").body)
    throw new NotImplementedException()
  }

  def getPositions: Either[FinancialCompanyClient.ClientError, Seq[Position]] = {
    throw new NotImplementedException()
  }

  def getBoard: Either[String, Int] = {
    throw new NotImplementedException()
  }

  def getCandles(count: Int, span: CandleSpan): Either[FinancialCompanyClient.ClientError, Seq[Candle]] = {
    throw new NotImplementedException()
  }

  def postOrderWithLogic(logic: OrderWithLogic, setting: OrderSetting): Either[FinancialCompanyClient.ClientError, Unit] = {
    throw new NotImplementedException()
  }

  def postCancelSingleOrders(productCode: String): Either[FinancialCompanyClient.ClientError, Unit] = {
    throw new NotImplementedException()
  }

  private[this] def callPrivateApi(method: Method, apiGroup: String, methodName: String, parameter: String): HttpResponse[String] = {
    val nonce = java.time.ZonedDateTime.now().toEpochSecond.toString
    val body = "method=" + methodName + "&nonce=" + nonce.toString + "&" + parameter
    val signature = generateHMAC(zaifApiSecret, body, "HmacSHA512")
    callApiCommon(method, apiGroup, body)
      .headers(Seq(("key", zaifApiKey), ("sign", signature)))
      .asString
  }
}
