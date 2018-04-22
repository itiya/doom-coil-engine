package domain.trade

import domain.candle.CandleSpan
import domain.client.order.Side.{Buy, Sell}
import domain.client.order.logic.OrderWithLogic.{IFO, OCO, Stop}
import domain.client.order.single.SingleOrder.{Limit, StopLimit}


// 正しい値が出ない、破棄予定
@deprecated
trait RSIScalpingTrade extends TradeLogic {
  protected[this] val updateSec: Int
  protected[this] val rsiLength: Int
  protected[this] val span: CandleSpan
  protected[this] val size: Double
  protected[this] val profit: Int

  def trade(): Unit = {
    while (true) {
      companyClient.getPositions.right.foreach { positions =>
        if (positions.isEmpty) {
          for {
            candles <- companyClient.getCandles(rsiLength + 2, span).right
          } yield {
            val forRSI = candles.sortWith((c1, c2) => c1.time > c2.time).drop(1).map(c => (c.close, c.time))
            val (upperBound, lowerBound) = calcBound(forRSI, 70, 30)
            val upperOrder = IFO(
              Limit(Sell, upperBound.toInt, size),
              OCO(
                Limit(Buy, upperBound.toInt - profit, size),
                StopLimit(Buy, upperBound.toInt + 30, upperBound.toInt + 35, size)
              )
            )
            val lowerOrder = IFO(
              Limit(Buy, lowerBound.toInt, size),
              OCO(
                Limit(Sell, lowerBound.toInt + profit, size),
                StopLimit(Sell, lowerBound.toInt - 30, lowerBound.toInt - 35, size)
              )
            )
            for {
              _ <- companyClient.postCancelSingleOrders("")
              _ <- companyClient.postOrderWithLogic(upperOrder)
              _ <- companyClient.postOrderWithLogic(lowerOrder)
            } yield ()
          }
        }
      }

      Thread.sleep(updateSec * 1000)
    }
  }

  def calcBound(timeSeries: Seq[(Double, Double)], upperRSA: Double, lowerRSA: Double): (Double, Double) = {
    val (a, b) = aAndB(timeSeries)
    val upperDiff = (upperRSA * (a + b) - 100 * a) / (100 - upperRSA)
    val lowerDiff = 100 * a / lowerRSA - (a + b)
    val prePrice = timeSeries.head._1
    (prePrice + upperDiff, prePrice - lowerDiff)
  }

  def calcRsi(timeSeries: Seq[(Double, Double)]): Double = {
    val (preA, preB) = aAndB(timeSeries)
    100 - 100/(1 + (preA / preB))
  }

  def aAndB(timeSeries: Seq[(Double, Double)]): (Double, Double) = {
    val prices = timeSeries
      .sortWith((c1, c2) => c1._2 < c2._2)
      .map(_._1)
    println(prices)
    val diff = prices
      .scanLeft((0.0, prices.head))((diffAndPrePrice, price) => (price - diffAndPrePrice._2, price))
      .drop(2)
      .map(_._1)
    println(diff)
    val (aSeq, bSeq) = diff.partition(n => n > 0)
    val a = aSeq.foldLeft(0.0)((c, z) => c + z)
    val b = bSeq.foldLeft(0.0)((c, z) => c + z)
    (a, -b)
  }
}
