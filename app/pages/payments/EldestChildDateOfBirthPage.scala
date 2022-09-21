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

package pages.payments

import controllers.payments.routes
import models.CurrentlyReceivingChildBenefit.{GettingPayments, NotClaiming, NotGettingPayments}
import models.UserAnswers
import pages.{NonEmptyWaypoints, Page, QuestionPage, Waypoints}
import play.api.libs.json.JsPath
import play.api.mvc.Call

import java.time.LocalDate

case object EldestChildDateOfBirthPage extends QuestionPage[LocalDate] {

  override def path: JsPath = JsPath \ toString

  override def toString: String = "eldestChildDateOfBirth"

  override def route(waypoints: Waypoints): Call =
    routes.EldestChildDateOfBirthController.onPageLoad(waypoints)

  override protected def nextPageNormalMode(waypoints: Waypoints, answers: UserAnswers): Page =
    WantToBePaidPage

  override protected def nextPageCheckMode(waypoints: NonEmptyWaypoints, answers: UserAnswers): Page =
    answers.get(CurrentlyReceivingChildBenefitPage).map {
      case GettingPayments =>
        answers.get(WantToBePaidPage).map {
          case true =>
            answers.get(WantToBePaidToExistingAccountPage)
              .map(_ => waypoints.next.page)
              .getOrElse(WantToBePaidToExistingAccountPage)

          case false =>
            waypoints.next.page
        }.orRecover

      case NotGettingPayments | NotClaiming =>
        waypoints.next.page
    }.orRecover
}
