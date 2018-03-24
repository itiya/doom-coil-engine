package infra.bitflyer

import java.math.BigInteger
import java.security.InvalidParameterException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import domain.client.FinancialCompanyClient
import domain.client.order.Order
import domain.client.order.single.SingleOrder
import domain.client.order.single.SingleOrder.{Limit, Market}
import domain.client.order.logic.OrderWithLogic
import domain.client.order.logic.OrderWithLogic.{IFD, IFO, OCO}
import infra.Method
import play.api.libs.json._

import scalaj.http.{Http, HttpRequest, HttpResponse}

class BitFlyerClient(bitFlyerApiKey: String, bitFlyerApiSecret: String) extends FinancialCompanyClient {
  def getPermissions: HttpResponse[String] = {
    callPrivateApi(Method.Get, "/v1/me/getpermissions", "")
  }

  def getMarkets: HttpResponse[String] = {
    callPublicApi(Method.Get, "/v1/getmarkets", "")
  }

  def postSingleOrder(singleOrder: SingleOrder): HttpResponse[String] = {
    val body = singleOrderToJson(singleOrder)
    callPrivateApi(Method.Post, "/v1/me/sendchildorder", body)
  }

  def postOrderWithLogic(logic: OrderWithLogic): HttpResponse[String] = {
    val (orderMethod, parameters) = logic match {
      case IFD(_, _, pre, post) =>
        ("IFD", singleOrderToJsonForSpecialOrder(Seq(pre, post)))
      case OCO(_, _, order, otherOrder) =>
        ("OCO", singleOrderToJsonForSpecialOrder(Seq(order, otherOrder)))
      case IFO(_, _, preOrder, postOrder) =>
        ("IFDOCO", singleOrderToJsonForSpecialOrder(Seq(preOrder, postOrder.order, postOrder.otherOrder)))
    }
    val body = Json.obj(
      "order_method" -> orderMethod,
      "minute_to_expire" -> logic.expireMinutes,
      "time_in_force" -> BitFlyerParameterConverter.timeInForce(logic.timeInForce),
      "parameters" -> JsArray(parameters)
    ).toString()
    callPrivateApi(Method.Post, "/v1/me/sendparentorder", body)
  }

  def getSingleOrders: HttpResponse[String] = {
    callPrivateApi(Method.Get, "/v1/me/getchildorders", "")
  }

  def postCancelAllOrders(productCode: String): HttpResponse[String] = {
    val body = Json.obj(
      "product_code" -> productCode
    ).toString()
    callPrivateApi(Method.Post, "/v1/me/cancelallchildorders", body)
  }

  def getCollateral: HttpResponse[String] =
    callPrivateApi(Method.Get, "/v1/me/getcollateral", "")

  def getOrderWithLogic: Either[String, Seq[Int]] = {
    val response = callPrivateApi(Method.Get, "/v1/me/getparentorders?parent_order_state=ACTIVE&product_code=FX_BTC_JPY", "").body
    val json = Json.parse(response)
    (for {
      orders <- json.validate[Seq[JsValue]].asEither.right
    } yield {
      orders.flatMap { order =>
        (order \ "price").validate[Int].asOpt
      }
    }).left.map(_ => response)
  }

  def getBoard: Either[String, Int] = {
    val response = callPrivateApi(Method.Get, "/v1/board?product_code=FX_BTC_JPY", "").body
    val json = Json.parse(response)
    (for {
      price <- (json \ "mid_price").validate[Int].asEither.right
    } yield {
      price
    }).left.map(_ => response)
  }

  private[this] def callPrivateApi(method: Method, path: String, body: String): HttpResponse[String] = {
    val timestamp = java.time.ZonedDateTime.now().toEpochSecond.toString
    val text = timestamp + method.value + path + body

    val sign = generateHMAC(bitFlyerApiSecret, text)

    callApiCommon(method, path, body)
      .headers(Seq(("ACCESS-KEY", bitFlyerApiKey), ("ACCESS-TIMESTAMP", timestamp), ("ACCESS-SIGN", sign), ("Content-Type", "application/json")))
      .asString
  }

  private[this] def callPublicApi(method: Method, path: String, body: String): HttpResponse[String] =
    callApiCommon(method, path, body)
      .asString

  private[this] def callApiCommon(method: Method, path: String, body: String): HttpRequest =
    (method match {
      case Method.Post =>
        Http("https://api.bitflyer.jp" + path)
          .postData(body)
      case Method.Get =>
        Http("https://api.bitflyer.jp" + path)
      case Method.Put =>
        throw new IllegalArgumentException("method put is not implemented in callApiCommon")
      case Method.Delete =>
        throw new IllegalArgumentException("method delete is not implemented in callApiCommon")
    }).method(method.value)
      .timeout(connTimeoutMs = 5000, readTimeoutMs = 10000)

  private[this] def singleOrderToJsonForSpecialOrder(orders: Seq[Order]): Seq[JsObject] = {
    orders.map {
      case singleOrder: SingleOrder =>
        val orderType = singleOrder match {
          case Market(_, _, _) => "MARKET"
          case Limit(_, _, _, _) => "LIMIT"
        }
        val price = singleOrder.price.getOrElse(0)
        Json.obj(
          "product_code" -> BitFlyerParameterConverter.productCode(singleOrder.productCode),
          "condition_type" -> orderType,
          "side" -> BitFlyerParameterConverter.side(singleOrder.side),
          "price" -> price,
          "size" -> singleOrder.size
        )
      case _ => throw new InvalidParameterException("except single order is not implemented")
    }
  }

  private[this] def singleOrderToJson(singleOrder: SingleOrder): String = {
    val orderType = singleOrder match {
      case Market(_, _, _) => "MARKET"
      case Limit(_, _, _, _) => "LIMIT"
    }
    val price = singleOrder.price.getOrElse(0)
    Json.obj(
      "product_code" -> BitFlyerParameterConverter.productCode(singleOrder.productCode),
      "child_order_type" -> orderType,
      "side" -> BitFlyerParameterConverter.side(singleOrder.side),
      "price" -> price,
      "size" -> singleOrder.size,
      "minute_to_expire" ->  singleOrder.expireMinutes,
      "time_in_force" -> BitFlyerParameterConverter.timeInForce(singleOrder.timeInForce)
    ).toString()
  }

  private[this] def generateHMAC(sharedSecret: String, preHashString: String): String = {
    val secret = new SecretKeySpec(sharedSecret.getBytes, "HmacSHA256")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(secret)
    val hashString: Array[Byte] = mac.doFinal(preHashString.getBytes)
    String.format("%032x", new BigInteger(1, hashString))
  }
}
