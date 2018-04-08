package infra.client

import domain.client.FinancialCompanyClient.ClientError

trait NormalClient extends Client {

  protected[this] def callApi(method: Method, path: String, headers: Seq[(String, String)], body: String): Either[ClientError, String] =
    callApiImpl(method, path, headers, body)

}
