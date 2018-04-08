package infra.chart_information.cryptowatch

import domain.candle.CandleSpan.{OneHour, OneMinute}
import domain.candle.{Candle, CandleSpan}
import domain.client.FinancialCompanyClient.{ClientError, InvalidResponse}
import infra.client.{BaseClient, Method}
import play.api.libs.json.{JsArray, Json}

class CryptoWatchClient(financialCompany: String) extends BaseClient {
  override protected[this] val baseUrl: String = "https://api.cryptowat.ch"

  def getCandles(count: Int, span: CandleSpan): Either[ClientError, Seq[Candle]] = {
    val response = callApi(Method.Get, "/markets/bitflyer/" + financialCompany + "/ohlc", Seq(), "")
    val result = response.right.map { response =>
      val json = Json.parse(response)
      val spanInt = span match {
        case OneHour => 3600
        case OneMinute => 60
        case _ => throw new IllegalArgumentException("未実装のローソク足間隔です")
      }
      val rawCandles = (json \ "result" \ spanInt.toString).validate[JsArray]
      rawCandles.asEither.left.map(_ => InvalidResponse(response))
    }.joinRight
    result.right.map { rawCandles =>
      rawCandles.value.map { rawCandle =>
        val validRawCandle = rawCandle.validate[JsArray].fold(valid => JsArray(), identity).value
        Candle(
          validRawCandle(0).as[Double],
          validRawCandle(1).as[Double],
          validRawCandle(4).as[Double],
          validRawCandle(2).as[Double],
          validRawCandle(3).as[Double]
        )
      }.takeRight(count)
    }
  }
}
