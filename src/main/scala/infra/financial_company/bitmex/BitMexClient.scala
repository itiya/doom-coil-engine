package infra.financial_company.bitmex

import domain.Position
import domain.candle.{Candle, CandleSpan}
import domain.client.FinancialCompanyClient
import domain.client.FinancialCompanyClient.{ClientError, InvalidResponse}
import domain.client.order.OrderSetting.DefaultOrderSetting
import domain.client.order.Side.{Buy, Sell}
import domain.client.order.{Order, OrderSetting}
import domain.client.order.logic.OrderWithLogic
import domain.client.order.logic.OrderWithLogic.{IFD, IFO, OCO, Stop}
import domain.client.order.single.SingleOrder
import domain.client.order.single.SingleOrder.{Limit, Market, StopLimit}
import infra.chart_information.cryptowatch.CryptoWatchClient
import infra.client.{Client, Method}
import play.api.libs.json.{JsArray, JsObject, Json}
import sun.reflect.generics.reflectiveObjects.NotImplementedException

import scala.math.abs

abstract class BitMexClient(bitMexApiKey: String, bitMexApiSecret: String, override protected[this] val productCode: BitMexProductCode) extends FinancialCompanyClient {
  self: Client =>

  override protected[this] val baseUrl: String = "https://www.bitmex.com/api/v1"
  //override protected[this] val baseUrl: String = "https://testnet.bitmex.com/api/v1"
  protected[this] val cryptoWatchClient: CryptoWatchClient

  var linkId = 0

  def postOrderWithLogic(logic: OrderWithLogic, setting: OrderSetting): Either[ClientError, Unit] = {
    val parameters = logic match {
      case _: IFD =>
        throw new NotImplementedException()
      case _: OCO =>
        throw new NotImplementedException()
      case IFO(preOrder, postOrders) =>
        ifoConverter(preOrder, postOrders.order, postOrders.otherOrder)
    }
    linkId = linkId + 1
    callPrivateApi(Method.Post, "/order/bulk", Json.obj("orders" -> parameters).toString()).right.map(_ => ())
  }

  def getOrdersWithLogic: Either[ClientError, Seq[Int]] = ???
  def getOrders: Either[ClientError, Seq[OrderWithLogic]] = ???
  def getPositions: Either[ClientError, Seq[Position]] = {
    for {
      response <-callPrivateApi(Method.Get, "/position", "").right
      positionsJsArray <- Json.parse(response).validate[JsArray].asEither.left.map(_ => InvalidResponse(response)).right
    } yield {
      val currentQuantity = positionsJsArray.value.map { rawPosition =>
        (rawPosition \ "currentQty").as[Double]
      }.foldLeft(0.0)((c, z) => c + z)

      if (currentQuantity == 0.0) {
        Seq()
      } else {
        Seq(Position(if (currentQuantity > 0) Buy else Sell, abs(currentQuantity), 0.0))
      }
    }
  }

  def getPosition: Either[ClientError, Position] = {
    for {
      response <-callPrivateApi(Method.Get, "/position", "").right
      positionsJsArray <- Json.parse(response).validate[JsArray].asEither.left.map(_ => InvalidResponse(response)).right
    } yield {
      val currentQuantity = positionsJsArray.value.map { rawPosition =>
        (rawPosition \ "currentQty").as[Double]
      }.foldLeft(0.0)((c, z) => c + z)

      if (currentQuantity == 0.0) {
        Position(Buy, 0.0, 0.0)
      } else {
        Position(if (currentQuantity > 0) Buy else Sell, abs(currentQuantity), 0.0)
      }
    }
  }

  def getBalance: Either[ClientError, Double] = ???
  def getCollateral: Either[ClientError, Double] = ???

  def getBoard: Either[ClientError, Int] = ???

  def getCandles(count: Int, span: CandleSpan): Either[ClientError, Seq[Candle]] = {
    cryptoWatchClient.getCandles(count, span)
  }

  def postCancelSingleOrders(productCode: String): Either[ClientError, Unit] = {
    callPrivateApi(Method.Delete, "/order/all", "").right.map(_ => ())
  }

  def postSingleOrder(singleOrder: SingleOrder, setting: OrderSetting = DefaultOrderSetting): Either[ClientError, Unit] = {
    val parameter = singleOrder match {
      case _: Market =>
        orderToJson(singleOrder, None)
      case _ =>
        throw new NotImplementedException()
    }
    callPrivateApi(Method.Post, "/order", parameter.toString()).right.map(_ => ())
  }

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
        val price = singleOrder.price
        Json.obj(
          "symbol" -> BitMexParameterConverter.productCode(productCode),
          "side" -> BitMexParameterConverter.side(singleOrder.side),
          "ordType" -> preOrderType,
          "orderQty" -> singleOrder.size,
          "price" -> price,
          "clOrdLinkID" -> linkId.toString,
          "contingencyType" -> contingencyType,
          "stopPx" -> stopTrigger
        )
      case stopOrder: Stop =>
        val (preOrderType, stopTrigger) = ("Stop", stopOrder.price)
        Json.obj(
          "symbol" -> BitMexParameterConverter.productCode(productCode),
          "side" -> BitMexParameterConverter.side(stopOrder.side),
          "ordType" -> preOrderType,
          "orderQty" -> stopOrder.size,
          "clOrdLinkID" -> linkId.toString,
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
