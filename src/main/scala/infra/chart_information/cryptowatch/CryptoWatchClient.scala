package infra.chart_information.cryptowatch

import domain.candle.CandleSpan.{OneHour, OneMinute}
import domain.candle.{Candle, CandleSpan}
import domain.client.FinancialCompanyClient.{ClientError, InvalidResponse}
import infra.client.{Client, Method}
import play.api.libs.json.Json

import scala.util.Try

class CryptoWatchClient(financialCompany: String) {
  self: Client =>

  override protected[this] val baseUrl: String = "https://api.cryptowat.ch"

  def getCandles(count: Int, span: CandleSpan): Either[ClientError, Seq[Candle]] = {
    val response = callApi(Method.Get, "/markets/bitflyer/" + financialCompany + "/ohlc", Seq(), "")
    val result = response.right.map { response =>
      val json = Json.parse(response)
      val spanSec = span match {
        case OneHour => 3600
        case OneMinute => 60
        case _ => throw new IllegalArgumentException("未実装のローソク足間隔です")
      }
      val rawCandles = (json \ "result" \ spanSec.toString).validate[Seq[Seq[Double]]]
      rawCandles.asEither.left.map(_ => InvalidResponse(response))
    }.joinRight
    Try {
      result.right.map { rawCandles =>
        rawCandles.map { rawCandle =>
          Candle(
            rawCandle(0),
            rawCandle(1),
            rawCandle(4),
            rawCandle(2),
            rawCandle(3)
          )
        }.takeRight(count)
      }
    }.toEither.left.map(_ => InvalidResponse(response.toString)).joinRight
  }
}
