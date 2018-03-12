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

    val profit = 30000

    val rangeTop = Seq(1200000, 1090000).min //1100000
    val rangeBottom = 750000 //660000
    println(rangeTop)
    Range(rangeBottom, rangeTop, 10000).toList.map { buyPrice =>
      //bitFlyerClient.postOrderWithLogic(IFD(43200, GTC, Limit(BtcJpyFx, Buy, buyPrice, 0.01), Limit(BtcJpyFx, Sell, buyPrice + profit, 0.01))).body
      ()
    }
  }).merge

  println(result)
  println(greeting)
}

trait Greeting {
  lazy val greeting: String = "Hello doom coil engine!!!"
}
