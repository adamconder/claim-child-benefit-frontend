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

package viewmodels.checkAnswers.partner

import models.UserAnswers
import pages.partner.{PartnerNamePage, PartnerWaitingForEntitlementDecisionPage}
import pages.{CheckAnswersPage, Waypoints}
import play.api.i18n.Messages
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import viewmodels.govuk.summarylist._
import viewmodels.implicits._

object PartnerWaitingForEntitlementDecisionSummary {

  def row(answers: UserAnswers, waypoints: Waypoints, sourcePage: CheckAnswersPage)
         (implicit messages: Messages): Option[SummaryListRow] =
    for {
      partnerName <- answers.get(PartnerNamePage)
      waiting <- answers.get(PartnerWaitingForEntitlementDecisionPage)
    } yield {

      val safeFirstName = HtmlFormat.escape(partnerName.firstName).toString
      val value = if (waiting) "site.yes" else "site.no"

      SummaryListRowViewModel(
        key = messages("partnerWaitingForEntitlementDecision.checkYourAnswersLabel", safeFirstName),
        value = ValueViewModel(value),
        actions = Seq(
          ActionItemViewModel(
            messages("site.change"),
            PartnerWaitingForEntitlementDecisionPage.changeLink(waypoints, sourcePage).url
          ).withVisuallyHiddenText(messages("partnerWaitingForEntitlementDecision.change.hidden", safeFirstName))
        )
      )
    }
}
