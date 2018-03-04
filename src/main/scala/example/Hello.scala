package example

import java.nio.file._
import client.BitFlyerClient
import scala.util.{Failure, Success, Try}

case class DoomConfiguration(bitFlyerApiKey: String, bitFlyerApiSecret: String)

object Hello extends Greeting with App {
  val confFiles = Set(Paths.get("./application.conf"))

  Try(pureconfig.loadConfigFromFiles[DoomConfiguration](confFiles)) match {
    case Failure(e) =>
      println("config load failed: " + e.getMessage)
    case Success(configResult) =>
      configResult match {
        case Left(_) =>
          println("doom conf from file read failed.")
        case Right(conf) =>
          println("doom conf from file read success.")
          val bitFlyerClient = new BitFlyerClient(conf.bitFlyerApiKey, conf.bitFlyerApiSecret)
          println(bitFlyerClient.getPermissions.body)
      }
      println(greeting)
  }

}

trait Greeting {
  lazy val greeting: String = "Hello doom coil engine!!!"
}
