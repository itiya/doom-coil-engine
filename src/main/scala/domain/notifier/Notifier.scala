package domain.notifier

trait Notifier {
  def notify(message: NotifyMessage, notifierLevel: NotifyLevel): Unit
}

case class Topic(title: String, text: String)

case class NotifyMessage(subject: String, topics: Seq[Topic])

sealed trait NotifyLevel

object NotifyLevel {
  case object Info extends NotifyLevel
  case object Warn extends NotifyLevel
  case object Error extends NotifyLevel
}
