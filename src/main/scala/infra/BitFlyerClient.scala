package infra

import java.math.BigInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import play.api.libs.json._

import scalaj.http.{Http, HttpRequest, HttpResponse}

class BitFlyerClient(bitFlyerApiKey: String, bitFlyerApiSecret: String) {
  def getPermissions: HttpResponse[String] = {
    callPrivateApi(Method.Get, "/v1/me/getpermissions", "")
  }

  def getMarkets: HttpResponse[String] = {
    callPublicApi(Method.Get, "/v1/getmarkets", "")
  }

  def postSingleOrder(productCode: String, orderType: String, side: String, price: Int, size: Double, expireMinutes: Int, timeInForce: String): HttpResponse[String] = {
    val body = Json.obj(
      "product_code" -> productCode,
      "child_order_type" -> orderType,
      "side" -> side,
      "price" -> price,
      "size" -> size,
      "minute_to_expire" -> expireMinutes,
      "time_in_force" -> timeInForce
    ).toString()
    println(body)
    callPrivateApi(Method.Post, "/v1/me/sendchildorder", body)
  }

  def getSingleOrders: HttpResponse[String] = {
    callPrivateApi(Method.Get, "/v1/me/getchildorders", "")
  }

  def cancelAllOrders: HttpResponse[String] = {
    val body = Json.obj(
      "product_code" -> "FX_BTC_JPY"
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


  private[this] def generateHMAC(sharedSecret: String, preHashString: String): String = {
    val secret = new SecretKeySpec(sharedSecret.getBytes, "HmacSHA256")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(secret)
    val hashString: Array[Byte] = mac.doFinal(preHashString.getBytes)
    String.format("%032x", new BigInteger(1, hashString))
  }
}
