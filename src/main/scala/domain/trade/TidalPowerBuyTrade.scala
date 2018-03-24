package domain.trade

import domain.client.FinancialCompanyClient
import domain.client.order.ProductCode.BtcJpyFx
import domain.client.order.Side.{Buy, Sell}
import domain.client.order.TimeInForce.GTC
import domain.client.order.logic.OrderWithLogic.IFD
import domain.client.order.single.SingleOrder.Limit
import org.joda.time.DateTime

trait TidalPowerBuyTrade extends TradeLogic {
  protected[this] val companyClient: FinancialCompanyClient

  def trade(): Unit = {
    val profit = 30000
    val rangeTopLimit = 1200000
    val rangeBottom = 750000

    while (true) {
      (for {
        nowPrice <- companyClient.getBoard
        orders <- companyClient.getOrdersWithLogic
      } yield {
        val nowPriceLimit = (nowPrice / 10000) * 10000 + 10000
        val rangeTop = Seq(rangeTopLimit, nowPriceLimit).min
        Range(rangeBottom, rangeTop, 10000).toSet.diff(orders.toSet).map { buyPrice =>
          (for {
            _ <- companyClient.postOrderWithLogic(IFD(43200, GTC, Limit(BtcJpyFx, Buy, buyPrice, 0.01), Limit(BtcJpyFx, Sell, buyPrice + profit, 0.01))).left.map(_.responseBody).right
          } yield {
            buyPrice.toString
          }).merge
        }
      }) match {
        case Left(e) => println(DateTime.now().toString() + " " + e)
        case Right(r) => if (r.nonEmpty) println(DateTime.now().toString() + " " + r)
      }
      Thread.sleep(60 * 1000)
    }
  }

}
