package infra.client

import java.math.BigInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import domain.client.FinancialCompanyClient.{ClientError, ErrorResponse, Timeout}

import scala.util.Try
import scalaj.http.Http

trait Client {
  protected[this] val baseUrl: String

  protected[this] def callApi(method: Method, path: String, headers: Seq[(String, String)], body: String): Either[ClientError, String]

  protected[this] def generateHMAC(sharedSecret: String, preHashString: String, logic: String = "HmacSHA256"): String = {
    val secret = new SecretKeySpec(sharedSecret.getBytes, logic)
    val mac = Mac.getInstance(logic)
    mac.init(secret)
    val hashString: Array[Byte] = mac.doFinal(preHashString.getBytes)
    String.format("%032x", new BigInteger(1, hashString))
  }

  private[client] def callApiImpl(method: Method, path: String, headers: Seq[(String, String)], body: String): Either[ClientError, String] = {
    val request = (method match {
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
      .timeout(connTimeoutMs = 5000, readTimeoutMs = 10000).headers(headers)

    for {
      response <- Try(request.asString).toEither.left.map(e => Timeout(e.getMessage)).right
      _ <- Either.cond(response.code == 200, response.body, ErrorResponse(response.body)).right
    } yield {
      response.body
    }
  }
}
