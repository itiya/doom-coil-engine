package example

import java.nio.file._

import domain.candle.CandleSpan
import domain.candle.CandleSpan.{OneHour, OneMinute}
import domain.notifier.Notifier
import domain.trade._
import infra.chart_information.cryptowatch.CryptoWatchClient
import infra.client.{NormalClient, RetryableClient}
import infra.financial_company.bitmex.BitMexClient
import infra.financial_company.bitmex.BitMexProductCode.BtcUsdFx
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

    val breakout = new ChannelBreakoutForMex {
      override protected[this] val companyClient: BitMexClient = new BitMexClient(config.bitMexApiKey, config.bitMexApiSecret, BtcUsdFx) with NormalClient {
        override protected[this] val cryptoWatchClient: CryptoWatchClient = new CryptoWatchClient("bitmex/btcusd-perpetual-futures") with RetryableClient {
          override val retryCount: Int = 5
          override val delaySec: Int = 2
        }
      }
      override protected[this] val notifier: Notifier = new SlackNotifier(config.slackToken, Seq(config.slackNotifyChannel))
      override protected[this] val channelLength: Int = 18
      override protected[this] val size: Double = 0.3
      override protected[this] val span: CandleSpan = OneHour
      override protected[this] val offset: Int = 1
      override protected[this] val updateSec: Int = 60
      override protected[this] val heartbeatCount: Int = 10
    }

    breakout.trade()
  }
}
