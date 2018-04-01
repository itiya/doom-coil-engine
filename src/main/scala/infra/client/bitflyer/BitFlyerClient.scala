package infra.client.bitflyer

import java.security.InvalidParameterException

import domain.{Candle, Position}
import domain.client.FinancialCompanyClient
import domain.client.FinancialCompanyClient.{ClientError, ErrorResponse, InvalidResponse, Timeout}
import domain.client.order.Side.{Buy, Sell}
import domain.client.order.{Order, OrderSetting}
import domain.client.order.single.SingleOrder
import domain.client.order.single.SingleOrder.{Limit, Market}
import domain.client.order.logic.OrderWithLogic
import domain.client.order.logic.OrderWithLogic.{IFD, IFO, OCO, Stop}
import infra.client.{BaseClient, Method}
import play.api.libs.json._
import sun.reflect.generics.reflectiveObjects.NotImplementedException

import scala.util.Try
import scalaj.http.HttpResponse

class BitFlyerClient(bitFlyerApiKey: String, bitFlyerApiSecret: String, override protected[this] val productCode: BitFlyerProductCode) extends FinancialCompanyClient with BaseClient {

  override protected[this] val baseUrl: String = "https://api.bitflyer.jp"
  private[this] val cryptoWatchUrl: String = "https://api.cryptowat.ch"

  def getPermissions: HttpResponse[String] = {
    callPrivateApi(Method.Get, "/v1/me/getpermissions", "")
  }

  def getMarkets: HttpResponse[String] = {
    callPublicApi(Method.Get, "/v1/getmarkets", "")
  }

