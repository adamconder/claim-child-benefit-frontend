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

package forms.applicant

import forms.behaviours.StringFieldBehaviours
import models.{Index, Nationality}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import play.api.data.FormError

class ApplicantNationalityFormProviderSpec extends StringFieldBehaviours {

  val index = Index(0)
  val emptyExistingAnswers = Seq.empty[Nationality]
  val requiredKey = "applicantNationality.error.required"

  val form = new ApplicantNationalityFormProvider()(index, emptyExistingAnswers)

  ".value" - {

    val fieldName = "value"

    behave like fieldThatBindsValidData(
      form,
      fieldName,
      Gen.oneOf(Nationality.allNationalities.map(_.name))
    )

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )

    "must not bind any values other than valid nationalities" in {

      val invalidAnswers = arbitrary[String].suchThat(x => !Nationality.allNationalities.map(_.name).contains(x))

      forAll(invalidAnswers) {
        answer =>
          val result = form.bind(Map("value" -> answer)).apply(fieldName)
          result.errors must contain only FormError(fieldName, requiredKey)
      }
    }

    "must not bind a duplicate value" in {

      val existingAnswers = Seq(Nationality.allNationalities.head, Nationality.allNationalities(1))
      val answer = Nationality.allNationalities(1)
      val form = new ApplicantNationalityFormProvider()(index, existingAnswers)

      val result = form.bind(Map(fieldName -> answer.name)).apply(fieldName)
      result.errors must contain only FormError(fieldName, "applicantNationality.error.duplicate")
    }
  }
}
