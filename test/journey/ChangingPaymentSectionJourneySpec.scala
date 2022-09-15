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

package journey

import generators.ModelGenerators
import models.RelationshipStatus._
import models.{BankAccountDetails, BankAccountHolder, Benefits, ChildName, PaymentFrequency}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import pages.applicant.ApplicantHasPreviousFamilyNamePage
import pages.income.ApplicantOrPartnerBenefitsPage
import pages.payments._
import pages.{CheckYourAnswersPage, RelationshipStatusPage}

import java.time.LocalDate

class ChangingPaymentSectionJourneySpec extends AnyFreeSpec with JourneyHelpers with ModelGenerators {

  private val childName               = arbitrary[ChildName].sample.value
  private val bankAccountHolder       = arbitrary[BankAccountHolder].sample.value
  private val bankDetails             = arbitrary[BankAccountDetails].sample.value
  private val benefits: Set[Benefits] = Set(Gen.oneOf(Benefits.values).sample.value)

  "when the user initially said they were currently receiving Child Benefit" - {

    val initialise = journeyOf(
      submitAnswer(CurrentlyReceivingChildBenefitPage, true),
      submitAnswer(EldestChildNamePage, childName),
      submitAnswer(EldestChildDateOfBirthPage, LocalDate.now),
      submitAnswer(WantToBePaidToExistingAccountPage, true),
      goTo(CheckYourAnswersPage)
    )

    "changing to say they are not currently receiving Child Benefit must remove the relevant questions, then ask if the user want to be paid" in {

      startingFrom(CurrentlyReceivingChildBenefitPage)
        .run(
          initialise,
          goToChangeAnswer(CurrentlyReceivingChildBenefitPage),
          submitAnswer(CurrentlyReceivingChildBenefitPage, false),
          pageMustBe(WantToBePaidPage),
          answersMustNotContain(EldestChildNamePage),
          answersMustNotContain(EldestChildDateOfBirthPage),
          answersMustNotContain(WantToBePaidToExistingAccountPage)
        )
    }

    "and initially said they wanted to be paid to their existing bank account" - {

      "changing that answer to `no` must collect bank account details" in {

        val initialise = journeyOf(
          submitAnswer(WantToBePaidToExistingAccountPage, true),
          goTo(CheckYourAnswersPage)
        )

        startingFrom(WantToBePaidToExistingAccountPage)
          .run(
            initialise,
            goToChangeAnswer(WantToBePaidToExistingAccountPage),
            submitAnswer(WantToBePaidToExistingAccountPage, false),
            pageMustBe(ApplicantHasSuitableAccountPage)
          )
      }
    }

    "and initially said they did not want to be paid to their existing bank account" - {

      "changing that answer to `yes` must remove their bank account details and return to Check Answers" in {

        val initialise = journeyOf(
          submitAnswer(WantToBePaidToExistingAccountPage, false),
          submitAnswer(ApplicantHasSuitableAccountPage, true),
          submitAnswer(BankAccountHolderPage, bankAccountHolder),
          submitAnswer(BankAccountDetailsPage, bankDetails),
          setUserAnswerTo(ApplicantHasPreviousFamilyNamePage, false),
          goTo(CheckYourAnswersPage)
        )

        startingFrom(WantToBePaidToExistingAccountPage)
          .run(
            initialise,
            goToChangeAnswer(WantToBePaidToExistingAccountPage),
            submitAnswer(WantToBePaidToExistingAccountPage, true),
            pageMustBe(CheckYourAnswersPage),
            answersMustNotContain(ApplicantHasSuitableAccountPage),
            answersMustNotContain(BankAccountHolderPage),
            answersMustNotContain(BankAccountDetailsPage)
          )
      }
    }
  }

