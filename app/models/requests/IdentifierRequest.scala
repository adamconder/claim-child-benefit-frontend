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

package models.requests

import play.api.mvc.{Request, WrappedRequest}

sealed trait IdentifierRequest[A] extends Request[A] {

  def request: Request[A]
  def userId: String
  val authenticated: Boolean
}

final case class AuthenticatedIdentifierRequest[A](
                                                    request: Request[A],
                                                    userId: String,
                                                    nino: String
                                                  ) extends WrappedRequest[A](request) with IdentifierRequest[A] {

  override val authenticated: Boolean = true
}

final case class UnauthenticatedIdentifierRequest[A](
                                                      request: Request[A],
                                                      userId: String
                                                    ) extends WrappedRequest[A](request) with IdentifierRequest[A] {

  override val authenticated: Boolean = false
}
