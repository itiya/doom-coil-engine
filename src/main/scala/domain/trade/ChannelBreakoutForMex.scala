package domain.trade

import domain.Position
import domain.candle.{Candle, CandleSpan}
import domain.client.FinancialCompanyClient.Timeout
import domain.client.order.Side
import domain.client.order.Side.{Buy, Sell}
import domain.client.order.single.SingleOrder.Market
import domain.notifier.{Notifier, NotifyLevel, NotifyMessage, Topic}
import infra.financial_company.bitmex.BitMexClient

import scala.math.{max, min}

trait ChannelBreakoutForMex {
  protected[this] val companyClient: BitMexClient

  protected[this] val notifier: Notifier
  protected[this] val channelLength: Int
  protected[this] val size: Double
  protected[this] val span: CandleSpan
  protected[this] val offset: Int
  protected[this] val updateSec: Int
  protected[this] val heartbeatCount: Int

  def trade(): Unit = {
    var side = None: Option[Side]
    while (true) {
      side = tradeImpl(side)
      Thread.sleep(updateSec * 1000)
    }
  }

  private[this] def tradeImpl(nowSideOpt: Option[Side]): (Option[Side]) = {
    nowSideOpt.fold {
      // ポジションなしの場合はどちらかにブレイクアウトした場合のみSomeを返す
      if (checkBreakAndMakePosition(Sell) == Sell) {
        if (checkBreakAndMakePosition(Buy) == Buy) {
          None
        } else {
          Some(Sell)
        }
      } else {
        Some(Buy)
      }: Option[Side]
    } { side =>
      Some(checkBreakAndMakePosition(side))
    }
  }

  private[this] def checkBreakAndMakePosition(nowSide: Side): Side = {
    val breakChecker = getHighAndLowAndNow

    nowSide match {
      case Buy if breakChecker.breakLow =>
        makePosition(Sell, breakChecker.now.low)
        Sell
      case Sell if breakChecker.breakHigh =>
        makePosition(Buy, breakChecker.now.high)
        Buy
      case _ =>
        nowSide
    }
  }

  private[this] def makePosition(side: Side, breakValue: Double): Unit = {
    def makePositionImpl(side: Side, size: Double): Unit = {
      companyClient.postSingleOrder(Market(side, size)) match {
        case Right(_) =>
          breakOutNotify(side)
          ()
        case Left(error) =>
          error match {
            case Timeout(_) =>
              Thread.sleep(60 * 1000)
              if (getPosition.side != side) {
                makePositionImpl(side, size)
              } else {
                breakOutNotify(side)
              }
            case _ =>
              notifier.notify(
                NotifyMessage(
                  "レスポンスがおかしい！",
                  Seq(Topic(
                    "エラー詳細",
                    "ポジションを取ろうとしたらこんなエラーが！\n" + error.responseBody))
                ),
                NotifyLevel.Error
              )
          }
      }
    }

    val nowPosition = getPosition
    val nowSize = if (nowPosition.size != 0.0 && side == nowPosition.side) {
      notifier.notify(
        NotifyMessage(
          "ポジションがおかしい！",
          Seq(Topic(
            "エラー詳細",
            "ブレイクアウト検知したのにそのポジションを既に持ってるよ！"))
        ),
        NotifyLevel.Error
      )
      -nowPosition.size
    } else {
      nowPosition.size
    }
    // 現在のポジションを解消 + 指定size(BTC)になるようにサイズを調整
    val targetSize = (nowSize + breakValue * size).toInt
    makePositionImpl(side, targetSize)
  }

  private[this] def breakOutNotify(side: Side): Unit = {
    val sideStr = side match {
      case Buy => "Buy"
      case Sell => "Sell"
    }
    notifier.notify(
      NotifyMessage(
        "Break Out！",
        Seq(Topic("Side", sideStr))
      ),
      NotifyLevel.Info
    )
  }

  case class HighAndLowAndNow(high: Double, low: Double, now: Candle) {
    def breakHigh: Boolean = high < now.high
    def breakLow: Boolean = low > now.low
  }
  private[this] def getHighAndLowAndNow: HighAndLowAndNow = {
    def calcHighAndLow(candles: Seq[Candle]): HighAndLowAndNow = {
      val target = candles.sortWith((c1, c2) => c1.time > c2.time).tail
      val nowCandle = candles.sortWith((c1, c2) => c1.time > c2.time).head
      HighAndLowAndNow(
        target.foldLeft(Double.MinValue)((high, candle) => max(high, candle.high)),
        target.foldLeft(Double.MaxValue)((low, candle) => min(low, candle.low)),
        nowCandle
      )
    }
    calcHighAndLow(getCandles)
  }

  private[this] def getCandles: Seq[Candle] =
    (for {
      candles <- companyClient.getCandles(channelLength + 1, span).right
    } yield {
      candles
    }).left.map { _ =>
      Thread.sleep(1*1000)
      getCandles
    }.merge

  private[this] def getPosition: Position =
    (for {
      positions <- companyClient.getPosition.right
    } yield {
      positions
    }).left.map { _ =>
      Thread.sleep(1 * 1000)
      getPosition
    }.merge

}
