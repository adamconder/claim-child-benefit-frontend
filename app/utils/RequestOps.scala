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

package utils

import models.requests.{AuthenticatedIdentifierRequest, DataRequest, OptionalDataRequest}
import play.api.mvc.Request

object RequestOps {

  implicit class RequestSyntax(request: Request[_]) {

    def signedIn: Boolean = request match {
      case d: DataRequest[_] =>
        d.request match {
          case _: AuthenticatedIdentifierRequest[_] => true
          case _ => false
        }

      case x: OptionalDataRequest[_] =>
        x.request match {
          case _: AuthenticatedIdentifierRequest[_] => true
          case _ => false
        }

      case x: AuthenticatedIdentifierRequest[_] =>
        true

      case _ =>
        false
    }
  }
}
