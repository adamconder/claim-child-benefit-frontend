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

final case class BankAccountDetails (
                                      firstName: String,
                                      lastName: String,
                                      bankName: String,
                                      sortCode: String,
                                      accountNumber: String,
                                      rollNumber: Option[String]
                                    ) {

  lazy val sortCodeTrimmed: String =
    sortCode
      .replace(" ", "")
      .replace("-", "")

  private val accountNumberLength = 8

  val accountNumberPadded: String =
    accountNumber
      .replace(" ", "")
      .replace("-", "")
      .reverse.padTo(accountNumberLength, '0').reverse
}

object BankAccountDetails {
  implicit val format = Json.format[BankAccountDetails]
}

final case class BuildingSocietyDetails(
                                         firstName: String,
                                         lastName: String,
                                         buildingSociety: BuildingSociety,
                                         rollNumber: String
                                       )

object BuildingSocietyDetails {

  implicit lazy val format: OFormat[BuildingSocietyDetails] = Json.format
}
