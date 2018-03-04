package example

import java.nio.file._

case class DoomConfiguration(doom: String)

object Hello extends Greeting with App {
  val confFiles = Set(Paths.get("./application.conf"))
  pureconfig.loadConfigFromFiles[DoomConfiguration](confFiles) match {
    case Right(conf) =>
      println("doom conf from file: " + conf.doom)
    case Left(_) =>
      println("doom conf from file read failed.")
  }
  println(greeting)
}

trait Greeting {
  lazy val greeting: String = "Hello doom coil engine!!!"
}
