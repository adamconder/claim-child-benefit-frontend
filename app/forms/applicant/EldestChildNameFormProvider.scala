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
import models.ChildName
import play.api.data.Form
import play.api.data.Forms._

import javax.inject.Inject

class EldestChildNameFormProvider @Inject() extends Mappings {

   def apply(): Form[ChildName] = Form(
     mapping(
      "firstName" -> text("eldestChildName.error.firstName.required")
        .verifying(firstError(
          maxLength(35, "eldestChildName.error.firstName.length"),
          regexp(Validation.nameInputPattern, "eldestChildName.error.firstName.invalid")
        )),
      "middleNames" -> optional(text("eldestChildName.error.middleNames.required")
        .verifying(firstError(
          maxLength(35, "eldestChildName.error.middleNames.length"),
          regexp(Validation.nameInputPattern, "eldestChildName.error.middleNames.invalid")
        ))),
       "lastName" -> text("eldestChildName.error.lastName.required")
         .verifying(firstError(
           maxLength(35, "eldestChildName.error.lastName.length"),
           regexp(Validation.nameInputPattern, "eldestChildName.error.lastName.invalid")
         ))
    )(ChildName.apply)(ChildName.unapply)
   )
 }
