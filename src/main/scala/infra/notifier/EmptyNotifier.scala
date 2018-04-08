package infra.notifier

import domain.notifier.{Notifier, NotifyLevel, NotifyMessage}

class EmptyNotifier extends Notifier {
  def notify(message: NotifyMessage, notifierLevel: NotifyLevel): Unit = ()
}
