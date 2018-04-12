package domain.trade

import domain.client.FinancialCompanyClient

trait TradeLogic {
  protected[this] val companyClient: FinancialCompanyClient
  def trade(): Unit
}
