package example

import java.nio.file._

import scala.util.{Failure, Success, Try}

case class DoomConfiguration(doom: String)

object Hello extends Greeting with App {
  val confFiles = Set(Paths.get("./application.conf"))

  Try(pureconfig.loadConfigFromFiles[DoomConfiguration](confFiles)) match {
    case Success(configResult) =>
      configResult match {
        case Right(conf) =>
          println("doom conf from file: " + conf.doom)
        case Left(_) =>
          println("doom conf from file read failed.")
      }
      println(greeting)
    case Failure(e) =>
      println("config load failed: " + e.getMessage)
  }
}

trait Greeting {
  lazy val greeting: String = "Hello doom coil engine!!!"
}
