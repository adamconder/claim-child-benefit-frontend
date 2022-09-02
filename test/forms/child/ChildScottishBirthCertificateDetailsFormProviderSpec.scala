/*
 * Copyright 2022 HM Revenue & Customs
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

package forms.child

import forms.Validation
import forms.behaviours.StringFieldBehaviours
import models.ChildName
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import play.api.data.FormError

class ChildScottishBirthCertificateDetailsFormProviderSpec extends StringFieldBehaviours {

  private val childName = ChildName("first", None, "last")

  val requiredKey = "childScottishBirthCertificateDetails.error.required"
  val invalidKey = "childScottishBirthCertificateDetails.error.invalid"
  val maxLength = 9

  val form = new ChildScottishBirthCertificateDetailsFormProvider()(childName)

  ".value" - {

    val fieldName = "value"

    "must bind 10 digit strings" in {

      val gen = Gen.listOfN(10, Gen.numChar).map(_.mkString)

      forAll(gen) {
        dataItem: String =>
          val result = form.bind(Map(fieldName -> dataItem)).apply(fieldName)
          result.value.value mustBe dataItem
          result.errors mustBe empty
      }
    }

    "must bind 10 digit strings that include spaces" in {

      val gen = Gen.listOfN(10, Gen.numChar).map(_.mkString(" "))

      forAll(gen) {
        dataItem: String =>
          val result = form.bind(Map(fieldName -> dataItem)).apply(fieldName)
          result.value.value mustBe dataItem
          result.errors mustBe empty
      }
    }

    "must bind 10 digit strings that include hyphens" in {

      val gen = Gen.listOfN(10, Gen.numChar).map(_.mkString("-"))

      forAll(gen) {
        dataItem: String =>
          val result = form.bind(Map(fieldName -> dataItem)).apply(fieldName)
          result.value.value mustBe dataItem
          result.errors mustBe empty
      }
    }

    "must not bind values with fewer than 10 digits" in {

      val gen = for {
        charCount <- Gen.choose(1, 9)
        chars     <- Gen.listOfN(charCount, Gen.numChar)
      } yield chars.mkString

      forAll(gen) {
        dataItem =>
          val result = form.bind(Map(fieldName -> dataItem)).apply(fieldName)
          result.errors must contain only FormError(fieldName, invalidKey, Seq(Validation.scottishBirthCertificateNumberPattern.toString))
      }
    }

    "must not bind values with more than 10 digits" in {

      val gen = for {
        charCount <- Gen.choose(11, 100)
        chars     <- Gen.listOfN(charCount, Gen.numChar)
      } yield chars.mkString

      forAll(gen) {
        dataItem =>
          val result = form.bind(Map(fieldName -> dataItem)).apply(fieldName)
          result.errors must contain only FormError(fieldName, invalidKey, Seq(Validation.scottishBirthCertificateNumberPattern.toString))
      }
    }

    "must not bind vales that contain characters other than digits" in {

      forAll(arbitrary[String]) {
        value =>

          whenever (!value.forall(_.isDigit) && !value.forall(_ == ' ')) {
            val result = form.bind(Map(fieldName -> value)).apply(fieldName)
            result.errors must contain only FormError(fieldName, invalidKey, Seq(Validation.scottishBirthCertificateNumberPattern.toString))
          }
      }
    }

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey, Seq(childName.firstName))
    )
  }
}
