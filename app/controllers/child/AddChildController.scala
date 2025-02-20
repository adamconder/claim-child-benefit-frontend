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

package controllers.child

import controllers.actions._
import forms.child.AddChildFormProvider
import pages.Waypoints
import pages.child.AddChildPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.UserDataService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import viewmodels.checkAnswers.child.AddChildSummary
import views.html.child.AddChildView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AddChildController @Inject()(
                                         override val messagesApi: MessagesApi,
                                         userDataService: UserDataService,
                                         identify: IdentifierAction,
                                         getData: DataRetrievalAction,
                                         requireData: DataRequiredAction,
                                         formProvider: AddChildFormProvider,
                                         val controllerComponents: MessagesControllerComponents,
                                         view: AddChildView
                                 )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport {

  val form = formProvider()

  def onPageLoad(waypoints: Waypoints): Action[AnyContent] = (identify andThen getData andThen requireData) {
    implicit request =>

      val children = AddChildSummary.rows(request.userAnswers, waypoints, AddChildPage())

      Ok(view(form, waypoints, children))
  }

  def onSubmit(waypoints: Waypoints): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>

      form.bindFromRequest().fold(
        formWithErrors => {
          val children = AddChildSummary.rows(request.userAnswers, waypoints, AddChildPage())

          Future.successful(BadRequest(view(formWithErrors, waypoints, children)))
        },

        value =>
          for {
            updatedAnswers <- Future.fromTry(request.userAnswers.set(AddChildPage(), value))
            _              <- userDataService.set(updatedAnswers)
          } yield Redirect(AddChildPage().navigate(waypoints, request.userAnswers, updatedAnswers).route)
      )
  }
}
