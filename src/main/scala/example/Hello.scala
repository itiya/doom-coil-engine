package example

import java.nio.file._

import domain.client.single.ProductCode.BtcJpyFx
import domain.client.single.Side.{Buy, Sell}
import domain.client.single.SingleOrder.{Limit, Market}
import infra.bitflyer.BitFlyerClient

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
    //val limitOrder = Limit(BtcJpyFx, Sell, 1000000, 0.001)
    //bitFlyerClient.postSingleOrder(limitOrder)
    bitFlyerClient.getPermissions
  }).merge

  println(result)
  println(greeting)
}

trait Greeting {
  lazy val greeting: String = "Hello doom coil engine!!!"
}
