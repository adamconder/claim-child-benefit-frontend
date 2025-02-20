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

package pages.child

import controllers.child.routes
import models.{Index, UserAnswers}
import pages.{Page, Waypoints}
import play.api.libs.json.JsPath
import play.api.mvc.Call

import scala.util.Try

final case class ChildLivesWithApplicantPage(index: Index) extends ChildQuestionPage[Boolean] {

  override def path: JsPath = JsPath \ "children" \ index.position \ toString

  override def toString: String = "childLivesWithApplicant"

  override def route(waypoints: Waypoints): Call =
    routes.ChildLivesWithApplicantController.onPageLoad(waypoints, index)

  override protected def nextPageNormalMode(waypoints: Waypoints, answers: UserAnswers): Page =
    answers.get(this).map {
      case true  => ChildLivedWithAnyoneElsePage(index)
      case false => GuardianNameKnownPage(index)
    }.orRecover

  override def cleanup(value: Option[Boolean], userAnswers: UserAnswers): Try[UserAnswers] =
    value.map {
      case true =>
        userAnswers
          .remove(GuardianNameKnownPage(index))
          .flatMap(_.remove(GuardianNamePage(index)))
          .flatMap(_.remove(GuardianAddressKnownPage(index)))
          .flatMap(_.remove(GuardianAddressInUkPage(index)))
          .flatMap(_.remove(GuardianUkAddressPage(index)))
          .flatMap(_.remove(GuardianInternationalAddressPage(index)))

      case false =>
        userAnswers
          .remove(ChildLivedWithAnyoneElsePage(index))
          .flatMap(_.remove(PreviousGuardianNameKnownPage(index)))
          .flatMap(_.remove(PreviousGuardianNamePage(index)))
          .flatMap(_.remove(PreviousGuardianAddressKnownPage(index)))
          .flatMap(_.remove(PreviousGuardianAddressInUkPage(index)))
          .flatMap(_.remove(PreviousGuardianUkAddressPage(index)))
          .flatMap(_.remove(PreviousGuardianInternationalAddressPage(index)))
          .flatMap(_.remove(PreviousGuardianPhoneNumberKnownPage(index)))
          .flatMap(_.remove(PreviousGuardianPhoneNumberPage(index)))
          .flatMap(_.remove(DateChildStartedLivingWithApplicantPage(index)))
    }.getOrElse(super.cleanup(value, userAnswers))
}
