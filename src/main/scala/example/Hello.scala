package example

import java.math.BigInteger
import java.nio.file._
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import scala.util.{Failure, Success, Try}
import scalaj.http._

case class DoomConfiguration(bitFlyerApiKey: String, bitFlyerApiSecret: String)

object Hello extends Greeting with App {
  val confFiles = Set(Paths.get("./application.conf"))

  Try(pureconfig.loadConfigFromFiles[DoomConfiguration](confFiles)) match {
    case Success(configResult) =>
      configResult match {
        case Right(conf) =>
          println("doom conf from file read success.")
          val timestamp = java.time.ZonedDateTime.now().toEpochSecond.toString
          val method = "GET"
          val path = "/v1/me/getpermissions"
          val body = ""

          val text = timestamp + method + path + body

          val sign = generateHMAC(conf.bitFlyerApiSecret, text)

          val response: HttpResponse[String] = Http("https://api.bitflyer.jp" + path).headers(Seq(("ACCESS-KEY", conf.bitFlyerApiKey), ("ACCESS-TIMESTAMP", timestamp), ("ACCESS-SIGN", sign))).timeout(connTimeoutMs = 5000, readTimeoutMs = 10000).asString
          println(response.body)
        case Left(_) =>
          println("doom conf from file read failed.")
      }


      println(greeting)
    case Failure(e) =>
      println("config load failed: " + e.getMessage)
  }

  def generateHMAC(sharedSecret: String, preHashString: String): String = {
    val secret = new SecretKeySpec(sharedSecret.getBytes, "HmacSHA256")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(secret)
    val hashString: Array[Byte] = mac.doFinal(preHashString.getBytes)
    String.format("%032x", new BigInteger(1, hashString))
  }
}

trait Greeting {
  lazy val greeting: String = "Hello doom coil engine!!!"
}
