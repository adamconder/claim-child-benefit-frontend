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

package controllers.applicant

import base.SpecBase
import pages.EmptyWaypoints
import pages.applicant.CheckApplicantDetailsPage
import play.api.test.FakeRequest
import play.api.test.Helpers._
import viewmodels.govuk.SummaryListFluency
import views.html.applicant.CheckApplicantDetailsView

class CheckApplicantDetailsControllerSpec extends SpecBase with SummaryListFluency {

  "Check Applicant Details" - {

    "must return OK and the correct view for a GET" in {

      val app = applicationBuilder(Some(emptyUserAnswers)).build()

      running(app) {
        val request = FakeRequest(GET, routes.CheckApplicantDetailsController.onPageLoad.url)

        val result = route(app, request).value

        val view = app.injector.instanceOf[CheckApplicantDetailsView]
        val emptyList = SummaryListViewModel(Nil)

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(emptyList)(request, messages(app)).toString
      }
    }

    "must redirect to the next page for a POST" in {

      val app = applicationBuilder(Some(emptyUserAnswers)).build()

      running(app) {
        val request = FakeRequest(POST, routes.CheckApplicantDetailsController.onSubmit.url)

        val result = route(app, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual CheckApplicantDetailsPage.navigate(EmptyWaypoints, emptyUserAnswers, emptyUserAnswers).route.url
      }
    }
  }
}
