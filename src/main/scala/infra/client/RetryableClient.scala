package infra.client

import domain.client.FinancialCompanyClient
import domain.client.FinancialCompanyClient.ClientError

trait RetryableClient extends Client {

  protected[this] val retryCount: Int
  protected[this] val delaySec: Int

  override protected[this] def callApi(method: Method, path: String, headers: Seq[(String, String)], body: String): Either[FinancialCompanyClient.ClientError, String] = {
    def callApiWithCount(count: Int): Either[ClientError, String] =
      callApiImpl(method, path, headers, body) match {
        case Left(_) if count < retryCount =>
          Thread.sleep(1000 * delaySec)
          callApiWithCount(count + 1)
        case Left(error) => Left(error)
        case Right(response) => Right(response)
      }

    callApiWithCount(0)
  }
}
