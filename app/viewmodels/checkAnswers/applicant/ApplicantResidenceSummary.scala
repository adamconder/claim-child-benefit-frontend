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

package viewmodels.checkAnswers.applicant

import models.UserAnswers
import pages.applicant.ApplicantResidencePage
import pages.{CheckAnswersPage, Waypoints}
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import viewmodels.govuk.summarylist._
import viewmodels.implicits._

object ApplicantResidenceSummary {

  def row(answers: UserAnswers, waypoints: Waypoints, sourcePage: CheckAnswersPage)
         (implicit messages: Messages): Option[SummaryListRow] =
    answers.get(ApplicantResidencePage).map {
      answer =>

        SummaryListRowViewModel(
          key = "applicantResidence.checkYourAnswersLabel",
          value = ValueViewModel(messages(s"applicantResidence.${answer.toString}")),
          actions = Seq(
            ActionItemViewModel("site.change", ApplicantResidencePage.changeLink(waypoints, sourcePage).url)
              .withVisuallyHiddenText(messages("applicantResidence.change.hidden"))
          )
        )
    }
}