  def getCandles(count: Int): Either[ClientError, Seq[Candle]] = {
    val response = (for {
      result <- Try(callPublicApi(Method.Get, "/markets/bitflyer/btcfxjpy/ohlc", "", cryptoWatchUrl)).toEither.left.map(e => Timeout(e.getMessage): ClientError).right
    } yield {
      if (result.code == 200) Right(result)
      else Left(ErrorResponse(result.body))
    }).joinRight
    val result = response.right.map { response =>
      val json = Json.parse(response.body)
      val rawCandles = (json \ "result" \ "60").validate[JsArray]
      rawCandles.asEither.left.map(_ => InvalidResponse(response.body))
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

  def postSingleOrder(singleOrder: SingleOrder, setting: OrderSetting): HttpResponse[String] = {
    val body = singleOrderToJson(singleOrder, setting)
    callPrivateApi(Method.Post, "/v1/me/sendchildorder", body)
  }

  def postOrderWithLogic(logic: OrderWithLogic, setting: OrderSetting): Either[ClientError, Unit] = {
    val (orderMethod, parameters) = logic match {
      case IFD(pre, post) =>
        ("IFD", singleOrderToJsonForSpecialOrder(Seq(pre, post)))
      case OCO(order, otherOrder) =>
        ("OCO", singleOrderToJsonForSpecialOrder(Seq(order, otherOrder)))
      case IFO(preOrder, postOrder) =>
        ("IFDOCO", singleOrderToJsonForSpecialOrder(Seq(preOrder, postOrder.order, postOrder.otherOrder)))
      case stop: Stop =>
        ("SIMPLE", stopOrderToJson(stop))
    }
    val specificSetting = BitFlyerParameterConverter.orderSetting(setting)
    val body = Json.obj(
      "order_method" -> orderMethod,
      "minute_to_expire" -> specificSetting.expireMinutes,
      "time_in_force" -> BitFlyerParameterConverter.timeInForce(specificSetting.timeInForce),
      "parameters" -> JsArray(parameters)
    ).toString()

    (for {
      result <- Try(callPrivateApi(Method.Post, "/v1/me/sendparentorder", body)).toEither.left.map(e => Timeout(e.getMessage)).right
    } yield {
        if (result.code == 200) Right(())
        else Left(ErrorResponse(result.body))
    }).joinRight
  }

  def getSingleOrders: HttpResponse[String] = {
    callPrivateApi(Method.Get, "/v1/me/getchildorders", "")
  }

  def postCancelSingleOrders(productCodeStr: String): Either[ClientError, Unit] = {
    val body = Json.obj(
      "product_code" -> productCodeStr
    ).toString()

    (for {
      result <- Try(callPrivateApi(Method.Post, "/v1/me/cancelallchildorders", body)).toEither.left.map(e => Timeout(e.getMessage)).right
    } yield {
      if (result.code == 200) Right(())
      else Left(ErrorResponse(result.body))
    }).joinRight
  }

  def getCollateral: HttpResponse[String] =
    callPrivateApi(Method.Get, "/v1/me/getcollateral", "")

  def getOrdersWithLogic: Either[String, Seq[Int]] = {
    val response = callPrivateApi(Method.Get, "/v1/me/getparentorders?parent_order_state=ACTIVE&product_code=FX_BTC_JPY", "").body
    val json = Json.parse(response)
    (for {
      orders <- json.validate[Seq[JsValue]].asEither.right
    } yield {
      orders.flatMap { order =>
        (order \ "price").validate[Int].asOpt
      }
    }).left.map(_ => response)
  }

  def getOrders: Either[ClientError, Seq[OrderWithLogic]] = {
    val response = (for {
      result <- Try(callPrivateApi(Method.Get, "/v1/me/getparentorders?parent_order_state=ACTIVE&product_code=FX_BTC_JPY", "")).toEither.left.map(e => Timeout(e.getMessage): ClientError).right
    } yield {
      if (result.code == 200) Right(result)
      else Left(ErrorResponse(result.body))
    }).joinRight
    response.right.map { response =>
      val json = Json.parse(response.body)
      val rawOrders = json.as[JsArray].value
      rawOrders.map { rawOrder =>
        (rawOrder \ "parent_order_type").as[String] match {
          case "STOP" =>
            val side = (rawOrder \ "side").as[String] match {
              case "SELL" => Sell
              case "BUY" => Buy
            }
            getParentOrderDetail(Stop(side, (rawOrder \ "price").as[Int], (rawOrder \ "size").as[Double]), (rawOrder \ "parent_order_id").as[String])
          case _ => throw new NotImplementedException()
        }
      }
    }
  }

  private[this] def getParentOrderDetail(logic: OrderWithLogic, id: String): OrderWithLogic = {
    val response = (for {
      result <- Try(callPrivateApi(Method.Get, "/v1/me/getparentorder?parent_order_id=" + id, "")).toEither.left.map(e => Timeout(e.getMessage): ClientError).right
    } yield {
      if (result.code == 200) Right(result)
      else Left(ErrorResponse(result.body))
    }).joinRight
    response match {
      case Right(httpResponse) =>
        val json = Json.parse(httpResponse.body)
        logic match {
          case order: Stop =>
            val price = ((json \ "parameters").as[JsArray].value.head \ "trigger_price").as[Int]
            order.copy(price = price)
        }
      case Left(_) =>
        logic
    }
  }

  def getPositions: Either[ClientError, Seq[Position]] = {
    val response = (for {
      result <- Try(callPrivateApi(Method.Get, "/v1/me/getpositions?product_code=FX_BTC_JPY", "")).toEither.left.map(e => Timeout(e.getMessage): ClientError).right
    } yield {
      if (result.code == 200) Right(result)
      else Left(ErrorResponse(result.body))
    }).joinRight
    response.right.map { response =>
      val json = Json.parse(response.body)
      for {
        positionsJsArray <- json.validate[JsArray].asEither.left.map(_ => InvalidResponse(response.body)).right
      } yield {
        positionsJsArray.value.map { rawPosition =>
          val side = (rawPosition \ "side").as[String] match {
            case "SELL" => Sell
            case "BUY" => Buy
          }
          Position(side, (rawPosition \ "size").as[Double])
        }
      }
    }.joinRight
  }

  def getBoard: Either[String, Int] = {
    val response = callPrivateApi(Method.Get, "/v1/board?product_code=FX_BTC_JPY", "").body
    val json = Json.parse(response)
    (for {
      price <- (json \ "mid_price").validate[Int].asEither.right
    } yield {
      price
    }).left.map(_ => response)
  }

  private[this] def callPrivateApi(method: Method, path: String, body: String): HttpResponse[String] = {
    val timestamp = java.time.ZonedDateTime.now().toEpochSecond.toString
    val text = timestamp + method.value + path + body

    val sign = generateHMAC(bitFlyerApiSecret, text)

    callApiCommon(method, path, body)
      .headers(Seq(("ACCESS-KEY", bitFlyerApiKey), ("ACCESS-TIMESTAMP", timestamp), ("ACCESS-SIGN", sign), ("Content-Type", "application/json")))
      .asString
  }

  private[this] def singleOrderToJsonForSpecialOrder(orders: Seq[Order]): Seq[JsObject] = {
    orders.map {
      case singleOrder: SingleOrder =>
        val orderType = singleOrder match {
          case _: Market => "MARKET"
          case _: Limit => "LIMIT"
          case _ => throw new NotImplementedException()
        }
        val price = singleOrder.price.getOrElse(0)
        Json.obj(
          "product_code" -> BitFlyerParameterConverter.productCode(productCode),
          "condition_type" -> orderType,
          "side" -> BitFlyerParameterConverter.side(singleOrder.side),
          "price" -> price,
          "size" -> singleOrder.size
        )
      case _ => throw new InvalidParameterException("except single order is not implemented")
    }
  }

  private[this] def stopOrderToJson(stop: Stop): Seq[JsObject] = {
    val json = Json.obj(
      "product_code" -> BitFlyerParameterConverter.productCode(productCode),
      "condition_type" -> "STOP",
      "side" -> BitFlyerParameterConverter.side(stop.side),
      "trigger_price" -> stop.price,
      "size" -> stop.size
    )
    Seq(json)
  }

  private[this] def singleOrderToJson(singleOrder: SingleOrder, setting: OrderSetting): String = {
    val orderType = singleOrder match {
      case _: Market => "MARKET"
      case _: Limit => "LIMIT"
      case _ => throw new NotImplementedException()
    }
    val price = singleOrder.price.getOrElse(0)
    val specificSetting = BitFlyerParameterConverter.orderSetting(setting)
    Json.obj(
      "product_code" -> BitFlyerParameterConverter.productCode(productCode),
      "child_order_type" -> orderType,
      "side" -> BitFlyerParameterConverter.side(singleOrder.side),
      "price" -> price,
      "size" -> singleOrder.size,
      "minute_to_expire" ->  specificSetting.expireMinutes,
      "time_in_force" -> BitFlyerParameterConverter.timeInForce(specificSetting.timeInForce)
    ).toString()
  }

}