  "when the user initially said they were not currently receiving Child Benefit" - {

    "must remove `want to be paid` answers and collect details of the eldest child" in {

      val initialise = journeyOf(
        setUserAnswerTo(RelationshipStatusPage, Single),
        submitAnswer(CurrentlyReceivingChildBenefitPage, false),
        submitAnswer(WantToBePaidPage, true),
        submitAnswer(PaymentFrequencyPage, PaymentFrequency.Weekly),
        goTo(CheckYourAnswersPage)
      )

      startingFrom(CurrentlyReceivingChildBenefitPage)
        .run(
          initialise,
          goToChangeAnswer(CurrentlyReceivingChildBenefitPage),
          submitAnswer(CurrentlyReceivingChildBenefitPage, true),
          submitAnswer(EldestChildNamePage, childName),
          submitAnswer(EldestChildDateOfBirthPage, LocalDate.now),
          pageMustBe(WantToBePaidToExistingAccountPage),
          answersMustNotContain(WantToBePaidPage),
          answersMustNotContain(PaymentFrequencyPage)
        )
    }

    "and did not want to be paid" - {

      "and the user is Single, Separated, Divorced or Widowed" - {

        "changing `want to be paid` to `yes` must ask `want to be paid weekly` then proceed to account details" in {

          Seq(Single, Separated, Divorced, Widowed).foreach { status =>

            val initialise = journeyOf(
              setUserAnswerTo(RelationshipStatusPage, status),
              submitAnswer(CurrentlyReceivingChildBenefitPage, false),
              submitAnswer(WantToBePaidPage, false),
              goTo(CheckYourAnswersPage)
            )

            startingFrom(CurrentlyReceivingChildBenefitPage)
              .run(
                initialise,
                goToChangeAnswer(WantToBePaidPage),
                submitAnswer(WantToBePaidPage, true),
                submitAnswer(PaymentFrequencyPage, arbitrary[PaymentFrequency].sample.value),
                pageMustBe(ApplicantHasSuitableAccountPage)
              )
          }
        }
      }

      "and the user is Married or Cohabiting" - {

        "and they or their partner receive a qualifying benefit" - {

          "changing `want to be paid` to `yes` must ask `want to be paid weekly` then proceed to account details" in {

            Seq(Married, Cohabiting).foreach { status =>

              val benefits = Set(Gen.oneOf(Benefits.qualifyingBenefits).sample.value)

              val initialise = journeyOf(
                setUserAnswerTo(RelationshipStatusPage, status),
                setUserAnswerTo(ApplicantOrPartnerBenefitsPage, benefits),
                submitAnswer(CurrentlyReceivingChildBenefitPage, false),
                submitAnswer(WantToBePaidPage, false),
                goTo(CheckYourAnswersPage)
              )

              startingFrom(CurrentlyReceivingChildBenefitPage)
                .run(
                  initialise,
                  goToChangeAnswer(WantToBePaidPage),
                  submitAnswer(WantToBePaidPage, true),
                  submitAnswer(PaymentFrequencyPage, arbitrary[PaymentFrequency].sample.value),
                  pageMustBe(ApplicantHasSuitableAccountPage)
                )
            }
          }
        }

        "and they or their partner do not receive a qualifying benefit" - {

          "changing `want to be paid` to `yes` must proceed to account details" in {

            Seq(Married, Cohabiting).foreach { status =>

              val benefits: Set[Benefits] = Set(Benefits.NoneOfTheAbove)

              val initialise = journeyOf(
                setUserAnswerTo(RelationshipStatusPage, status),
                setUserAnswerTo(ApplicantOrPartnerBenefitsPage, benefits),
                submitAnswer(CurrentlyReceivingChildBenefitPage, false),
                submitAnswer(WantToBePaidPage, false),
                goTo(CheckYourAnswersPage)
              )

              startingFrom(CurrentlyReceivingChildBenefitPage)
                .run(
                  initialise,
                  goToChangeAnswer(WantToBePaidPage),
                  submitAnswer(WantToBePaidPage, true),
                  pageMustBe(ApplicantHasSuitableAccountPage)
                )
            }
          }
        }
      }
    }

    "and wanted to be paid" - {

      "changing `want to be paid` to `no` must remove all of the bank details and `want to be paid weekly`" in {

        val initialise = journeyOf(
          setUserAnswerTo(RelationshipStatusPage, Single),
          setUserAnswerTo(ApplicantOrPartnerBenefitsPage, benefits),
          submitAnswer(CurrentlyReceivingChildBenefitPage, false),
          submitAnswer(WantToBePaidPage, true),
          submitAnswer(PaymentFrequencyPage, arbitrary[PaymentFrequency].sample.value),
          submitAnswer(ApplicantHasSuitableAccountPage, true),
          submitAnswer(BankAccountHolderPage, bankAccountHolder),
          submitAnswer(BankAccountDetailsPage, bankDetails),
          setUserAnswerTo(ApplicantHasPreviousFamilyNamePage, false),
          goTo(CheckYourAnswersPage)
        )

        startingFrom(CurrentlyReceivingChildBenefitPage)
          .run(
            initialise,
            goToChangeAnswer(WantToBePaidPage),
            submitAnswer(WantToBePaidPage, false),
            pageMustBe(CheckYourAnswersPage),
            answersMustNotContain(PaymentFrequencyPage),
            answersMustNotContain(ApplicantHasSuitableAccountPage),
            answersMustNotContain(BankAccountHolderPage),
            answersMustNotContain(BankAccountDetailsPage)
          )
      }
    }
  }

  "when the user initially said they have a suitable account" - {

    "changing the answer to `no` must remove all of the bank details" in {

      val initialise = journeyOf(
        submitAnswer(ApplicantHasSuitableAccountPage, true),
        submitAnswer(BankAccountHolderPage, bankAccountHolder),
        submitAnswer(BankAccountDetailsPage, bankDetails),
        setUserAnswerTo(ApplicantHasPreviousFamilyNamePage, false),
        goTo(CheckYourAnswersPage)
      )

      startingFrom(ApplicantHasSuitableAccountPage)
        .run(
          initialise,
          goToChangeAnswer(ApplicantHasSuitableAccountPage),
          submitAnswer(ApplicantHasSuitableAccountPage, false),
          pageMustBe(CheckYourAnswersPage),
          answersMustNotContain(BankAccountHolderPage),
          answersMustNotContain(BankAccountDetailsPage)
        )
    }
  }

  "when the user initially said they did not have a suitable account" - {

    val initialise = journeyOf(
      submitAnswer(ApplicantHasSuitableAccountPage, false),
      setUserAnswerTo(ApplicantHasPreviousFamilyNamePage, false),
      goTo(CheckYourAnswersPage)
    )

    "changing the answer to `yes` must proceed to collect bank details" in {

        startingFrom(ApplicantHasSuitableAccountPage)
          .run(
            initialise,
            goToChangeAnswer(ApplicantHasSuitableAccountPage),
            submitAnswer(ApplicantHasSuitableAccountPage, true),
            submitAnswer(BankAccountHolderPage, bankAccountHolder),
            submitAnswer(BankAccountDetailsPage, bankDetails),
            pageMustBe(CheckYourAnswersPage)
          )
    }
  }
}
