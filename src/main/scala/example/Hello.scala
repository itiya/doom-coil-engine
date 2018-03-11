package example

import java.nio.file._

import infra.BitFlyerClient
import scala.util.control.Exception._

case class DoomConfiguration(bitFlyerApiKey: String, bitFlyerApiSecret: String)

object Hello extends Greeting with App {
  val confFiles = Set(Paths.get("./application.conf"))

  val result = (for {
    configResult <- (allCatch either pureconfig.loadConfigFromFiles[DoomConfiguration](confFiles))
      .left.map(e => "config load failed: " + e.getMessage).right
    config <- configResult.left.map(_ => "doom conf from file read failed").right
  } yield {
    val bitFlyerClient = new BitFlyerClient(config.bitFlyerApiKey, config.bitFlyerApiSecret)
    //bitFlyerClient.postSingleOrder("FX_BTC_JPY", "MARKET", "SELL", 0, 0.001, 43200, "GTC")
    bitFlyerClient.getPermissions
  }).merge

  println(result)
  println(greeting)
}

trait Greeting {
  lazy val greeting: String = "Hello doom coil engine!!!"
}
