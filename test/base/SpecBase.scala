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

package base

import controllers.actions._
import models.{Index, UserAnswers}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{OptionValues, TryValues}
import play.api.Application
import play.api.i18n.{Messages, MessagesApi}
import play.api.inject.{Binding, bind}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest

import java.time.{Clock, Instant, LocalDate, ZoneId}

trait SpecBase
  extends AnyFreeSpec
    with Matchers
    with TryValues
    with OptionValues
    with ScalaFutures
    with IntegrationPatience {

  protected val index: Index = Index(0)

  protected val userAnswersId: String = "id"

  protected val emptyUserAnswers : UserAnswers = UserAnswers(userAnswersId)

  protected val fixedInstant: Instant = LocalDate.now.atStartOfDay(ZoneId.systemDefault).toInstant
  protected val clockAtFixedInstant: Clock = Clock.fixed(fixedInstant, ZoneId.systemDefault)

  protected def messages(app: Application): Messages = app.injector.instanceOf[MessagesApi].preferred(FakeRequest())

  protected def applicationBuilder(
                                    userAnswers: Option[UserAnswers] = None,
                                    clock: Clock = clockAtFixedInstant,
                                    userIsAuthenticated: Boolean = false
                                  ): GuiceApplicationBuilder = {

    val identifierActionBinding: Binding[IdentifierAction] = if (userIsAuthenticated) {
      bind[IdentifierAction].to[FakeAuthenticatedIdentifierAction]
    } else {
      bind[IdentifierAction].to[FakeUnauthenticatedIdentifierAction]
    }

    new GuiceApplicationBuilder()
      .overrides(
        bind[DataRequiredAction].to[DataRequiredActionImpl],
        identifierActionBinding,
        bind[DataRetrievalAction].toInstance(new FakeDataRetrievalAction(userAnswers)),
        bind[Clock].toInstance(clock)
      )
  }
}
