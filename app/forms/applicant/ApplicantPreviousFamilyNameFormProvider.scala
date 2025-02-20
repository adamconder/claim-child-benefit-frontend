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
import forms.mappings.Mappings
import models.ApplicantPreviousName
import play.api.data.Form

import javax.inject.Inject

class ApplicantPreviousFamilyNameFormProvider @Inject() extends Mappings {

  def apply(): Form[ApplicantPreviousName] =
    Form(
      "value" -> text("applicantPreviousFamilyName.error.required")
        .verifying(firstError(
          maxLength(35, "applicantPreviousFamilyName.error.length"),
          regexp(Validation.nameInputPattern, "applicantPreviousFamilyName.error.invalid")
        ))
        .transform[ApplicantPreviousName](ApplicantPreviousName.apply, x => x.lastName)
    )
}
