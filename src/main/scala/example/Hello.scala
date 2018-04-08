package example

import java.nio.file._

import domain.candle.CandleSpan
import domain.candle.CandleSpan.OneHour
import domain.client.FinancialCompanyClient
import domain.client.order.OrderSetting.DefaultOrderSetting
import domain.client.order.Side.{Buy, Sell}
import domain.client.order.logic.OrderWithLogic.{IFO, OCO, Stop}
import domain.client.order.single.SingleOrder.{Limit, StopLimit}
import domain.notifier.NotifyLevel.Info
import domain.notifier.{NotifyMessage, Topic}
import domain.trade.ChannelBreakoutTrade
import infra.chart_information.cryptowatch.CryptoWatchClient
import infra.client.RetryableClient
import infra.financial_company.bitflyer.BitFlyerClient
import infra.financial_company.bitflyer.BitFlyerProductCode.BtcJpyFx
import infra.financial_company.bitmex.BitMexClient
import infra.financial_company.bitmex.BitMexProductCode.BtcUsdFx
import infra.notifier.SlackNotifier
import play.api.libs.json._
import play.api.libs.json.OFormat
import play.api.libs.functional.syntax._

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

  case class TestClass(testA: String, testB: Int)

  implicit val testClassFormat: OFormat[TestClass] = (
    (__ \ "testA").format[String] ~
      (__ \ "testB").format[Int]
  ) (TestClass.apply, unlift(TestClass.unapply))

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

//    val bitMexClient: BitMexClient = new BitMexClient(config.bitMexTestApiKey, config.bitMexTestApiSecret, BtcUsdFx)
//    val size = 7100*0.1
//    val preOrder = Limit(Buy, 7100, size)
//    val postOrder = Limit(Sell, 7200, size)
//    val postOtherOrder = StopLimit(Sell, 6000, 6000, size)
//    val oco = OCO(postOrder, postOtherOrder)
//    val ifoOrder = IFO(preOrder, oco)

//    val breakout = new ChannelBreakoutTrade {
//      override protected[this] val companyClient: FinancialCompanyClient = new BitFlyerClient(config.bitFlyerApiKey, config.bitFlyerApiSecret, BtcJpyFx)
//      override protected[this] val channelLength: Int = 18
//      override protected[this] val size: Double = 0.3
//      override protected[this] val span: CandleSpan = OneHour
//      override protected[this] val offset: Double = 100.0
//      override protected[this] val updateSec: Int = 60
//    }
//
//    breakout.trade()

    // val client: FinancialCompanyClient = new BitFlyerClient(config.bitFlyerApiKey, config.bitFlyerApiSecret, BtcJpyFx)
    // println(client.getOrders)
    //new SlackNotifier(config.slackToken, Seq(config.slackNotifyChannel)).notify(NotifyMessage("試験ノティファイだよー！", Seq(Topic("試験アタッチメントタイトルだよ", "こっちは本文が書けるよ！"))), Info)
    println(Json.parse("""{"testA": "test a text", "testB": 114514}""").validateOpt[TestClass])
    val client = new BitFlyerClient(config.bitFlyerApiKey, config.bitFlyerApiSecret, BtcJpyFx) with RetryableClient {
      override protected[this] val cryptoWatchClient: CryptoWatchClient  = new CryptoWatchClient("btcfxjpy") with RetryableClient {
        override val retryCount: Int = 5
        override val delaySec: Int = 2
      }
      override val retryCount: Int = 5
      override val delaySec = 2
    }
    println(client.getCandles(5, OneHour))
  }
}
