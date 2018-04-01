package example

import java.nio.file._

import domain.client.order.Side.{Buy, Sell}
import domain.client.order.logic.OrderWithLogic.{IFO, OCO}
import domain.client.order.single.SingleOrder.{Limit, StopLimit}
import infra.client.bitmex.BitMexClient
import infra.client.bitmex.BitMexProductCode.BtcUsdFx

import scala.util.control.Exception._

case class DoomConfiguration(
  bitFlyerApiKey: String,
  bitFlyerApiSecret: String,
  bitMexApiKey: String,
  bitMexApiSecret: String,
  bitMexTestApiKey: String,
  bitMexTestApiSecret: String
)

object Hello extends App {
  val confFiles = Set(Paths.get("./application.conf"))

  for {
    configResult <- (allCatch either pureconfig.loadConfigFromFiles[DoomConfiguration](confFiles))
      .left.map(e => "config load failed: " + e.getMessage).right
    config <- configResult.left.map(_ => "doom conf from file read failed").right
  } yield {
    /*
    val selectedCompanyClient: FinancialCompanyClient = new BitMexClient(config.bitMexApiKey, config.bitMexApiSecret)
    val logic: TradeLogic = new TidalPowerBuyTrade {
      override protected[this] val companyClient: FinancialCompanyClient = selectedCompanyClient
    }
    */

    val bitMexClient: BitMexClient = new BitMexClient(config.bitMexTestApiKey, config.bitMexTestApiSecret, BtcUsdFx)
    val size = 7100*0.1
    val preOrder = Limit(Buy, 7100, size)
    val postOrder = Limit(Sell, 7200, size)
    val postOtherOrder = StopLimit(Sell, 6000, 6000, size)
    val oco = OCO(postOrder, postOtherOrder)
    val ifoOrder = IFO(preOrder, oco)
    println(bitMexClient.postOrderWithLogic(ifoOrder))
  }
}
