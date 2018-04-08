package infra.notifier

import domain.notifier.NotifyLevel.{Error, Info, Warn}
import domain.notifier.{Notifier, NotifyLevel, NotifyMessage}
import slack.models.{Attachment, AttachmentField}

class SlackNotifier(token: String, channels: Seq[String]) extends Notifier {
  val client = new SlackNotifyClient(token)
  val maxShortTextLength = 30

  override def notify(message: NotifyMessage, notifyLevel: NotifyLevel): Unit = {
    val attachment = Attachment(
      color = Some(levelToColor(notifyLevel)),
      fields = message.topics.map { topic =>
        AttachmentField(
          title = topic.title,
          value = topic.text,
          short = topic.text.length < maxShortTextLength
        )
      }
    )
    client.postMessage(channels, message.subject, attachment)
  }

  private[this] def levelToColor(notifyLevel: NotifyLevel): String =
    notifyLevel match {
      case Info => "good"
      case Warn => "warning"
      case Error => "danger"
    }
}


class SlackNotifyClient(token: String) {
  import scala.util.{Failure, Success}
  import akka.actor.ActorSystem
  import slack.api.SlackApiClient
  import scala.concurrent.ExecutionContext.Implicits.global

  val client = SlackApiClient(token)

  implicit val system = ActorSystem("Slack")

  def postMessage(channels: Seq[String], text: String, attachment: Attachment): Unit = {
    channels.foreach { channel =>
      val res = client.postChatMessage(
        channelId = channel,
        text = text,
        asUser = Some(true),
        parse = Some("full"),
        attachments = Some(Seq(attachment))
      )
      res.onComplete {
        case Success(_) => ()
        case Failure(_) => ()
      }

    }
  }
}
