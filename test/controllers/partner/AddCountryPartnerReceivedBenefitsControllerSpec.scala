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

package controllers.partner

import base.SpecBase
import controllers.{routes => baseRoutes}
import forms.partner.AddCountryPartnerReceivedBenefitsFormProvider
import models.AdultName
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import pages.EmptyWaypoints
import pages.partner.{AddCountryPartnerReceivedBenefitsPage, PartnerNamePage}
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.UserDataService
import viewmodels.checkAnswers.partner.AddCountryPartnerReceivedBenefitsSummary
import views.html.partner.AddCountryPartnerReceivedBenefitsView

import scala.concurrent.Future

class AddCountryPartnerReceivedBenefitsControllerSpec extends SpecBase with MockitoSugar {

  private val name = AdultName(None, "first", None, "last")

  private val formProvider = new AddCountryPartnerReceivedBenefitsFormProvider()
  private val form = formProvider(name.firstName)
  private val waypoints = EmptyWaypoints
  private val baseAnswers = emptyUserAnswers.set(PartnerNamePage, name).success.value

  lazy val addCountryPartnerReceivedBenefitsRoute = routes.AddCountryPartnerReceivedBenefitsController.onPageLoad(waypoints).url

  "AddCountryPartnerReceivedBenefits Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(baseAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, addCountryPartnerReceivedBenefitsRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[AddCountryPartnerReceivedBenefitsView]

        implicit val msgs: Messages = messages(application)
        val nationalities = AddCountryPartnerReceivedBenefitsSummary.rows(emptyUserAnswers, waypoints, AddCountryPartnerReceivedBenefitsPage())

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, waypoints, nationalities, name.firstName)(request, implicitly).toString
      }
    }

    "must save the answer and redirect to the next page when valid data is submitted" in {

      val mockUserDataService = mock[UserDataService]

      when(mockUserDataService.set(any())) thenReturn Future.successful(true)

      val application =
        applicationBuilder(userAnswers = Some(baseAnswers))
          .overrides(
            bind[UserDataService].toInstance(mockUserDataService)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, addCountryPartnerReceivedBenefitsRoute)
            .withFormUrlEncodedBody(("value", "true"))

        val result = route(application, request).value
        val expectedAnswers = baseAnswers.set(AddCountryPartnerReceivedBenefitsPage(), true).success.value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual AddCountryPartnerReceivedBenefitsPage().navigate(waypoints, baseAnswers, expectedAnswers).url
        verify(mockUserDataService, times(1)).set(eqTo(expectedAnswers))
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val application = applicationBuilder(userAnswers = Some(baseAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, addCountryPartnerReceivedBenefitsRoute)
            .withFormUrlEncodedBody(("value", ""))

        val boundForm = form.bind(Map("value" -> ""))

        val view = application.injector.instanceOf[AddCountryPartnerReceivedBenefitsView]

        implicit val msgs: Messages = messages(application)
        val nationalities = AddCountryPartnerReceivedBenefitsSummary.rows(baseAnswers, waypoints, AddCountryPartnerReceivedBenefitsPage())

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) mustEqual view(boundForm, waypoints, nationalities, name.firstName)(request, implicitly).toString
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, addCountryPartnerReceivedBenefitsRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual baseRoutes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, addCountryPartnerReceivedBenefitsRoute)
            .withFormUrlEncodedBody(("value", "true"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual baseRoutes.JourneyRecoveryController.onPageLoad().url
      }
    }
  }
}
