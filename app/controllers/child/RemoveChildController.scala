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

import controllers.AnswerExtractor
import controllers.actions._
import forms.child.RemoveChildFormProvider
import models.Index
import pages.Waypoints
import pages.child.{ChildNamePage, RemoveChildPage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import queries.ChildQuery
import services.UserDataService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.child.RemoveChildView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RemoveChildController @Inject()(
                                         override val messagesApi: MessagesApi,
                                         userDataService: UserDataService,
                                         identify: IdentifierAction,
                                         getData: DataRetrievalAction,
                                         requireData: DataRequiredAction,
                                         formProvider: RemoveChildFormProvider,
                                         val controllerComponents: MessagesControllerComponents,
                                         view: RemoveChildView
                                 )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with AnswerExtractor {

  def onPageLoad(waypoints: Waypoints, index: Index): Action[AnyContent] = (identify andThen getData andThen requireData) {
    implicit request =>
      getAnswer(ChildNamePage(index)) {
        childName =>

          val form = formProvider(childName)

          val preparedForm = request.userAnswers.get(RemoveChildPage(index)) match {
            case None => form
            case Some(value) => form.fill(value)
          }

          Ok(view(preparedForm, waypoints, index, childName))
      }
  }

  def onSubmit(waypoints: Waypoints, index: Index): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      getAnswerAsync(ChildNamePage(index)) {
        childName =>

          val form = formProvider(childName)

          form.bindFromRequest().fold(
            formWithErrors =>
              Future.successful(BadRequest(view(formWithErrors, waypoints, index, childName))),

            value =>
              if (value) {
                for {
                  updatedAnswers <- Future.fromTry(request.userAnswers.remove(ChildQuery(index)))
                  _ <- userDataService.set(updatedAnswers)
                } yield Redirect(RemoveChildPage(index).navigate(waypoints, request.userAnswers, updatedAnswers).route)
              } else {
                Future.successful(Redirect(RemoveChildPage(index).navigate(waypoints, request.userAnswers, request.userAnswers).route))
              }
          )
      }
  }
}
