package domain.trade

import domain.Position
import domain.candle.{Candle, CandleSpan}
import domain.client.FinancialCompanyClient
import domain.client.order.Side.{Buy, Sell}
import domain.client.order.logic.OrderWithLogic
import domain.client.order.logic.OrderWithLogic.Stop
import domain.notifier.NotifyLevel.Info
import domain.notifier.{Notifier, NotifyMessage, Topic}

import math.{max, min}

trait ChannelBreakoutTrade extends TradeLogic {
  protected[this] val notifier: Notifier

  protected[this] val channelLength: Int
  protected[this] val size: Double
  protected[this] val span: CandleSpan
  protected[this] val offset: Double
  protected[this] val updateSec: Int
  protected[this] val heartbeatCount: Int

  def trade(): Unit = {
    var count = 0
    while (true) {
      val (candles, orders, positions) = getInfo
      val highAndLow = calcHighAndLow(candles)
      val positionSum = positions.foldLeft(0.0)((z, n) => z + n.size)
      val positionSide = positions.head.side
      orders.headOption.fold
      {
        positionSide match {
          case Buy =>
            postOrder(Stop(Sell, (highAndLow.low - offset).toInt, size * 2))
            println("create buy")
          case Sell =>
            postOrder(Stop(Buy, (highAndLow.high + offset).toInt, size * 2))
            println("create sell")
        }
        ()
      }
      {
        case stop: Stop =>
          if (stop.price != 0.0) {
            stop.side match {
              case Buy =>
                if (stop.price > highAndLow.high + offset) {
                  cancelOrders()
                  postOrder(Stop(Buy, (highAndLow.high + offset).toInt, size * 2))
                  println(stop.price)
                  println(highAndLow.high)
                  println("update buy")
                }
              case Sell =>
                if (stop.price < highAndLow.low - offset) {
                  cancelOrders()
                  postOrder(Stop(Sell, (highAndLow.low - offset).toInt, size * 2))
                  println("update sell")
                }
            }
          }
        case _ => throw new IllegalArgumentException("規定の注文以外が入っています")
      }

      if (count < heartbeatCount) {
        count = count + 1
      } else {
        val collateralInfo = companyClient.getCollateral match {
          case Right(collateral) => collateral.toString + "円だよっ！"
          case Left(_) => "取得に失敗しちゃった……"
        }
        val side = positionSide match {
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

      Thread.sleep(updateSec * 1000)
    }
  }


  private[this] def getInfo: (Seq[Candle], Seq[OrderWithLogic], Seq[Position]) = {
    (for {
      candles <- companyClient.getCandles(channelLength + 1, span).right
      orders <- companyClient.getOrders.right
      positions <- companyClient.getPositions.right
    } yield {
      (candles, orders, positions)
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

  private[this] def postOrder(stop: Stop): Unit = {
    companyClient.postOrderWithLogic(stop) match {
      case Right(_) => ()
      case Left(_) =>
        Thread.sleep(1000)
        postOrder(stop)
    }
  }

}
