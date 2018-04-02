package domain.candle

sealed trait CandleSpan

object CandleSpan {
  case object OneMinute extends CandleSpan
  case object ThreeMinutes extends CandleSpan
  case object FiveMinutes extends CandleSpan
  case object FifteenMinutes extends CandleSpan
  case object ThirtyMinutes extends CandleSpan
  case object OneHour extends CandleSpan
  case object TwoHours extends CandleSpan
  case object FourHours extends CandleSpan
  case object SixHours extends CandleSpan
}
