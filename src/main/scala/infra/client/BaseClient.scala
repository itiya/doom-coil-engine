package infra.client

import java.math.BigInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import scalaj.http.{Http, HttpRequest}

trait BaseClient {
  protected[this] val baseUrl: String

  protected[this] def callApiCommon(method: Method, path: String, body: String): HttpRequest =
    (method match {
      case Method.Post =>
        Http(baseUrl + path)
          .postData(body)
      case Method.Get =>
        Http(baseUrl + path)
      case Method.Put =>
        throw new IllegalArgumentException("method put is not implemented in callApiCommon")
      case Method.Delete =>
        throw new IllegalArgumentException("method delete is not implemented in callApiCommon")
    }).method(method.value)
      .timeout(connTimeoutMs = 5000, readTimeoutMs = 10000)

  protected[this] def generateHMAC(sharedSecret: String, preHashString: String): String = {
    val secret = new SecretKeySpec(sharedSecret.getBytes, "HmacSHA256")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(secret)
    val hashString: Array[Byte] = mac.doFinal(preHashString.getBytes)
    String.format("%032x", new BigInteger(1, hashString))
  }
}
