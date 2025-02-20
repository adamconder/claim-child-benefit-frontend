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
import controllers.{routes => baseRoutes}
import forms.applicant.CountryApplicantWorkedFormProvider
import models.{Country, UserAnswers}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import pages.EmptyWaypoints
import pages.applicant.CountryApplicantWorkedPage
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.UserDataService
import views.html.applicant.CountryApplicantWorkedView

import scala.concurrent.Future

class CountryApplicantWorkedControllerSpec extends SpecBase with MockitoSugar {

  private val waypoints = EmptyWaypoints

  private val formProvider = new CountryApplicantWorkedFormProvider()
  private val form = formProvider(index, Seq.empty[Country])
  private val validAnswer = Country.internationalCountries.head

  lazy val countryApplicantWorkedRoute = routes.CountryApplicantWorkedController.onPageLoad(waypoints, index).url

  "CountryApplicantWorked Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, countryApplicantWorkedRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[CountryApplicantWorkedView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, waypoints, index)(request, messages(application)).toString
      }
    }

    "must populate the view correctly on a GET when the question has previously been answered" in {

      val userAnswers = UserAnswers(userAnswersId).set(CountryApplicantWorkedPage(index), validAnswer).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, countryApplicantWorkedRoute)

        val view = application.injector.instanceOf[CountryApplicantWorkedView]

        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form.fill(validAnswer), waypoints, index)(request, messages(application)).toString
      }
    }

    "must save the answer and redirect to the next page when valid data is submitted" in {

      val mockUserDataService = mock[UserDataService]

      when(mockUserDataService.set(any())) thenReturn Future.successful(true)

      val application =
        applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(
            bind[UserDataService].toInstance(mockUserDataService)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, countryApplicantWorkedRoute)
            .withFormUrlEncodedBody(("value", validAnswer.code))

        val result = route(application, request).value
        val expectedAnswers = emptyUserAnswers.set(CountryApplicantWorkedPage(index), validAnswer).success.value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual CountryApplicantWorkedPage(index).navigate(waypoints, emptyUserAnswers, expectedAnswers).url
        verify(mockUserDataService, times(1)).set(eqTo(expectedAnswers))
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, countryApplicantWorkedRoute)
            .withFormUrlEncodedBody(("value", ""))

        val boundForm = form.bind(Map("value" -> ""))

        val view = application.injector.instanceOf[CountryApplicantWorkedView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) mustEqual view(boundForm, waypoints, index)(request, messages(application)).toString
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, countryApplicantWorkedRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual baseRoutes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, countryApplicantWorkedRoute)
            .withFormUrlEncodedBody(("value", validAnswer.code))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual baseRoutes.JourneyRecoveryController.onPageLoad().url
      }
    }
  }
}
