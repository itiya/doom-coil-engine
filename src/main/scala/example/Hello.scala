package example

import java.nio.file._

import domain.client.order.ProductCode.BtcJpyFx
import domain.client.order.Side.{Buy, Sell}
import domain.client.order.TimeInForce.GTC
import domain.client.order.single.SingleOrder.Limit
import domain.client.order.special.OrderWithLogic.{IFD, IFO, OCO}
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
    //val logic = IFO(43200, GTC, Limit(BtcJpyFx, Buy, 950000, 0.001), OCO(43200, GTC, Limit(BtcJpyFx, Buy, 950000, 0.001), Limit(BtcJpyFx, Sell, 1500000, 0.001)))
    //bitFlyerClient.postSpecialOrder(logic)
    bitFlyerClient.getMarkets.body
  }).merge

  println(result)
  println(greeting)
}

trait Greeting {
  lazy val greeting: String = "Hello doom coil engine!!!"
}
