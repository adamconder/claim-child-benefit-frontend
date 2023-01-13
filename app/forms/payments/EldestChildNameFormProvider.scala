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

package forms.payments

import forms.mappings.Mappings
import models.ChildName
import play.api.data.Form
import play.api.data.Forms._

import javax.inject.Inject

class EldestChildNameFormProvider @Inject() extends Mappings {

   def apply(): Form[ChildName] = Form(
     mapping(
      "firstName" -> text("eldestChildName.error.firstName.required")
        .verifying(maxLength(100, "eldestChildName.error.firstName.length")),
      "middleNames" -> optional(text("eldestChildName.error.middleNames.required")
        .verifying(maxLength(100, "eldestChildName.error.middleNames.length"))),
       "lastName" -> text("eldestChildName.error.lastName.required")
         .verifying(maxLength(100, "eldestChildName.error.lastName.length"))
    )(ChildName.apply)(ChildName.unapply)
   )
 }
