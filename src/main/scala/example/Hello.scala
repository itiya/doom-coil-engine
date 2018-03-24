package example

import java.nio.file._

import domain.client.FinancialCompanyClient
import domain.trade.{TidalPowerBuyTrade, TradeLogic}
import infra.bitflyer.BitFlyerClient

import scala.util.control.Exception._

case class DoomConfiguration(bitFlyerApiKey: String, bitFlyerApiSecret: String)

object Hello extends App {
  val confFiles = Set(Paths.get("./application.conf"))

  for {
    configResult <- (allCatch either pureconfig.loadConfigFromFiles[DoomConfiguration](confFiles))
      .left.map(e => "config load failed: " + e.getMessage).right
    config <- configResult.left.map(_ => "doom conf from file read failed").right
  } yield {
    val selectedCompanyClient: FinancialCompanyClient = new BitFlyerClient(config.bitFlyerApiKey, config.bitFlyerApiSecret)
    val logic: TradeLogic = new TidalPowerBuyTrade {
      override protected[this] val companyClient: FinancialCompanyClient = selectedCompanyClient
    }

    logic.trade()
  }
}
