package example

import java.nio.file._

import domain.candle.CandleSpan
import domain.candle.CandleSpan.OneHour
import domain.client.FinancialCompanyClient
import domain.notifier.Notifier
import domain.trade.ChannelBreakoutTrade
import infra.chart_information.cryptowatch.CryptoWatchClient
import infra.client.RetryableClient
import infra.financial_company.bitflyer.BitFlyerClient
import infra.financial_company.bitflyer.BitFlyerProductCode.BtcJpyFx
import infra.notifier.SlackNotifier

import scala.util.control.Exception._

case class DoomConfiguration(
  bitFlyerApiKey: String,
  bitFlyerApiSecret: String,
  bitMexApiKey: String,
  bitMexApiSecret: String,
  bitMexTestApiKey: String,
  bitMexTestApiSecret: String,
  slackToken: String,
  slackNotifyChannel: String
)

object Hello extends App {
  val confFiles = Set(Paths.get("./application.conf"))

  for {
    configResult <- (allCatch either pureconfig.loadConfigFromFiles[DoomConfiguration](confFiles))
      .left.map(e => "config load failed: " + e.getMessage).right
    config <- configResult.left.map(_ => "doom conf from file read failed").right
  } yield {

    val breakout = new ChannelBreakoutTrade {
      override protected[this] val companyClient: FinancialCompanyClient = new BitFlyerClient(config.bitFlyerApiKey, config.bitFlyerApiSecret, BtcJpyFx) with RetryableClient {
        override protected[this] val cryptoWatchClient: CryptoWatchClient = new CryptoWatchClient("btcfxjpy") with RetryableClient {
          override val retryCount: Int = 5
          override val delaySec: Int = 2
        }
        override val retryCount: Int = 5
        override val delaySec = 2
      }
      override protected[this] val notifier: Notifier = new SlackNotifier(config.slackToken, Seq(config.slackNotifyChannel))
      override protected[this] val channelLength: Int = 18
      override protected[this] val size: Double = 0.3
      override protected[this] val span: CandleSpan = OneHour
      override protected[this] val offset: Double = 50.0
      override protected[this] val updateSec: Int = 60
      override protected[this] val heartbeatCount: Int = 10
    }

    breakout.trade()
  }
}
