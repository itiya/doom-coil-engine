package example

import java.nio.file._

import domain.client.FinancialCompanyClient
import domain.client.order.ProductCode.BtcJpyFx
import domain.client.order.Side.{Buy, Sell}
import domain.client.order.TimeInForce.GTC
import domain.client.order.single.SingleOrder.Limit
import domain.client.order.logic.OrderWithLogic.{IFD, IFO, OCO}
import infra.bitflyer.BitFlyerClient
import org.joda.time.DateTime

import scala.util.control.Exception._

case class DoomConfiguration(bitFlyerApiKey: String, bitFlyerApiSecret: String)

object Hello extends Greeting with App {
  val confFiles = Set(Paths.get("./application.conf"))

  val result = (for {
    configResult <- (allCatch either pureconfig.loadConfigFromFiles[DoomConfiguration](confFiles))
      .left.map(e => "config load failed: " + e.getMessage).right
    config <- configResult.left.map(_ => "doom conf from file read failed").right
  } yield {
    val companyClient: FinancialCompanyClient = new BitFlyerClient(config.bitFlyerApiKey, config.bitFlyerApiSecret)

    val profit = 30000
    val rangeTopLimit = 1200000
    val rangeBottom = 750000


    while (true) {

      val result = for {
        nowPrice <- companyClient.getBoard
        orders <- companyClient.getOrderWithLogic
      } yield {
        val nowPriceLimit = (nowPrice / 10000) * 10000 + 10000
        val rangeTop = Seq(rangeTopLimit, nowPriceLimit).min
        Range(rangeBottom, rangeTop, 10000).toSet.diff(orders.toSet).map { buyPrice =>
          companyClient.postOrderWithLogic(IFD(43200, GTC, Limit(BtcJpyFx, Buy, buyPrice, 0.01), Limit(BtcJpyFx, Sell, buyPrice + profit, 0.01))).body
        }
      }
      result match {
        case Left(e) => println(DateTime.now().toString() + " " + e)
        case Right(r) => if (r.nonEmpty) println(DateTime.now().toString() + " " + r)
      }
      Thread.sleep(60 * 1000)
    }

  }).merge

  println(result)
  println(greeting)
}

trait Greeting {
  lazy val greeting: String = "Hello doom coil engine!!!"
}
