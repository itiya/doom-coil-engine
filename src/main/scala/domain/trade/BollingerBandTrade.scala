package domain.trade

import scala.math.sqrt

trait BollingerBandTrade {



  def calcBollingerBand(closes: Seq[Double]): (Double, Double) = {
    val average = closes.foldLeft(0.0)((c, z) => c + z) / closes.length
    val variance = closes.foldLeft((0.0, average))((sumAndAverage, z) => (sumAndAverage._1 + (sumAndAverage._2 - z) * (sumAndAverage._2 - z), average))._1 / closes.length
    val sigma = sqrt(variance)
    println(average)
    println(variance)
    println(sigma)
    (average + sigma * 2, average - sigma * 2)
  }
}
