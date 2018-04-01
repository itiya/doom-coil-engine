package infra.client.bitmex

import domain.client.FinancialCompanyClient.{ClientError, ErrorResponse, Timeout}
import domain.client.order.{Order, OrderSetting}
import domain.client.order.logic.OrderWithLogic
import domain.client.order.logic.OrderWithLogic.{IFD, IFO, OCO}
import domain.client.order.single.SingleOrder
import domain.client.order.single.SingleOrder.{Limit, Market, StopLimit}
import infra.client.{BaseClient, Method}
import play.api.libs.json.{JsArray, JsObject, Json}
import sun.reflect.generics.reflectiveObjects.NotImplementedException

import scala.util.Try
import scalaj.http.HttpResponse

class BitMexClient(bitMexApiKey: String, bitMexApiSecret: String, override protected[this] val productCode: BitMexProductCode) extends BaseClient {
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

    (for {
      result <- Try(callPrivateApi(Method.Post, "/order/bulk", Json.obj("orders" -> parameters).toString())).toEither.left.map(e => Timeout(e.getMessage)).right
    } yield {
      if (result.code == 200) Right(())
      else Left(ErrorResponse(result.body))
    }).joinRight
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

  private[this] def callPrivateApi(method: Method, path: String, body: String): HttpResponse[String] = {
    val timestamp = (java.time.ZonedDateTime.now().toEpochSecond + 10).toString
    val text = method.value + "/api/v1" + path + timestamp + body

    val sign = generateHMAC(bitMexApiSecret, text)

    callApiCommon(method, path, body)
      .headers(Seq(("api-key", bitMexApiKey), ("api-expires", timestamp), ("api-signature", sign), ("Content-Type", "application/json")))
      .asString
  }

  private[this] def callPublicApi(method: Method, path: String, body: String): HttpResponse[String] =
    callApiCommon(method, path, body)
      .asString
}
