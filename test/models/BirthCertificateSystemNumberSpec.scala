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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsString, JsSuccess, Json}

class BirthCertificateSystemNumberSpec extends AnyFreeSpec with Matchers {

  "must serialise / deserialise to/from a JsString" in {

    val systemNumber = BirthCertificateSystemNumber("123456789")

    val json = Json.toJson(systemNumber)

    json mustEqual JsString("123456789")
    json.validate[BirthCertificateSystemNumber] mustEqual JsSuccess(systemNumber)
  }
}
