package example

import java.nio.file._

import domain.candle.CandleSpan
import domain.candle.CandleSpan.{OneHour, OneMinute}
import domain.client.FinancialCompanyClient
import domain.client.order.Side.{Buy, Sell}
import domain.client.order.logic.OrderWithLogic.{IFO, OCO, Stop}
import domain.client.order.single.SingleOrder.{Limit, Market}
import domain.notifier.Notifier
import domain.trade.{BollingerBandTrade, ChannelBreakoutOneMinuteTrade, ChannelBreakoutTrade, RSIScalpingTrade}
import infra.chart_information.cryptowatch.CryptoWatchClient
import infra.client.{NormalClient, RetryableClient}
import infra.financial_company.bitflyer.BitFlyerClient
import infra.financial_company.bitflyer.BitFlyerProductCode.BtcJpyFx
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

//    val breakout = new ChannelBreakoutTrade {
//      override protected[this] val companyClient: FinancialCompanyClient = new BitFlyerClient(config.bitFlyerApiKey, config.bitFlyerApiSecret, BtcJpyFx) with RetryableClient {
//        override protected[this] val cryptoWatchClient: CryptoWatchClient = new CryptoWatchClient("bitflyer/btcfxjpy") with RetryableClient {
//          override val retryCount: Int = 5
//          override val delaySec: Int = 2
//        }
//        override val retryCount: Int = 5
//        override val delaySec = 2
//      }
//      override protected[this] val notifier: Notifier = new SlackNotifier(config.slackToken, Seq(config.slackNotifyChannel))
//      override protected[this] val channelLength: Int = 18
//      override protected[this] val size: Double = 0.3
//      override protected[this] val span: CandleSpan = OneHour
//      override protected[this] val offset: Double = 1.0
//      override protected[this] val updateSec: Int = 60
//      override protected[this] val heartbeatCount: Int = 10
//    }
//
//    breakout.trade()


//    val rsi = new RSIScalpingTrade {
//      override protected[this] val rsiLength: Int = 7
//      override protected[this] val updateSec: Int = 30
//
//      override protected[this] val profit: Int = 5
//      override protected[this] val size: Double = 80
//
//      override protected[this] val span: CandleSpan = OneMinute
//      override protected[this] val companyClient: FinancialCompanyClient = new BitMexClient(config.bitMexApiKey, config.bitMexApiSecret, BtcUsdFx) with RetryableClient {
//        override protected[this] val cryptoWatchClient: CryptoWatchClient = new CryptoWatchClient("bitmex/btcusd-perpetual-futures") with RetryableClient {
//          override val retryCount: Int = 5
//          override val delaySec: Int = 2
//        }
//        override val retryCount: Int = 5
//        override val delaySec: Int = 2
//      }
//    }
//    rsi.trade()

//    val client = new BitFlyerClient(config.bitFlyerApiKey, config.bitFlyerApiSecret, BtcJpyFx) with NormalClient {
//      override protected[this] val cryptoWatchClient: CryptoWatchClient = new CryptoWatchClient("bitflyer/btcfxjpy") with RetryableClient {
//        override val retryCount: Int = 5
//        override val delaySec: Int = 2
//      }
//    }
//
//    new ChannelBreakoutOneMinuteTrade {
//      override protected[this] val notifier: Notifier = new SlackNotifier(config.slackToken, Seq(config.slackNotifyChannel))
//      override protected[this] val companyClient: FinancialCompanyClient = client
//    }.trade()

  }
}
