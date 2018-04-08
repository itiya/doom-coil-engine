package infra.financial_company.bitflyer

import java.security.InvalidParameterException

import domain.Position
import domain.candle.{Candle, CandleSpan}
import domain.client.FinancialCompanyClient
import domain.client.FinancialCompanyClient.{ClientError, InvalidResponse}
import domain.client.order.Side.{Buy, Sell}
import domain.client.order.{Order, OrderSetting}
import domain.client.order.single.SingleOrder
import domain.client.order.single.SingleOrder.{Limit, Market}
import domain.client.order.logic.OrderWithLogic
import domain.client.order.logic.OrderWithLogic.{IFD, IFO, OCO, Stop}
import infra.chart_information.cryptowatch.CryptoWatchClient
import infra.client.{BaseClient, Method}
import play.api.libs.json._
import sun.reflect.generics.reflectiveObjects.NotImplementedException

class BitFlyerClient(bitFlyerApiKey: String, bitFlyerApiSecret: String, override protected[this] val productCode: BitFlyerProductCode) extends FinancialCompanyClient with BaseClient {

  override protected[this] val baseUrl: String = "https://api.bitflyer.jp"
  private[this] val cryptoWatchClient = new CryptoWatchClient("btcfxjpy")

  def getPermissions: Either[ClientError, String] = {
    callPrivateApi(Method.Get, "/v1/me/getpermissions", "")
  }

  def getMarkets: Either[ClientError, String] = {
    callApi(Method.Get, "/v1/getmarkets", Seq(), "")
  }

  def getCandles(count: Int, span: CandleSpan): Either[ClientError, Seq[Candle]] = {
    cryptoWatchClient.getCandles(count, span)
  }

  def postSingleOrder(singleOrder: SingleOrder, setting: OrderSetting): Either[ClientError, String] = {
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

    callPrivateApi(Method.Post, "/v1/me/sendparentorder", body).right.map(_ => ())
  }

  def getSingleOrders: Either[ClientError, String] = {
    callPrivateApi(Method.Get, "/v1/me/getchildorders", "")
  }

  def postCancelSingleOrders(productCodeStr: String): Either[ClientError, Unit] = {
    val body = Json.obj(
      "product_code" -> productCodeStr
    ).toString()

    callPrivateApi(Method.Post, "/v1/me/cancelallchildorders", body).right.map(_ => ())
  }

  def getCollateral: Either[ClientError, String] =
    callPrivateApi(Method.Get, "/v1/me/getcollateral", "")

  def getOrdersWithLogic: Either[ClientError, Seq[Int]] = {
    callPrivateApi(Method.Get, "/v1/me/getparentorders?parent_order_state=ACTIVE&product_code=FX_BTC_JPY", "") match {
      case Right(response) =>
        val json = Json.parse(response)
        (for {
          orders <- json.validate[Seq[JsValue]].asEither.right
        } yield {
          orders.flatMap { order =>
            (order \ "price").validate[Int].asOpt
          }
        }).left.map(_ => InvalidResponse(response))
      case Left(error) => Left(error)
    }

  }

  def getOrders: Either[ClientError, Seq[OrderWithLogic]] = {
    val response = callPrivateApi(Method.Get, "/v1/me/getparentorders?parent_order_state=ACTIVE&product_code=FX_BTC_JPY", "")
    response.right.map { response =>
      val json = Json.parse(response)
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
    val response = callPrivateApi(Method.Get, "/v1/me/getparentorder?parent_order_id=" + id, "")
    response match {
      case Right(httpResponse) =>
        val json = Json.parse(httpResponse)
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
    val response = callPrivateApi(Method.Get, "/v1/me/getpositions?product_code=FX_BTC_JPY", "")
    response.right.map { response =>
      val json = Json.parse(response)
      for {
        positionsJsArray <- json.validate[JsArray].asEither.left.map(_ => InvalidResponse(response)).right
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

  def getBoard: Either[ClientError, Int] = {
    val response = callPrivateApi(Method.Get, "/v1/board?product_code=FX_BTC_JPY", "")
    response match {
      case Right(body) =>
        val json = Json.parse(body)
        (for {
          price <- (json \ "mid_price").validate[Int].asEither.right
        } yield {
          price
        }).left.map(_ => InvalidResponse(body))
      case Left(error) => Left(error)
    }

  }

  private[this] def callPrivateApi(method: Method, path: String, body: String): Either[ClientError, String] = {
    val timestamp = java.time.ZonedDateTime.now().toEpochSecond.toString
    val text = timestamp + method.value + path + body

    val sign = generateHMAC(bitFlyerApiSecret, text)

    callApi(method, path, Seq(("ACCESS-KEY", bitFlyerApiKey), ("ACCESS-TIMESTAMP", timestamp), ("ACCESS-SIGN", sign), ("Content-Type", "application/json")), body)
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
