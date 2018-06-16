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
    var side = checkPosition
    var count = 60
    while (true) {
      side = tradeImpl(side)
      count += 1
      if (count > 60) {
        count = 0
        val position = getPosition
        val actualSide = position.side match {
          case Buy => "Buy"
          case Sell => "Sell"
        }
        val logicalSide = side.fold("None"){
          case Buy => "Buy"
          case Sell => "Sell"
        }
        notifier.notify(
          NotifyMessage(
            "Keep Alive!",
            Seq(
              Topic("Size", position.size.toString),
              Topic("Actual side", actualSide),
              Topic("Logical side", logicalSide)
            )
          ),
          NotifyLevel.Info
        )
      }
      Thread.sleep(updateSec * 1000)
    }
  }

  private[this] def checkPosition: Option[Side] = {
    val position = getPosition
    if (position.size == 0.0) {
      None
    } else {
      Some(position.side)
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
    val breakChecker = getBreakChecker

    nowSide match {
      case Buy if breakChecker.breakLow =>
        makePosition(Sell, breakChecker.nowChecker.now.low)
        Sell
      case Sell if breakChecker.breakHigh =>
        makePosition(Buy, breakChecker.nowChecker.now.high)
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
              val position = getPosition
              if (position.side != side || position.size == 0.0) {
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
              Thread.sleep(60 * 1000)
              val position = getPosition
              if (position.side != side || position.size == 0.0) {
                makePositionImpl(side, size)
              } else {
                breakOutNotify(side)
              }
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

  case class BreakChecker(nowChecker: HighAndLowAndNow, prevChecker: HighAndLowAndNow) {
    def breakHigh: Boolean = nowChecker.breakHigh || prevChecker.breakHigh
    def breakLow: Boolean = nowChecker.breakLow || prevChecker.breakLow
  }
  case class HighAndLowAndNow(high: Double, low: Double, now: Candle) {
    def breakHigh: Boolean = high < now.high
    def breakLow: Boolean = low > now.low
  }

  private[this] def getBreakChecker: BreakChecker = {
    def calcBreakChecker(candles: Seq[Candle]): BreakChecker = {
      val sortedCandles = candles.sortWith((c1, c2) => c1.time > c2.time)
      val target = sortedCandles.tail.take(channelLength)
      val nowCandle = sortedCandles.head
      val prevTarget = sortedCandles.tail.tail
      val prevCandle = sortedCandles.tail.head
      BreakChecker(
        HighAndLowAndNow(
          target.foldLeft(Double.MinValue)((high, candle) => max(high, candle.high)),
          target.foldLeft(Double.MaxValue)((low, candle) => min(low, candle.low)),
          nowCandle
        ),
        HighAndLowAndNow(
          prevTarget.foldLeft(Double.MinValue)((high, candle) => max(high, candle.high)),
          prevTarget.foldLeft(Double.MaxValue)((low, candle) => min(low, candle.low)),
          prevCandle
        )
      )
    }
    calcBreakChecker(getCandles)
  }

  private[this] def getHighAndLowAndNow: HighAndLowAndNow = {
    def calcHighAndLow(candles: Seq[Candle]): HighAndLowAndNow = {
      val target = candles.sortWith((c1, c2) => c1.time > c2.time).tail.take(channelLength)
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
      candles <- companyClient.getCandles(channelLength + 2, span).right
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
