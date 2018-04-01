package infra.client

sealed trait Method {
  def value: String
}

object Method {
  case object Get extends Method {
    override def value: String = "GET"
  }

  case object Post extends Method {
    override def value: String = "POST"
  }

  case object Put extends Method {
    override def value: String = "PUT"
  }

  case object Delete extends Method {
    override def value: String = "DELETE"
  }
}