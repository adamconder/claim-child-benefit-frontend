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

package audit

import models.journey
import play.api.libs.json.{Json, Writes}

final case class Guardian(name: Option[AdultName], address: Option[Address])

object Guardian {

  implicit lazy val writes: Writes[Guardian] = Json.writes

  def build(guardian: journey.Guardian): Guardian =
    Guardian(
      name = guardian.name.map(AdultName.build),
      address = guardian.address.map(Address.build)
    )
}
