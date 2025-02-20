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

package models

import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

final case class BrmsCacheItem(
                                 request: BirthRegistrationMatchingRequest,
                                 result: BirthRegistrationMatchingResult,
                                 timestamp: Instant
                               )

object BrmsCacheItem {

  val reads: Reads[BrmsCacheItem] =
    (
      (__ \ "request").read[BirthRegistrationMatchingRequest] and
      (__ \ "result").read[BirthRegistrationMatchingResult] and
      (__ \ "timestamp").read(MongoJavatimeFormats.instantFormat)
    )(BrmsCacheItem.apply _)

  val writes: OWrites[BrmsCacheItem] =
    (
      (__ \ "request").write[BirthRegistrationMatchingRequest] and
      (__ \ "result").write[BirthRegistrationMatchingResult] and
      (__ \ "timestamp").write(MongoJavatimeFormats.instantFormat)
    )(unlift(BrmsCacheItem.unapply))

  implicit val format: OFormat[BrmsCacheItem] = OFormat(reads, writes)
}
