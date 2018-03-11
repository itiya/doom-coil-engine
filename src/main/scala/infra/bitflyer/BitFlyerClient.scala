package infra.bitflyer

import java.math.BigInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import domain.client.OrderClient
import domain.client.single.SingleOrder
import domain.client.single.SingleOrder.{Limit, Market}
import infra.Method
import play.api.libs.json._

import scalaj.http.{Http, HttpRequest, HttpResponse}

class BitFlyerClient(bitFlyerApiKey: String, bitFlyerApiSecret: String) extends OrderClient {
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

  def getSingleOrders: HttpResponse[String] = {
    callPrivateApi(Method.Get, "/v1/me/getchildorders", "")
  }

  def postCancelAllOrders(productCode: String): HttpResponse[String] = {
    val body = Json.obj(
      "product_code" -> productCode
    ).toString()
    println(body)
    callPrivateApi(Method.Post, "/v1/me/cancelallchildorders", body)
  }

  def getCollateral: HttpResponse[String] =
    callPrivateApi(Method.Get, "/v1/me/getcollateral", "")

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
    }).method(method.value)
      .timeout(connTimeoutMs = 5000, readTimeoutMs = 10000)

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
