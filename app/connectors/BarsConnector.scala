/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors

import audit.{AuditService, ValidateBankDetailsAuditEvent}
import config.Service
import connectors.BarsHttpParser.{BarsReads, ValidateBankDetailsResponse}
import models.ValidateBankDetailsRequest
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class BarsConnector @Inject()(config: Configuration, httpClient: HttpClient, auditService: AuditService)
                             (implicit ec: ExecutionContext) {

  private val baseUrl     = config.get[Service]("microservice.services.bank-account-reputation")
  private val validateUrl = s"$baseUrl/validate/bank-details"

  def validate(barsRequest: ValidateBankDetailsRequest)
              (implicit hc: HeaderCarrier): Future[ValidateBankDetailsResponse] = {

    val json = Json.toJson(barsRequest)

    httpClient.POST(validateUrl, json).map {
      case (connectorResponse, httpResponse) =>
        auditService.auditValidateBankDetails(
          ValidateBankDetailsAuditEvent(
            request  = barsRequest,
            response = getResponseJson(httpResponse)
          )
        )

        connectorResponse
    }
  }

  private def getResponseJson(response: HttpResponse): JsValue =
    Try(response.json).getOrElse(Json.obj())
}
