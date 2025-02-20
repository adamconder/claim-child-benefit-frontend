/*
 * Copyright 2023 HM Revenue & Customs
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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import config.Service
import connectors.ClaimChildBenefitConnector._
import connectors.SubmitClaimHttpParser._
import models.domain.Claim
import models.{CheckLimitResponse, DesignatoryDetails, Done, RelationshipDetails, SupplementaryMetadata}
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import java.time.{LocalDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ClaimChildBenefitConnector @Inject()(
                                            config: Configuration,
                                            httpClient: HttpClientV2
                                          )(implicit ec: ExecutionContext) {

  private val baseUrl = config.get[Service]("microservice.services.claim-child-benefit")
  private val designatoryDetailsUrl = url"$baseUrl/claim-child-benefit/designatory-details"
  private val submitClaimUrl = url"$baseUrl/claim-child-benefit/submit"
  private val supplementaryDataUrl = url"$baseUrl/claim-child-benefit/supplementary-data"
  private val checkThrottleUrl = url"$baseUrl/claim-child-benefit/throttle/check"
  private val incrementThrottleCountUrl = url"$baseUrl/claim-child-benefit/throttle/increment"
  private val relationshipDetailsUrl = url"$baseUrl/claim-child-benefit/relationship-details"

  private val internalAuthToken = config.get[String]("internal-auth.token")

  def designatoryDetails()(implicit hc: HeaderCarrier): Future[DesignatoryDetails] = {
    httpClient
      .get(designatoryDetailsUrl)
      .execute[DesignatoryDetails]
  }

  def submitClaim(claim: Claim, correlationId: UUID)(implicit hc: HeaderCarrier): Future[Done] =
    httpClient
      .post(submitClaimUrl)
      .withBody(Json.toJson(claim))
      .setHeader("Authorization" -> internalAuthToken)
      .setHeader("CorrelationId" -> correlationId.toString)
      .execute[Either[SubmitClaimException, Done]]
      .flatMap {
        case Right(_)        => Future.successful(Done)
        case Left(exception) => Future.failed(exception)
      }

  def submitSupplementaryData(pdf: Array[Byte], metadata: SupplementaryMetadata)(implicit hc: HeaderCarrier): Future[Done] = {

    val formattedSubmissionDate =
      DateTimeFormatter.ISO_DATE_TIME.format(
        LocalDateTime.ofInstant(
          metadata.submissionDate.truncatedTo(ChronoUnit.SECONDS),
          ZoneOffset.UTC
        )
      )

    httpClient.post(supplementaryDataUrl)
      .setHeader("Authorization" -> internalAuthToken)
      .withBody(
        Source(
          List(
            MultipartFormData.DataPart("metadata.nino", metadata.nino),
            MultipartFormData.DataPart("metadata.submissionDate", formattedSubmissionDate),
            MultipartFormData.DataPart("metadata.correlationId", metadata.correlationId),
            MultipartFormData.FilePart(
              key = "file",
              filename = "supplementary-data.pdf",
              contentType = Some("application/pdf"),
              ref = Source.single(ByteString(pdf))
            )
          )
        )
      )
      .execute[HttpResponse]
      .map(_ => Done)
  }

  def checkThrottleLimit()(implicit hc: HeaderCarrier): Future[CheckLimitResponse] =
    httpClient
      .get(checkThrottleUrl)
      .setHeader("Authorization" -> internalAuthToken)
      .execute[CheckLimitResponse]

  def incrementThrottleCount()(implicit hc: HeaderCarrier): Future[Done] =
    httpClient
      .post(incrementThrottleCountUrl)
      .setHeader("Authorization" -> internalAuthToken)
      .execute[HttpResponse]
      .map(_ => Done)

  def relationshipDetails()(implicit hc: HeaderCarrier): Future[RelationshipDetails] = {
    httpClient
      .get(relationshipDetailsUrl)
      .execute[RelationshipDetails]
  }
}

object ClaimChildBenefitConnector {

  sealed abstract class SubmitClaimException(message: String) extends Exception(message)

  class BadRequestException extends SubmitClaimException(message = "Received BAD_REQUEST when submitting a claim to CBS")

  class InvalidClaimStateException extends SubmitClaimException(message = "Received INVALID_CLAIM_STATE when submitting a claim to CBS")

  class InvalidAccountStateException extends SubmitClaimException(message = "Received INVALID_ACCOUNT_STATE when submitting a claim to CBS")

  class AlreadyInPaymentException extends SubmitClaimException(message = "Received PAYMENT_PRESENT_AFTER_FIRST_PAYMENT_INSTRUCTION when submitting a claim to CBS")

  class UnprocessableEntityException extends SubmitClaimException(message = "Received UNPROCESSABLE_ENTITY when submitting a claim to CBS, with no recognised failure code")

  class UnrecognisedResponseException(code: Int) extends SubmitClaimException(message = s"Received an unrecognised response code $code when submitting a claim to CBS")

  class ServerErrorException extends SubmitClaimException(message = "Received INTERNAL_SERVER_ERROR when submitting a claim to CBS")

  class ServiceUnavailableException extends SubmitClaimException(message = "Received SERVICE_UNAVAILABLE when submitting a claim to CBS")
}
