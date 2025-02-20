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

package models.journey

import cats.data._
import models.{Benefits, OtherEligibilityFailReason, ReasonNotToSubmit, RequiredDocument}

final case class JourneyModel(
                               applicant: Applicant,
                               relationship: Relationship,
                               children: NonEmptyList[Child],
                               benefits: Option[Set[Benefits]],
                               paymentPreference: PaymentPreference,
                               additionalInformation: Option[String],
                               userAuthenticated: Boolean,
                               reasonsNotToSubmit: Seq[ReasonNotToSubmit],
                               otherEligibilityFailureReasons: Seq[OtherEligibilityFailReason]
                             ) {

  val allRequiredDocuments: List[RequiredDocument] =
    children.toList.flatMap { child =>
      child.requiredDocuments.map(doc => RequiredDocument(child.name, doc))
    }

  val anyDocumentsRequired: Boolean = allRequiredDocuments.nonEmpty
}
