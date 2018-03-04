package client

import java.math.BigInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import scalaj.http.{Http, HttpRequest, HttpResponse}

class BitFlyerClient(bitFlyerApiKey: String, bitFlyerApiSecret: String) {
  def getPermissions: HttpResponse[String] = {
    callPrivateApi(Method.Get, "/v1/me/getpermissions", "")
  }

  private[this] def callPrivateApi(method: Method, path: String, body: String): HttpResponse[String] = {
    val timestamp = java.time.ZonedDateTime.now().toEpochSecond.toString
    val text = timestamp + method.value + path + body

    val sign = generateHMAC(bitFlyerApiSecret, text)

    callApiCommon(method, path, body)
      .headers(Seq(("ACCESS-KEY", bitFlyerApiKey), ("ACCESS-TIMESTAMP", timestamp), ("ACCESS-SIGN", sign)))
      .asString
  }

  private[this] def callPublicApi(method: Method, path: String, body: String): HttpResponse[String] =
    callApiCommon(method, path, body)
      .asString

  private[this] def callApiCommon(method: Method, path: String, body: String): HttpRequest =
    Http("https://api.bitflyer.jp" + path)
      .method(method.value)
      .timeout(connTimeoutMs = 5000, readTimeoutMs = 10000)


  private[this] def generateHMAC(sharedSecret: String, preHashString: String): String = {
    val secret = new SecretKeySpec(sharedSecret.getBytes, "HmacSHA256")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(secret)
    val hashString: Array[Byte] = mac.doFinal(preHashString.getBytes)
    String.format("%032x", new BigInteger(1, hashString))
  }
}
