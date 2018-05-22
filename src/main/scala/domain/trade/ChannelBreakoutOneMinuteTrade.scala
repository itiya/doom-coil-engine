package domain.trade

import domain.Position
import domain.candle.CandleSpan.OneMinute
import domain.candle.{Candle, CandleSpan}
import domain.client.FinancialCompanyClient.Timeout
import domain.client.order.Side
import domain.client.order.Side.{Buy, Sell}
import domain.client.order.logic.OrderWithLogic
import domain.client.order.logic.OrderWithLogic.{IFO, OCO, Stop}
import domain.client.order.single.SingleOrder.Limit
import domain.notifier.NotifyLevel.Info
import domain.notifier.{Notifier, NotifyMessage, Topic}

import scala.math.{max, min}

// 動作がちゃんと確認できてないしまだ使っちゃだめです
trait ChannelBreakoutOneMinuteTrade extends TradeLogic {
  protected[this] val notifier: Notifier

  protected[this] val channelLength: Int = 40
  protected[this] val size: Double = 0.01
  protected[this] val span: CandleSpan = OneMinute
  protected[this] val offset: Double = 1.0
  protected[this] val updateSec: Int = 30
  protected[this] val heartbeatCount: Int = 20
  val profit = 3000
  val stop = 5000
  var count = 0
  var mode: Side = Sell

  def trade(): Unit = {
    while (true) {
      val (candles, positions) = getInfo
      val highAndLow = calcHighAndLow(candles)
      val positionSum = positions.foldLeft(0.0)((z, n) => z + n.size)
      val positionAveragePrice = positions.foldLeft(0.0)((z, n) => z + n.size * n.price) / positionSum

      positions.headOption.fold{
        cancelOrders()
        if (mode == Buy) {
          postOrder(Stop(Sell, (highAndLow.low - offset).toInt, size))
        } else {
          postOrder(Stop(Buy, (highAndLow.high + offset).toInt, size))
        }
        notify(positionSum, None)
      }
      { position =>
        val positionSide = position.side
        if (positionSide != mode) {
          mode = positionSide
        }

        cancelOrders()
        val order = mode match {
          case Buy =>
            val (stopLow, stopSize) = if (highAndLow.low > positionAveragePrice - stop) {
              (highAndLow.low, size * 2)
            } else {
              (positionAveragePrice - stop, size)
            }
            OCO(
              Limit(Sell, (positionAveragePrice + profit).toInt, size),
              Stop(Sell, stopLow.toInt, stopSize)
            )
          case Sell =>
            val (stopHigh, stopSize) = if (highAndLow.high < positionAveragePrice + stop) {
              (highAndLow.high, size * 2)
            } else {
              (positionAveragePrice + stop, size)
            }
            OCO(
              Limit(Buy, (positionAveragePrice - profit).toInt, size),
              Stop(Buy, stopHigh.toInt, stopSize)
            )
        }
        val (_, positions) = getInfo
        val positionSum = positions.foldLeft(0.0)((z, n) => z + n.size)

        positions.headOption.fold {
          ()
        }{ position =>
          if (position.side == positionSide) {
            postProfitOrder(order)
          }
        }

        notify(positionSum, Some(positionSide))
      }

      Thread.sleep(updateSec * 1000)
    }
  }

  private[this] def notify(positionSum: Double, positionSide: Option[Side]): Unit = {
    if (count < heartbeatCount) {
      count = count + 1
    } else {
      val collateralInfo = companyClient.getCollateral match {
        case Right(collateral) => collateral.toString + "円だよっ！"
        case Left(_) => "取得に失敗しちゃった……"
      }
      val side = positionSide.fold("無し"){
        case Buy => "買い"
        case Sell => "売り"
      }
      val positionInfo = "建玉は" + "%.3f".format(positionSum) + "、" + side + "で建ててるよ！"

      notifier.notify(
        NotifyMessage("はーとびーとだよ！", Seq(Topic("評価額", collateralInfo), Topic("建玉", positionInfo))),
        Info
      )
      count = 0
    }
  }


  private[this] def getInfo: (Seq[Candle], Seq[Position]) = {
    (for {
      candles <- companyClient.getCandles(channelLength + 1, span).right
      positions <- companyClient.getPositions.right
    } yield {
      (candles, positions)
    }).left.map { _ =>
      Thread.sleep(1 * 1000)
      getInfo
    }.merge
  }

  case class HighAndLow(high: Double, low: Double)

  private[this] def calcHighAndLow(candles: Seq[Candle]): HighAndLow = {
    val target = candles.sortWith((c1, c2) => c1.time > c2.time).tail
    HighAndLow(
      target.foldLeft(Double.MinValue)((high, candle) => max(high, candle.high)),
      target.foldLeft(Double.MaxValue)((low, candle) => min(low, candle.low))
    )
  }

  private[this] def cancelOrders(): Unit = {
    companyClient.postCancelSingleOrders("FX_BTC_JPY") match {
      case Right(_) => ()
      case Left(_) =>
        Thread.sleep(1000)
        cancelOrders()
    }
  }

  private[this] def postOrder(orderWithLogic: OrderWithLogic): Unit = {
    companyClient.postOrderWithLogic(orderWithLogic) match {
      case Right(_) => ()
      case Left(e) =>
        println(e)
        Thread.sleep(1000)
        postOrder(orderWithLogic)
    }
  }

  private[this] def postProfitOrder(orderWithLogic: OCO): Unit = {
    companyClient.postOrderWithLogic(orderWithLogic) match {
      case Right(_) => ()
      case Left(e) =>
        println(e)
        Thread.sleep(1000)
        val (_, positions) = getInfo
        val positionSum = positions.foldLeft(0.0)((z, n) => z + n.size)

        // クソみたいなコードだが、注文のリトライ前にポジションのサイズとポジションの方向を確認している
        // タイムアウトによる失敗の場合は注文が通っている場合があるため
        if (positionSum > size - 0.000000001) {
          if (positions.head.side != mode) {
            cancelOrders()
            postProfitOrder(orderWithLogic)
          } else {
            ()
          }
        } else {
          ()
        }
    }
  }

}
