package infra.financial_company.bitmex

import domain.Position
import domain.candle.{Candle, CandleSpan}
import domain.client.FinancialCompanyClient
import domain.client.FinancialCompanyClient.{ClientError}
import domain.client.order.{Order, OrderSetting}
import domain.client.order.logic.OrderWithLogic
import domain.client.order.logic.OrderWithLogic.{IFD, IFO, OCO}
import domain.client.order.single.SingleOrder
import domain.client.order.single.SingleOrder.{Limit, Market, StopLimit}
import infra.client.{Client, Method}
import play.api.libs.json.{JsArray, JsObject, Json}
import sun.reflect.generics.reflectiveObjects.NotImplementedException

class BitMexClient(bitMexApiKey: String, bitMexApiSecret: String, override protected[this] val productCode: BitMexProductCode) extends FinancialCompanyClient {
  self: Client =>

  //override val baseUrl: String = "https://www.bitmex.com/api/v1"
  override protected[this] val baseUrl: String = "https://testnet.bitmex.com/api/v1"

  def postOrderWithLogic(logic: OrderWithLogic, setting: OrderSetting): Either[ClientError, Unit] = {
    val parameters = logic match {
      case _: IFD =>
        throw new NotImplementedException()
      case _: OCO =>
        throw new NotImplementedException()
      case IFO(preOrder, postOrders) =>
        ifoConverter(preOrder, postOrders.order, postOrders.otherOrder)
    }
    callPrivateApi(Method.Post, "/order/bulk", Json.obj("orders" -> parameters).toString()).right.map(_ => ())
  }

  def getOrdersWithLogic: Either[ClientError, Seq[Int]] = ???
  def getOrders: Either[ClientError, Seq[OrderWithLogic]] = ???
  def getPositions: Either[ClientError, Seq[Position]] = ???
  def getBalance: Either[ClientError, Double] = ???
  def getCollateral: Either[ClientError, Double] = ???

  def getBoard: Either[ClientError, Int] = ???
  def getCandles(count: Int, span: CandleSpan): Either[ClientError, Seq[Candle]] = ???

  def postCancelSingleOrders(productCode: String): Either[ClientError, Unit] = ???

  private[this] def ifoConverter(preOrder: Order, postOrder: Order, postOtherOrder: Order): JsArray =
    JsArray(Seq(orderToJson(preOrder, Some("OneTriggersTheOther")), orderToJson(postOrder, Some("OneUpdatesTheOtherAbsolute")), orderToJson(postOtherOrder, Some("OneUpdatesTheOtherAbsolute"))))

  private[this] def orderToJson(order: Order, contingencyType: Option[String]): JsObject = {
    order match {
      case singleOrder: SingleOrder =>
        val (preOrderType, stopTrigger) = singleOrder match {
          case _: Market => ("Market", None)
          case _: Limit => ("Limit", None)
          case stopLimit: StopLimit => ("StopLimit", Some(stopLimit.trigger))
        }
        val price = singleOrder.price.get
        Json.obj(
          "symbol" -> BitMexParameterConverter.productCode(productCode),
          "side" -> BitMexParameterConverter.side(singleOrder.side),
          "ordType" -> preOrderType,
          "orderQty" -> singleOrder.size,
          "price" -> price,
          "clOrdLinkID" -> 0.toString,
          "contingencyType" -> contingencyType,
          "stopPx" -> stopTrigger
        )
      case _ => throw new IllegalArgumentException("order is not single order")
    }
  }

  private[this] def callPrivateApi(method: Method, path: String, body: String): Either[ClientError, String] = {
    val timestamp = (java.time.ZonedDateTime.now().toEpochSecond + 10).toString
    val text = method.value + "/api/v1" + path + timestamp + body

    val sign = generateHMAC(bitMexApiSecret, text)

    callApi(method, path, Seq(("api-key", bitMexApiKey), ("api-expires", timestamp), ("api-signature", sign), ("Content-Type", "application/json")), body)
  }

}
