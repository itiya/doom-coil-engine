package example

case class DoomConfiguration(doom: String)

object Hello extends Greeting with App {
  pureconfig.loadConfig[DoomConfiguration] match {
    case Right(conf) =>
      println("doom conf: " + conf.doom)
    case Left(_) =>
      println("doom conf read failed.")
  }
  println(greeting)
}

trait Greeting {
  lazy val greeting: String = "Hello doom coil engine!!!"
}
