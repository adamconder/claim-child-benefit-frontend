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

import forms.Validation
import forms.behaviours.StringFieldBehaviours
import play.api.data.FormError

class ApplicantPreviousUkAddressFormProviderSpec extends StringFieldBehaviours {

  val form = new ApplicantPreviousUkAddressFormProvider()()

  ".line1" - {

    val fieldName = "line1"
    val requiredKey = "applicantPreviousUkAddress.error.line1.required"
    val lengthKey = "applicantPreviousUkAddress.error.line1.length"
    val invalidKey = "applicantPreviousUkAddress.error.line1.invalid"
    val maxLength = 35

    behave like fieldThatBindsValidData(
      form,
      fieldName,
      safeAddressInputsWithMaxLength(maxLength)
    )

    behave like fieldThatDoesNotBindInvalidData(
      form,
      fieldName,
      unsafeInputsWithMaxLength(maxLength),
      FormError(fieldName, invalidKey, Seq(Validation.addressInputPattern.toString))
    )

    behave like fieldWithMaxLength(
      form,
      fieldName,
      maxLength = maxLength,
      lengthError = FormError(fieldName, lengthKey, Seq(maxLength))
    )

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )
  }

  ".line2" - {

    val fieldName = "line2"
    val lengthKey = "applicantPreviousUkAddress.error.line2.length"
    val invalidKey = "applicantPreviousUkAddress.error.line2.invalid"
    val maxLength = 35

    behave like fieldThatBindsValidData(
      form,
      fieldName,
      safeAddressInputsWithMaxLength(maxLength)
    )

    behave like fieldThatDoesNotBindInvalidData(
      form,
      fieldName,
      unsafeInputsWithMaxLength(maxLength),
      FormError(fieldName, invalidKey, Seq(Validation.addressInputPattern.toString))
    )

    behave like fieldWithMaxLength(
      form,
      fieldName,
      maxLength = maxLength,
      lengthError = FormError(fieldName, lengthKey, Seq(maxLength))
    )
  }

  ".town" - {

    val fieldName = "town"
    val requiredKey = "applicantPreviousUkAddress.error.town.required"
    val lengthKey = "applicantPreviousUkAddress.error.town.length"
    val invalidKey = "applicantPreviousUkAddress.error.town.invalid"
    val maxLength = 35

    behave like fieldThatBindsValidData(
      form,
      fieldName,
      safeAddressInputsWithMaxLength(maxLength)
    )

    behave like fieldThatDoesNotBindInvalidData(
      form,
      fieldName,
      unsafeInputsWithMaxLength(maxLength),
      FormError(fieldName, invalidKey, Seq(Validation.addressInputPattern.toString))
    )

    behave like fieldWithMaxLength(
      form,
      fieldName,
      maxLength = maxLength,
      lengthError = FormError(fieldName, lengthKey, Seq(maxLength))
    )

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )
  }

  ".county" - {

    val fieldName = "county"
    val lengthKey = "applicantPreviousUkAddress.error.county.length"
    val invalidKey = "applicantPreviousUkAddress.error.county.invalid"
    val maxLength = 35

    behave like fieldThatBindsValidData(
      form,
      fieldName,
      safeAddressInputsWithMaxLength(maxLength)
    )

    behave like fieldThatDoesNotBindInvalidData(
      form,
      fieldName,
      unsafeInputsWithMaxLength(maxLength),
      FormError(fieldName, invalidKey, Seq(Validation.addressInputPattern.toString))
    )

    behave like fieldWithMaxLength(
      form,
      fieldName,
      maxLength = maxLength,
      lengthError = FormError(fieldName, lengthKey, Seq(maxLength))
    )
  }

  ".postcode" - {

    val fieldName = "postcode"
    val requiredKey = "applicantPreviousUkAddress.error.postcode.required"
    val lengthKey = "applicantPreviousUkAddress.error.postcode.length"
    val invalidKey = "applicantPreviousUkAddress.error.postcode.invalid"
    val maxLength = 8

    behave like fieldThatBindsValidData(
      form,
      fieldName,
      ukPostcode
    )

    behave like fieldThatDoesNotBindInvalidData(
      form,
      fieldName,
      unsafeInputsWithMaxLength(maxLength),
      FormError(fieldName, invalidKey, Seq(Validation.ukPostcodePattern.toString))
    )

    behave like fieldWithMaxLength(
      form,
      fieldName,
      maxLength = maxLength,
      lengthError = FormError(fieldName, lengthKey, Seq(maxLength))
    )

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )
  }
}
