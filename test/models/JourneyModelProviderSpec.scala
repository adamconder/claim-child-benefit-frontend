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

import cats.data.NonEmptyList
import generators.ModelGenerators
import models.AdditionalInformation.{Information, NoInformation}
import models.BirthRegistrationMatchingResult.{Matched, NotAttempted, NotMatched}
import models.JourneyModel.Residency._
import models.RelationshipStatus._
import models.{ChildBirthRegistrationCountry => BirthCountry}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, EitherValues, OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages._
import pages.applicant._
import pages.child._
import pages.partner._
import pages.payments._
import queries.{AllChildPreviousNames, AllChildSummaries, AllCountriesApplicantReceivedBenefits, AllCountriesApplicantWorked, AllCountriesPartnerReceivedBenefits, AllCountriesPartnerWorked, AllPartnerNationalities, AllPreviousFamilyNames}
import services.BrmsService
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class JourneyModelProviderSpec
  extends AnyFreeSpec
    with Matchers
    with TryValues
    with EitherValues
    with OptionValues
    with ModelGenerators
    with MockitoSugar
    with BeforeAndAfterEach
    with ScalaFutures {

  private val now = LocalDate.now
  private val applicantName = AdultName(None, "first", None, "last")
  private val applicantNino = arbitrary[Nino].sample.value
  private val country = arbitrary[Country].sample.value
  private val phoneNumber = "07777 777777"
  private val applicantBenefits = Set[Benefits](Benefits.NoneOfTheAbove)
  private val applicantNationality = Gen.oneOf(Nationality.allNationalities).sample.value
  private val previousName1 = ApplicantPreviousName("previous name 1")
  private val previousName2 = ApplicantPreviousName("previous name 2")

  private val bankAccountDetails = BankAccountDetails("first", "last", "00000000", "000000")
  private val bankAccountHolder = BankAccountHolder.Applicant
  private val buildingSocietyDetails = arbitrary[BuildingSocietyDetails].sample.value
  private val eldestChildName = ChildName("first", None, "last")

  private val partnerName = AdultName(None, "partner first", None, "partner last")
  private val partnerNationality = Gen.oneOf(Nationality.allNationalities).sample.value
  private val partnerNino = arbitrary[Nino].sample.value
  private val partnerClaiming = PartnerClaimingChildBenefit.GettingPayments
  private val partnerEldestChildName = ChildName("partner child first", None, "partner child last")

  private val childName = ChildName("first", None, "last")
  private val biologicalSex = ChildBiologicalSex.Female
  private val relationshipToChild = ApplicantRelationshipToChild.BirthChild
  private val systemNumber = BirthCertificateSystemNumber("000000000")
  private val scottishBirthCertificateDetails = arbitrary[ScottishBirthCertificateDetails].sample.value
  private val childPreviousName1 = ChildName("first 1", None, "last 1")
  private val childPreviousName2 = ChildName("first 2", None, "last 2")
  private val adultName = AdultName(None, "pc first", None, "pc last")
  private val ukAddress = arbitrary[UkAddress].sample.value
  private val internationalAddress = arbitrary[InternationalAddress].sample.value

  private val mockBrmsService = mock[BrmsService]
  private val journeyModelProvider = new JourneyModelProvider(mockBrmsService)
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  override def beforeEach(): Unit = {
    reset(mockBrmsService)
    super.beforeEach()
  }

  ".buildFromUserAnswers" - {

    "must create a journey model" - {

      "from minimal answers when the applicant does not have a partner" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(WantToBePaidPage, false).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val expectedModel = JourneyModel(
          applicant = JourneyModel.Applicant(
            name = applicantName,
            previousFamilyNames = Nil,
            dateOfBirth = now,
            nationalInsuranceNumber = None,
            currentAddress = ukAddress,
            previousAddress = None,
            telephoneNumber = phoneNumber,
            nationalities = NonEmptyList(applicantNationality, Nil),
            residency = AlwaysLivedInUk,
            memberOfHMForcesOrCivilServantAbroad = false,
            currentlyReceivingChildBenefit = CurrentlyReceivingChildBenefit.NotClaiming
          ),
          relationship = JourneyModel.Relationship(Single, None, None),
          children = NonEmptyList(
            JourneyModel.Child(
              name = childName,
              nameChangedByDeedPoll = None,
              previousNames = Nil,
              biologicalSex = biologicalSex,
              dateOfBirth = now,
              countryOfRegistration = ChildBirthRegistrationCountry.England,
              birthCertificateNumber = Some(systemNumber),
              birthCertificateDetailsMatched = Matched,
              relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
              adoptingThroughLocalAuthority = false,
              previousClaimant = None,
              guardian = None,
              previousGuardian = None,
              dateChildStartedLivingWithApplicant = None
            ), Nil
          ),
          benefits = None,
          paymentPreference = JourneyModel.PaymentPreference.DoNotPay(None),
          additionalInformation = NoInformation
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value mustEqual expectedModel
      }

      "from minimal answers when the applicant has a partner" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(NotMatched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .set(RelationshipStatusPage, Married).success.value
          .set(ApplicantOrPartnerIncomePage, Income.BelowLowerThreshold).success.value
          .set(ApplicantOrPartnerBenefitsPage, applicantBenefits).success.value
          .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.NotClaiming).success.value
          .set(WantToBePaidPage, false).success.value
          .set(PartnerNamePage, partnerName).success.value
          .set(PartnerNinoKnownPage, false).success.value
          .set(PartnerDateOfBirthPage, now).success.value
          .set(PartnerNationalityPage(Index(0)), partnerNationality).success.value
          .set(PartnerEmploymentStatusPage, EmploymentStatus.activeStatuses).success.value
          .set(PartnerIsHmfOrCivilServantPage, false).success.value
          .set(PartnerWorkedAbroadPage, false).success.value
          .set(PartnerReceivedBenefitsAbroadPage, false).success.value
          .set(PartnerClaimingChildBenefitPage, PartnerClaimingChildBenefit.NotClaiming).success.value
          .set(AdditionalInformationPage, Information("info")).success.value

        val expectedModel = JourneyModel(
          applicant = JourneyModel.Applicant(
            name = applicantName,
            previousFamilyNames = Nil,
            dateOfBirth = now,
            nationalInsuranceNumber = None,
            currentAddress = ukAddress,
            previousAddress = None,
            telephoneNumber = phoneNumber,
            nationalities = NonEmptyList(applicantNationality, Nil),
            residency = AlwaysLivedInUk,
            memberOfHMForcesOrCivilServantAbroad = false,
            currentlyReceivingChildBenefit = CurrentlyReceivingChildBenefit.NotClaiming
          ),
          relationship = JourneyModel.Relationship(
            status = Married,
            since = None,
            partner = Some(JourneyModel.Partner(
              name = partnerName,
              dateOfBirth = now,
              nationalities = NonEmptyList(partnerNationality, Nil),
              nationalInsuranceNumber = None,
              memberOfHMForcesOrCivilServantAbroad = false,
              currentlyClaimingChildBenefit = PartnerClaimingChildBenefit.NotClaiming,
              eldestChild = None,
              countriesWorked = Nil,
              countriesReceivedBenefits = Nil,
              employmentStatus = EmploymentStatus.activeStatuses
            ))
          ),
          children = NonEmptyList(
            JourneyModel.Child(
              name = childName,
              nameChangedByDeedPoll = None,
              previousNames = Nil,
              biologicalSex = biologicalSex,
              dateOfBirth = now,
              countryOfRegistration = ChildBirthRegistrationCountry.England,
              birthCertificateNumber = Some(systemNumber),
              birthCertificateDetailsMatched = NotMatched,
              relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
              adoptingThroughLocalAuthority = false,
              previousClaimant = None,
              guardian = None,
              previousGuardian = None,
              dateChildStartedLivingWithApplicant = None
            ), Nil
          ),
          benefits = None,
          paymentPreference = JourneyModel.PaymentPreference.DoNotPay(None),
          additionalInformation = Information("info")
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value mustEqual expectedModel
      }

      "when the applicant has multiple children" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val childName2 = ChildName("first 2", None, "last 2")

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalCoupleIncomeDetails
          .withMinimalPartnerDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Married).success.value
          .set(ChildNamePage(Index(1)), childName2).success.value
          .set(ChildHasPreviousNamePage(Index(1)), false).success.value
          .set(ChildBiologicalSexPage(Index(1)), biologicalSex).success.value
          .set(ChildDateOfBirthPage(Index(1)), now).success.value
          .set(ChildBirthRegistrationCountryPage(Index(1)), ChildBirthRegistrationCountry.Scotland).success.value
          .set(ScottishBirthCertificateHasNumbersPage(Index(1)), true).success.value
          .set(ChildScottishBirthCertificateDetailsPage(Index(1)), scottishBirthCertificateDetails).success.value
          .set(ApplicantRelationshipToChildPage(Index(1)), relationshipToChild).success.value
          .set(AdoptingThroughLocalAuthorityPage(Index(1)), false).success.value
          .set(AnyoneClaimedForChildBeforePage(Index(1)), false).success.value
          .set(ChildLivesWithApplicantPage(Index(1)), true).success.value
          .set(ChildLivedWithAnyoneElsePage(Index(1)), false).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val expectedModel = JourneyModel(
          applicant = JourneyModel.Applicant(
            name = applicantName,
            previousFamilyNames = Nil,
            dateOfBirth = now,
            nationalInsuranceNumber = None,
            currentAddress = ukAddress,
            previousAddress = None,
            telephoneNumber = phoneNumber,
            nationalities = NonEmptyList(applicantNationality, Nil),
            residency = AlwaysLivedInUk,
            memberOfHMForcesOrCivilServantAbroad = false,
            currentlyReceivingChildBenefit = CurrentlyReceivingChildBenefit.NotClaiming
          ),
          relationship = JourneyModel.Relationship(
            status = Married,
            since = None,
            partner = Some(JourneyModel.Partner(
              name = partnerName,
              dateOfBirth = now,
              nationalities = NonEmptyList(partnerNationality, Nil),
              nationalInsuranceNumber = None,
              memberOfHMForcesOrCivilServantAbroad = false,
              currentlyClaimingChildBenefit = PartnerClaimingChildBenefit.NotClaiming,
              eldestChild = None,
              countriesWorked = Nil,
              countriesReceivedBenefits = Nil,
              employmentStatus = EmploymentStatus.activeStatuses
            ))
          ),
          children = NonEmptyList(
            JourneyModel.Child(
              name = childName,
              nameChangedByDeedPoll = None,
              previousNames = Nil,
              biologicalSex = biologicalSex,
              dateOfBirth = now,
              countryOfRegistration = ChildBirthRegistrationCountry.England,
              birthCertificateNumber = Some(systemNumber),
              birthCertificateDetailsMatched = Matched,
              relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
              adoptingThroughLocalAuthority = false,
              previousClaimant = None,
              guardian = None,
              previousGuardian = None,
              dateChildStartedLivingWithApplicant = None
            ), List(
              JourneyModel.Child(
                name = childName2,
                nameChangedByDeedPoll = None,
                previousNames = Nil,
                biologicalSex = biologicalSex,
                dateOfBirth = now,
                countryOfRegistration = ChildBirthRegistrationCountry.Scotland,
                birthCertificateNumber = Some(scottishBirthCertificateDetails),
                birthCertificateDetailsMatched = Matched,
                relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
                adoptingThroughLocalAuthority = false,
                previousClaimant = None,
                guardian = None,
                previousGuardian = None,
                dateChildStartedLivingWithApplicant = None
              )
            )
          ),
          benefits = None,
          paymentPreference = JourneyModel.PaymentPreference.DoNotPay(None),
          additionalInformation = NoInformation
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value mustEqual expectedModel
      }

      "when the applicant is already receiving child benefit" - {

        "and wants to be paid to their existing account" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .set(RelationshipStatusPage, Single).success.value
            .set(ApplicantIncomePage, Income.BelowLowerThreshold).success.value
            .set(ApplicantBenefitsPage, applicantBenefits).success.value
            .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.GettingPayments).success.value
            .set(EldestChildNamePage, eldestChildName).success.value
            .set(EldestChildDateOfBirthPage, now).success.value
            .set(WantToBePaidPage, true).success.value
            .set(ApplicantBenefitsPage, applicantBenefits).success.value
            .set(PaymentFrequencyPage, PaymentFrequency.Weekly).success.value
            .set(WantToBePaidToExistingAccountPage, true).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val expectedPaymentPreference = JourneyModel.PaymentPreference.ExistingAccount(
            JourneyModel.EldestChild(eldestChildName, now),
            PaymentFrequency.Weekly
          )
          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors mustBe empty
          data.value.paymentPreference mustEqual expectedPaymentPreference
        }

        "and does not want to be paid into their existing account" - {

          "and has a suitable account" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = UserAnswers("id")
              .withMinimalApplicantDetails
              .withOneChild
              .set(RelationshipStatusPage, Single).success.value
              .set(ApplicantIncomePage, Income.BelowLowerThreshold).success.value
              .set(ApplicantBenefitsPage, applicantBenefits).success.value
              .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.GettingPayments).success.value
              .set(EldestChildNamePage, eldestChildName).success.value
              .set(EldestChildDateOfBirthPage, now).success.value
              .set(WantToBePaidPage, true).success.value
              .set(ApplicantBenefitsPage, applicantBenefits).success.value
              .set(PaymentFrequencyPage, PaymentFrequency.EveryFourWeeks).success.value
              .set(WantToBePaidToExistingAccountPage, false).success.value
              .set(ApplicantHasSuitableAccountPage, true).success.value
              .set(BankAccountHolderPage, bankAccountHolder).success.value
              .set(AccountTypePage, AccountType.SortCodeAccountNumber).success.value
              .set(BankAccountDetailsPage, bankAccountDetails).success.value
              .set(AdditionalInformationPage, NoInformation).success.value

            val expectedPaymentPreference = JourneyModel.PaymentPreference.EveryFourWeeks(
              accountDetails = Some(JourneyModel.BankAccountWithHolder(bankAccountHolder, bankAccountDetails)),
              eldestChild = Some(JourneyModel.EldestChild(eldestChildName, now))
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.paymentPreference mustEqual expectedPaymentPreference
            data.value.benefits.value mustEqual applicantBenefits
          }

          "and does not have a suitable account" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = UserAnswers("id")
              .withMinimalApplicantDetails
              .withOneChild
              .set(RelationshipStatusPage, Single).success.value
              .set(ApplicantIncomePage, Income.BelowLowerThreshold).success.value
              .set(ApplicantBenefitsPage, applicantBenefits).success.value
              .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.GettingPayments).success.value
              .set(EldestChildNamePage, eldestChildName).success.value
              .set(EldestChildDateOfBirthPage, now).success.value
              .set(WantToBePaidPage, true).success.value
              .set(ApplicantBenefitsPage, applicantBenefits).success.value
              .set(PaymentFrequencyPage, PaymentFrequency.Weekly).success.value
              .set(WantToBePaidToExistingAccountPage, false).success.value
              .set(ApplicantHasSuitableAccountPage, false).success.value
              .set(AdditionalInformationPage, NoInformation).success.value

            val expectedPaymentPreference = JourneyModel.PaymentPreference.Weekly(
              accountDetails = None,
              eldestChild = Some(JourneyModel.EldestChild(eldestChildName, now))
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.paymentPreference mustEqual expectedPaymentPreference
          }

          "and does not want to be paid" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = UserAnswers("id")
              .withMinimalApplicantDetails
              .withOneChild
              .set(RelationshipStatusPage, Single).success.value
              .set(ApplicantIncomePage, Income.BelowLowerThreshold).success.value
              .set(ApplicantBenefitsPage, applicantBenefits).success.value
              .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.GettingPayments).success.value
              .set(EldestChildNamePage, eldestChildName).success.value
              .set(EldestChildDateOfBirthPage, now).success.value
              .set(WantToBePaidPage, false).success.value
              .set(AdditionalInformationPage, NoInformation).success.value

            val expectedPaymentPreference = JourneyModel.PaymentPreference.DoNotPay(
              Some(JourneyModel.EldestChild(eldestChildName, now))
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.paymentPreference mustEqual expectedPaymentPreference
          }
        }
      }

      "when the applicant is claiming Child Benefit but not getting payments" - {

        val baseAnswers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantIncomePage, Income.BelowLowerThreshold).success.value
          .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.NotGettingPayments).success.value
          .set(AdditionalInformationPage, NoInformation).success.value
          .set(EldestChildNamePage, eldestChildName).success.value
          .set(EldestChildDateOfBirthPage, now).success.value

        "and wants to be paid every 4 weeks" - {

          "and has a suitable account" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = baseAnswers
              .set(WantToBePaidPage, true).success.value
              .set(ApplicantBenefitsPage, applicantBenefits).success.value
              .set(PaymentFrequencyPage, PaymentFrequency.EveryFourWeeks).success.value
              .set(ApplicantHasSuitableAccountPage, true).success.value
              .set(BankAccountHolderPage, bankAccountHolder).success.value
              .set(AccountTypePage, AccountType.SortCodeAccountNumber).success.value
              .set(BankAccountDetailsPage, bankAccountDetails).success.value

            val expectedPaymentPreference = JourneyModel.PaymentPreference.EveryFourWeeks(
              accountDetails = Some(JourneyModel.BankAccountWithHolder(bankAccountHolder, bankAccountDetails)),
              eldestChild = Some(JourneyModel.EldestChild(eldestChildName, now))
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.paymentPreference mustEqual expectedPaymentPreference
          }

          "and does not have a suitable account" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = baseAnswers
              .set(WantToBePaidPage, true).success.value
              .set(ApplicantBenefitsPage, applicantBenefits).success.value
              .set(PaymentFrequencyPage, PaymentFrequency.EveryFourWeeks).success.value
              .set(ApplicantHasSuitableAccountPage, false).success.value

            val expectedPaymentPreference = JourneyModel.PaymentPreference.EveryFourWeeks(
              accountDetails = None,
              eldestChild = Some(JourneyModel.EldestChild(eldestChildName, now))
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.paymentPreference mustEqual expectedPaymentPreference
          }
        }

        "and wants to be paid weekly" - {

          "and has a suitable account" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = baseAnswers
              .set(WantToBePaidPage, true).success.value
              .set(ApplicantBenefitsPage, applicantBenefits).success.value
              .set(PaymentFrequencyPage, PaymentFrequency.Weekly).success.value
              .set(ApplicantHasSuitableAccountPage, true).success.value
              .set(BankAccountHolderPage, bankAccountHolder).success.value
              .set(AccountTypePage, AccountType.BuildingSocietyRollNumber).success.value
              .set(BuildingSocietyDetailsPage, buildingSocietyDetails).success.value

            val expectedPaymentPreference = JourneyModel.PaymentPreference.Weekly(
              accountDetails = Some(JourneyModel.BuildingSocietyWithHolder(bankAccountHolder, buildingSocietyDetails)),
              eldestChild = Some(JourneyModel.EldestChild(eldestChildName, now))
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.paymentPreference mustEqual expectedPaymentPreference
          }

          "and does not have a suitable account" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = baseAnswers
              .set(WantToBePaidPage, true).success.value
              .set(ApplicantBenefitsPage, applicantBenefits).success.value
              .set(PaymentFrequencyPage, PaymentFrequency.Weekly).success.value
              .set(ApplicantHasSuitableAccountPage, false).success.value

            val expectedPaymentPreference = JourneyModel.PaymentPreference.Weekly(
              accountDetails = None,
              eldestChild = Some(JourneyModel.EldestChild(eldestChildName, now))
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.paymentPreference mustEqual expectedPaymentPreference
          }
        }

        "and does not want to be paid" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = baseAnswers
            .set(WantToBePaidPage, false).success.value

          val expectedPaymentPreference = JourneyModel.PaymentPreference.DoNotPay(Some(JourneyModel.EldestChild(eldestChildName, now)))

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors mustBe empty
          data.value.paymentPreference mustEqual expectedPaymentPreference
        }
      }

      "when the applicant is not already receiving child benefit" - {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val baseAnswers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantIncomePage, Income.BelowLowerThreshold).success.value
          .set(ApplicantBenefitsPage, applicantBenefits).success.value
          .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.NotClaiming).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        "and wants to be paid every 4 weeks" - {

          "and has a suitable account" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = baseAnswers
              .set(WantToBePaidPage, true).success.value
              .set(PaymentFrequencyPage, PaymentFrequency.EveryFourWeeks).success.value
              .set(ApplicantHasSuitableAccountPage, true).success.value
              .set(BankAccountHolderPage, bankAccountHolder).success.value
              .set(AccountTypePage, AccountType.SortCodeAccountNumber).success.value
              .set(BankAccountDetailsPage, bankAccountDetails).success.value

            val expectedPaymentPreference = JourneyModel.PaymentPreference.EveryFourWeeks(
              accountDetails = Some(JourneyModel.BankAccountWithHolder(bankAccountHolder, bankAccountDetails)),
              eldestChild = None
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.paymentPreference mustEqual expectedPaymentPreference
          }

          "and does not have a suitable account" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = baseAnswers
              .set(WantToBePaidPage, true).success.value
              .set(PaymentFrequencyPage, PaymentFrequency.EveryFourWeeks).success.value
              .set(BankAccountHolderPage, bankAccountHolder).success.value
              .set(ApplicantHasSuitableAccountPage, false).success.value

            val expectedPaymentPreference = JourneyModel.PaymentPreference.EveryFourWeeks(
              accountDetails = None,
              eldestChild = None
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.paymentPreference mustEqual expectedPaymentPreference
          }
        }

        "and wants to be paid weekly" - {

          "and has a suitable account" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = baseAnswers
              .set(WantToBePaidPage, true).success.value
              .set(PaymentFrequencyPage, PaymentFrequency.Weekly).success.value
              .set(ApplicantHasSuitableAccountPage, true).success.value
              .set(BankAccountHolderPage, bankAccountHolder).success.value
              .set(AccountTypePage, AccountType.SortCodeAccountNumber).success.value
              .set(BankAccountDetailsPage, bankAccountDetails).success.value

            val expectedPaymentPreference = JourneyModel.PaymentPreference.Weekly(
              accountDetails = Some(JourneyModel.BankAccountWithHolder(bankAccountHolder, bankAccountDetails)),
              eldestChild = None
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.paymentPreference mustEqual expectedPaymentPreference
          }

          "and does not have a suitable account" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = baseAnswers
              .set(WantToBePaidPage, true).success.value
              .set(PaymentFrequencyPage, PaymentFrequency.Weekly).success.value
              .set(ApplicantHasSuitableAccountPage, false).success.value

            val expectedPaymentPreference = JourneyModel.PaymentPreference.Weekly(
              accountDetails = None,
              eldestChild = None
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.paymentPreference mustEqual expectedPaymentPreference
          }
        }

        "and does not want to be paid" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = baseAnswers
            .set(WantToBePaidPage, false).success.value

          val expectedPaymentPreference = JourneyModel.PaymentPreference.DoNotPay(None)

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors mustBe empty
          data.value.paymentPreference mustEqual expectedPaymentPreference
        }
      }

      "when the applicant knows their NINO" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantNinoKnownPage, true).success.value
          .set(ApplicantNinoPage, applicantNino).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val expectedApplicant = JourneyModel.Applicant(
          name = applicantName,
          previousFamilyNames = Nil,
          dateOfBirth = now,
          nationalInsuranceNumber = Some(applicantNino.value),
          currentAddress = ukAddress,
          previousAddress = None,
          telephoneNumber = phoneNumber,
          nationalities = NonEmptyList(applicantNationality, Nil),
          residency = AlwaysLivedInUk,
          memberOfHMForcesOrCivilServantAbroad = false,
          currentlyReceivingChildBenefit = CurrentlyReceivingChildBenefit.NotClaiming
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value.applicant mustEqual expectedApplicant
      }

      "when the applicant has some previous family names" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantHasPreviousFamilyNamePage, true).success.value
          .set(ApplicantPreviousFamilyNamePage(Index(0)), previousName1).success.value
          .set(ApplicantPreviousFamilyNamePage(Index(1)), previousName2).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val expectedApplicant = JourneyModel.Applicant(
          name = applicantName,
          previousFamilyNames = List(previousName1, previousName2),
          dateOfBirth = now,
          nationalInsuranceNumber = None,
          currentAddress = ukAddress,
          previousAddress = None,
          telephoneNumber = phoneNumber,
          nationalities = NonEmptyList(applicantNationality, Nil),
          residency = AlwaysLivedInUk,
          memberOfHMForcesOrCivilServantAbroad = false,
          currentlyReceivingChildBenefit = CurrentlyReceivingChildBenefit.NotClaiming
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value.applicant mustEqual expectedApplicant
      }

      "when the applicant's current address is international" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantResidencePage, ApplicantResidence.UkAndAbroad).success.value
          .set(ApplicantUsuallyLivesInUkPage, false).success.value
          .set(ApplicantUsualCountryOfResidencePage, country).success.value
          .set(ApplicantArrivedInUkPage, LocalDate.now).success.value
          .set(ApplicantCurrentAddressInUkPage, false).success.value
          .set(ApplicantCurrentInternationalAddressPage, internationalAddress).success.value
          .set(ApplicantLivedAtCurrentAddressOneYearPage, true).success.value
          .set(ApplicantEmploymentStatusPage, EmploymentStatus.activeStatuses).success.value
          .set(ApplicantWorkedAbroadPage, false).success.value
          .set(ApplicantReceivedBenefitsAbroadPage, false).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val expectedApplicant = JourneyModel.Applicant(
          name = applicantName,
          previousFamilyNames = Nil,
          dateOfBirth = now,
          nationalInsuranceNumber = None,
          currentAddress = internationalAddress,
          previousAddress = None,
          telephoneNumber = phoneNumber,
          nationalities = NonEmptyList(applicantNationality, Nil),
          residency = LivedInUkAndAbroad(Some(country), None, EmploymentStatus.activeStatuses, Nil, Nil),
          memberOfHMForcesOrCivilServantAbroad = false,
          currentlyReceivingChildBenefit = CurrentlyReceivingChildBenefit.NotClaiming
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value.applicant mustEqual expectedApplicant
      }

      "when the applicant has not lived at their current address for a year" - {

        "and their previous address is in the UK" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .withMinimalPaymentDetails
            .set(RelationshipStatusPage, Single).success.value
            .set(ApplicantResidencePage, ApplicantResidence.UkAndAbroad).success.value
            .set(ApplicantUsuallyLivesInUkPage, true).success.value
            .set(ApplicantArrivedInUkPage, LocalDate.now).success.value
            .set(ApplicantCurrentAddressInUkPage, true).success.value
            .set(ApplicantCurrentUkAddressPage, ukAddress).success.value
            .set(ApplicantLivedAtCurrentAddressOneYearPage, false).success.value
            .set(ApplicantPreviousAddressInUkPage, true).success.value
            .set(ApplicantPreviousUkAddressPage, ukAddress).success.value
            .set(ApplicantEmploymentStatusPage, EmploymentStatus.activeStatuses).success.value
            .set(ApplicantWorkedAbroadPage, false).success.value
            .set(ApplicantReceivedBenefitsAbroadPage, false).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val expectedApplicant = JourneyModel.Applicant(
            name = applicantName,
            previousFamilyNames = Nil,
            dateOfBirth = now,
            nationalInsuranceNumber = None,
            currentAddress = ukAddress,
            previousAddress = Some(ukAddress),
            telephoneNumber = phoneNumber,
            nationalities = NonEmptyList(applicantNationality, Nil),
            residency = LivedInUkAndAbroad(None, Some(LocalDate.now), EmploymentStatus.activeStatuses, Nil, Nil),
            memberOfHMForcesOrCivilServantAbroad = false,
            currentlyReceivingChildBenefit = CurrentlyReceivingChildBenefit.NotClaiming
          )

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors mustBe empty
          data.value.applicant mustEqual expectedApplicant
        }

        "and their previous address is international" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .withMinimalPaymentDetails
            .set(ApplicantResidencePage, ApplicantResidence.UkAndAbroad).success.value
            .set(ApplicantUsuallyLivesInUkPage, true).success.value
            .set(ApplicantArrivedInUkPage, LocalDate.now).success.value
            .set(ApplicantCurrentAddressInUkPage, true).success.value
            .set(ApplicantCurrentUkAddressPage, ukAddress).success.value
            .set(RelationshipStatusPage, Single).success.value
            .set(ApplicantLivedAtCurrentAddressOneYearPage, false).success.value
            .set(ApplicantPreviousAddressInUkPage, false).success.value
            .set(ApplicantPreviousInternationalAddressPage, internationalAddress).success.value
            .set(ApplicantEmploymentStatusPage, EmploymentStatus.activeStatuses).success.value
            .set(ApplicantWorkedAbroadPage, false).success.value
            .set(ApplicantReceivedBenefitsAbroadPage, false).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val expectedApplicant = JourneyModel.Applicant(
            name = applicantName,
            previousFamilyNames = Nil,
            dateOfBirth = now,
            nationalInsuranceNumber = None,
            currentAddress = ukAddress,
            previousAddress = Some(internationalAddress),
            telephoneNumber = phoneNumber,
            nationalities = NonEmptyList(applicantNationality, Nil),
            residency = LivedInUkAndAbroad(None, Some(LocalDate.now), EmploymentStatus.activeStatuses, Nil, Nil),
            memberOfHMForcesOrCivilServantAbroad = false,
            currentlyReceivingChildBenefit = CurrentlyReceivingChildBenefit.NotClaiming
          )

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors mustBe empty
          data.value.applicant mustEqual expectedApplicant
        }
      }

      "when the applicant recently worked abroad" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(ApplicantResidencePage, ApplicantResidence.UkAndAbroad).success.value
          .set(ApplicantUsuallyLivesInUkPage, true).success.value
          .set(ApplicantCurrentAddressInUkPage, true).success.value
          .set(ApplicantArrivedInUkPage, LocalDate.now).success.value
          .set(ApplicantCurrentUkAddressPage, ukAddress).success.value
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantLivedAtCurrentAddressOneYearPage, true).success.value
          .set(ApplicantEmploymentStatusPage, EmploymentStatus.activeStatuses).success.value
          .set(ApplicantWorkedAbroadPage, true).success.value
          .set(CountryApplicantWorkedPage(Index(0)), country).success.value
          .set(ApplicantReceivedBenefitsAbroadPage, false).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val expectedApplicant = JourneyModel.Applicant(
          name = applicantName,
          previousFamilyNames = Nil,
          dateOfBirth = now,
          nationalInsuranceNumber = None,
          currentAddress = ukAddress,
          previousAddress = None,
          telephoneNumber = phoneNumber,
          nationalities = NonEmptyList(applicantNationality, Nil),
          residency = LivedInUkAndAbroad(None, Some(LocalDate.now), EmploymentStatus.activeStatuses, List(country), Nil),
          memberOfHMForcesOrCivilServantAbroad = false,
          currentlyReceivingChildBenefit = CurrentlyReceivingChildBenefit.NotClaiming
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value.applicant mustEqual expectedApplicant
      }

      "when the applicant recently received benefits abroad" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(ApplicantResidencePage, ApplicantResidence.UkAndAbroad).success.value
          .set(ApplicantUsuallyLivesInUkPage, true).success.value
          .set(ApplicantCurrentAddressInUkPage, true).success.value
          .set(ApplicantArrivedInUkPage, LocalDate.now).success.value
          .set(ApplicantCurrentUkAddressPage, ukAddress).success.value
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantLivedAtCurrentAddressOneYearPage, true).success.value
          .set(ApplicantEmploymentStatusPage, EmploymentStatus.activeStatuses).success.value
          .set(ApplicantWorkedAbroadPage, false).success.value
          .set(ApplicantReceivedBenefitsAbroadPage, true).success.value
          .set(CountryApplicantReceivedBenefitsPage(Index(0)), country).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val expectedApplicant = JourneyModel.Applicant(
          name = applicantName,
          previousFamilyNames = Nil,
          dateOfBirth = now,
          nationalInsuranceNumber = None,
          currentAddress = ukAddress,
          previousAddress = None,
          telephoneNumber = phoneNumber,
          nationalities = NonEmptyList(applicantNationality, Nil),
          residency = LivedInUkAndAbroad(None, Some(LocalDate.now), EmploymentStatus.activeStatuses, Nil, List(country)),
          memberOfHMForcesOrCivilServantAbroad = false,
          currentlyReceivingChildBenefit = CurrentlyReceivingChildBenefit.NotClaiming
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value.applicant mustEqual expectedApplicant
      }

      "when the applicant knows their partner's NINO" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalCoupleIncomeDetails
          .withMinimalPaymentDetails
          .withMinimalPartnerDetails
          .set(RelationshipStatusPage, Married).success.value
          .set(PartnerNinoKnownPage, true).success.value
          .set(PartnerNinoPage, partnerNino).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val expectedPartner = JourneyModel.Partner(
          name = partnerName,
          dateOfBirth = now,
          nationalities = NonEmptyList(partnerNationality, Nil),
          nationalInsuranceNumber = Some(partnerNino.value),
          memberOfHMForcesOrCivilServantAbroad = false,
          currentlyClaimingChildBenefit = PartnerClaimingChildBenefit.NotClaiming,
          eldestChild = None,
          countriesWorked = Nil,
          countriesReceivedBenefits = Nil,
          employmentStatus = EmploymentStatus.activeStatuses
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value.relationship.partner.value mustEqual expectedPartner
      }

      "when the applicant's partner is entitled to Child Benefit" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalCoupleIncomeDetails
          .withMinimalPaymentDetails
          .withMinimalPartnerDetails
          .set(RelationshipStatusPage, Married).success.value
          .set(PartnerClaimingChildBenefitPage, partnerClaiming).success.value
          .set(PartnerEldestChildNamePage, partnerEldestChildName).success.value
          .set(PartnerEldestChildDateOfBirthPage, now).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val expectedPartner = JourneyModel.Partner(
          name = partnerName,
          dateOfBirth = now,
          nationalities = NonEmptyList(partnerNationality, Nil),
          nationalInsuranceNumber = None,
          memberOfHMForcesOrCivilServantAbroad = false,
          currentlyClaimingChildBenefit = partnerClaiming,
          eldestChild = Some(JourneyModel.EldestChild(partnerEldestChildName, now)),
          countriesWorked = Nil,
          countriesReceivedBenefits = Nil,
          employmentStatus = EmploymentStatus.activeStatuses
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value.relationship.partner.value mustEqual expectedPartner
      }

      "when the applicant's partner is waiting to hear if they are entitled to Child Benefit" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalCoupleIncomeDetails
          .withMinimalPaymentDetails
          .withMinimalPartnerDetails
          .set(RelationshipStatusPage, Married).success.value
          .set(PartnerClaimingChildBenefitPage, PartnerClaimingChildBenefit.GettingPayments).success.value
          .set(PartnerEldestChildNamePage, partnerEldestChildName).success.value
          .set(PartnerEldestChildDateOfBirthPage, now).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val expectedPartner = JourneyModel.Partner(
          name = partnerName,
          dateOfBirth = now,
          nationalities = NonEmptyList(partnerNationality, Nil),
          nationalInsuranceNumber = None,
          memberOfHMForcesOrCivilServantAbroad = false,
          currentlyClaimingChildBenefit = PartnerClaimingChildBenefit.GettingPayments,
          eldestChild = Some(JourneyModel.EldestChild(partnerEldestChildName, now)),
          countriesWorked = Nil,
          countriesReceivedBenefits = Nil,
          employmentStatus = EmploymentStatus.activeStatuses
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value.relationship.partner.value mustEqual expectedPartner
      }

      "when the applicant's partner has worked and receive benefits abroad" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalCoupleIncomeDetails
          .withMinimalPaymentDetails
          .withMinimalPartnerDetails
          .set(RelationshipStatusPage, Married).success.value
          .set(PartnerWorkedAbroadPage, true).success.value
          .set(CountryPartnerWorkedPage(Index(0)), country).success.value
          .set(PartnerReceivedBenefitsAbroadPage, true).success.value
          .set(CountryPartnerReceivedBenefitsPage(Index(0)), country).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val expectedPartner = JourneyModel.Partner(
          name = partnerName,
          dateOfBirth = now,
          nationalities = NonEmptyList(partnerNationality, Nil),
          nationalInsuranceNumber = None,
          memberOfHMForcesOrCivilServantAbroad = false,
          currentlyClaimingChildBenefit = PartnerClaimingChildBenefit.NotClaiming,
          eldestChild = None,
          countriesWorked = List(country),
          countriesReceivedBenefits = List(country),
          employmentStatus = EmploymentStatus.activeStatuses
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value.relationship.partner.value mustEqual expectedPartner
      }

      "when a child has previous names" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildHasPreviousNamePage(Index(0)), true).success.value
          .set(ChildNameChangedByDeedPollPage(Index(0)), true).success.value
          .set(ChildPreviousNamePage(Index(0), Index(0)), childPreviousName1).success.value
          .set(ChildPreviousNamePage(Index(0), Index(1)), childPreviousName2).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val expectedChildDetails = JourneyModel.Child(
          name = childName,
          nameChangedByDeedPoll = Some(true),
          previousNames = List(childPreviousName1, childPreviousName2),
          biologicalSex = ChildBiologicalSex.Female,
          dateOfBirth = now,
          countryOfRegistration = ChildBirthRegistrationCountry.England,
          birthCertificateNumber = Some(systemNumber),
          birthCertificateDetailsMatched = Matched,
          relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
          adoptingThroughLocalAuthority = false,
          previousClaimant = None,
          guardian = None,
          previousGuardian = None,
          dateChildStartedLivingWithApplicant = None
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value.children.toList must contain only expectedChildDetails
      }

      "when a child was born in England and their birth certificate does not have a system number" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildBirthRegistrationCountryPage(Index(0)), ChildBirthRegistrationCountry.England).success.value
          .set(BirthCertificateHasSystemNumberPage(Index(0)), false).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val expectedChildDetails = JourneyModel.Child(
          name = childName,
          nameChangedByDeedPoll = None,
          previousNames = Nil,
          biologicalSex = ChildBiologicalSex.Female,
          dateOfBirth = now,
          countryOfRegistration = ChildBirthRegistrationCountry.England,
          birthCertificateNumber = None,
          birthCertificateDetailsMatched = Matched,
          relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
          adoptingThroughLocalAuthority = false,
          previousClaimant = None,
          guardian = None,
          previousGuardian = None,
          dateChildStartedLivingWithApplicant = None
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value.children.toList must contain only expectedChildDetails
      }

      "when a child was born in Wales and their birth certificate does not have a system number" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildBirthRegistrationCountryPage(Index(0)), ChildBirthRegistrationCountry.Wales).success.value
          .set(BirthCertificateHasSystemNumberPage(Index(0)), false).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val expectedChildDetails = JourneyModel.Child(
          name = childName,
          nameChangedByDeedPoll = None,
          previousNames = Nil,
          biologicalSex = ChildBiologicalSex.Female,
          dateOfBirth = now,
          countryOfRegistration = ChildBirthRegistrationCountry.Wales,
          birthCertificateNumber = None,
          birthCertificateDetailsMatched = Matched,
          relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
          adoptingThroughLocalAuthority = false,
          previousClaimant = None,
          guardian = None,
          previousGuardian = None,
          dateChildStartedLivingWithApplicant = None
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value.children.toList must contain only expectedChildDetails
      }

      "when a child was born in Scotland" - {

        "and their birth certificate has details" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .withMinimalPaymentDetails
            .set(RelationshipStatusPage, Single).success.value
            .set(ChildBirthRegistrationCountryPage(Index(0)), ChildBirthRegistrationCountry.Scotland).success.value
            .set(ScottishBirthCertificateHasNumbersPage(Index(0)), true).success.value
            .set(ChildScottishBirthCertificateDetailsPage(Index(0)), scottishBirthCertificateDetails).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val expectedChildDetails = JourneyModel.Child(
            name = childName,
            nameChangedByDeedPoll = None,
            previousNames = Nil,
            biologicalSex = ChildBiologicalSex.Female,
            dateOfBirth = now,
            countryOfRegistration = ChildBirthRegistrationCountry.Scotland,
            birthCertificateNumber = Some(scottishBirthCertificateDetails),
            birthCertificateDetailsMatched = Matched,
            relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
            adoptingThroughLocalAuthority = false,
            previousClaimant = None,
            guardian = None,
            previousGuardian = None,
            dateChildStartedLivingWithApplicant = None
          )

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors mustBe empty
          data.value.children.toList must contain only expectedChildDetails
        }

        "and their birth certificate does not have details" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .withMinimalPaymentDetails
            .set(RelationshipStatusPage, Single).success.value
            .set(ChildBirthRegistrationCountryPage(Index(0)), ChildBirthRegistrationCountry.Scotland).success.value
            .set(ScottishBirthCertificateHasNumbersPage(Index(0)), false).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val expectedChildDetails = JourneyModel.Child(
            name = childName,
            nameChangedByDeedPoll = None,
            previousNames = Nil,
            biologicalSex = ChildBiologicalSex.Female,
            dateOfBirth = now,
            countryOfRegistration = ChildBirthRegistrationCountry.Scotland,
            birthCertificateNumber = None,
            birthCertificateDetailsMatched = Matched,
            relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
            adoptingThroughLocalAuthority = false,
            previousClaimant = None,
            guardian = None,
            previousGuardian = None,
            dateChildStartedLivingWithApplicant = None
          )

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors mustBe empty
          data.value.children.toList must contain only expectedChildDetails
        }
      }

      "when a child was born outside the UK" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(NotAttempted)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildBirthRegistrationCountryPage(Index(0)), ChildBirthRegistrationCountry.Other).success.value
          .set(BirthCertificateHasSystemNumberPage(Index(0)), false).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val expectedChildDetails = JourneyModel.Child(
          name = childName,
          nameChangedByDeedPoll = None,
          previousNames = Nil,
          biologicalSex = ChildBiologicalSex.Female,
          dateOfBirth = now,
          countryOfRegistration = ChildBirthRegistrationCountry.Other,
          birthCertificateNumber = None,
          birthCertificateDetailsMatched = NotAttempted,
          relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
          adoptingThroughLocalAuthority = false,
          previousClaimant = None,
          guardian = None,
          previousGuardian = None,
          dateChildStartedLivingWithApplicant = None
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value.children.toList must contain only expectedChildDetails
        verify(mockBrmsService, times(1)).matchChild(eqTo(None))(any(), any())
      }

      "when a child's country of registration is unknown" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(NotAttempted)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildBirthRegistrationCountryPage(Index(0)), ChildBirthRegistrationCountry.Unknown).success.value
          .set(BirthCertificateHasSystemNumberPage(Index(0)), false).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val expectedChildDetails = JourneyModel.Child(
          name = childName,
          nameChangedByDeedPoll = None,
          previousNames = Nil,
          biologicalSex = ChildBiologicalSex.Female,
          dateOfBirth = now,
          countryOfRegistration = ChildBirthRegistrationCountry.Unknown,
          birthCertificateNumber = None,
          birthCertificateDetailsMatched = NotAttempted,
          relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
          adoptingThroughLocalAuthority = false,
          previousClaimant = None,
          guardian = None,
          previousGuardian = None,
          dateChildStartedLivingWithApplicant = None
        )

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors mustBe empty
        data.value.children.toList must contain only expectedChildDetails
        verify(mockBrmsService, times(1)).matchChild(eqTo(None))(any(), any())
      }

      "when someone has claimed for this child before" - {

        "and the user does not know their details" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .withMinimalPaymentDetails
            .set(RelationshipStatusPage, Single).success.value
            .set(AnyoneClaimedForChildBeforePage(Index(0)), true).success.value
            .set(PreviousClaimantNameKnownPage(Index(0)), false).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val expectedChildDetails = JourneyModel.Child(
            name = childName,
            nameChangedByDeedPoll = None,
            previousNames = Nil,
            biologicalSex = biologicalSex,
            dateOfBirth = now,
            countryOfRegistration = ChildBirthRegistrationCountry.England,
            birthCertificateNumber = Some(systemNumber),
            birthCertificateDetailsMatched = Matched,
            relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
            adoptingThroughLocalAuthority = false,
            previousClaimant = Some(JourneyModel.PreviousClaimant(None, None)),
            guardian = None,
            previousGuardian = None,
            dateChildStartedLivingWithApplicant = None
          )

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors mustBe empty
          data.value.children.toList must contain only expectedChildDetails
        }

        "and the user knows their name but not their address" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .withMinimalPaymentDetails
            .set(RelationshipStatusPage, Single).success.value
            .set(AnyoneClaimedForChildBeforePage(Index(0)), true).success.value
            .set(PreviousClaimantNameKnownPage(Index(0)), true).success.value
            .set(PreviousClaimantNamePage(Index(0)), adultName).success.value
            .set(PreviousClaimantAddressKnownPage(Index(0)), false).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val expectedChildDetails = JourneyModel.Child(
            name = childName,
            nameChangedByDeedPoll = None,
            previousNames = Nil,
            biologicalSex = biologicalSex,
            dateOfBirth = now,
            countryOfRegistration = ChildBirthRegistrationCountry.England,
            birthCertificateNumber = Some(systemNumber),
            birthCertificateDetailsMatched = Matched,
            relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
            adoptingThroughLocalAuthority = false,
            previousClaimant = Some(JourneyModel.PreviousClaimant(Some(adultName), None)),
            guardian = None,
            previousGuardian = None,
            dateChildStartedLivingWithApplicant = None
          )

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors mustBe empty
          data.value.children.toList must contain only expectedChildDetails
        }

        "and the user knows their name and address" - {

          "and their address is in the UK" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = UserAnswers("id")
              .withMinimalApplicantDetails
              .withOneChild
              .withMinimalSingleIncomeDetails
              .withMinimalPaymentDetails
              .set(RelationshipStatusPage, Single).success.value
              .set(AnyoneClaimedForChildBeforePage(Index(0)), true).success.value
              .set(PreviousClaimantNameKnownPage(Index(0)), true).success.value
              .set(PreviousClaimantNamePage(Index(0)), adultName).success.value
              .set(PreviousClaimantAddressKnownPage(Index(0)), true).success.value
              .set(PreviousClaimantAddressInUkPage(Index(0)), true).success.value
              .set(PreviousClaimantUkAddressPage(Index(0)), ukAddress).success.value
              .set(AdditionalInformationPage, NoInformation).success.value

            val expectedChildDetails = JourneyModel.Child(
              name = childName,
              nameChangedByDeedPoll = None,
              previousNames = Nil,
              biologicalSex = biologicalSex,
              dateOfBirth = now,
              countryOfRegistration = ChildBirthRegistrationCountry.England,
              birthCertificateNumber = Some(systemNumber),
              birthCertificateDetailsMatched = Matched,
              relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
              adoptingThroughLocalAuthority = false,
              previousClaimant = Some(JourneyModel.PreviousClaimant(Some(adultName), Some(ukAddress))),
              guardian = None,
              previousGuardian = None,
              dateChildStartedLivingWithApplicant = None
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.children.toList must contain only expectedChildDetails
          }

          "and their address is international" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = UserAnswers("id")
              .withMinimalApplicantDetails
              .withOneChild
              .withMinimalSingleIncomeDetails
              .withMinimalPaymentDetails
              .set(RelationshipStatusPage, Single).success.value
              .set(AnyoneClaimedForChildBeforePage(Index(0)), true).success.value
              .set(PreviousClaimantNameKnownPage(Index(0)), true).success.value
              .set(PreviousClaimantNamePage(Index(0)), adultName).success.value
              .set(PreviousClaimantAddressKnownPage(Index(0)), true).success.value
              .set(PreviousClaimantAddressInUkPage(Index(0)), false).success.value
              .set(PreviousClaimantInternationalAddressPage(Index(0)), internationalAddress).success.value
              .set(AdditionalInformationPage, NoInformation).success.value

            val expectedChildDetails = JourneyModel.Child(
              name = childName,
              nameChangedByDeedPoll = None,
              previousNames = Nil,
              biologicalSex = ChildBiologicalSex.Female,
              dateOfBirth = now,
              countryOfRegistration = ChildBirthRegistrationCountry.England,
              birthCertificateNumber = Some(systemNumber),
              birthCertificateDetailsMatched = Matched,
              relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
              adoptingThroughLocalAuthority = false,
              previousClaimant = Some(JourneyModel.PreviousClaimant(Some(adultName), Some(internationalAddress))),
              guardian = None,
              previousGuardian = None,
              dateChildStartedLivingWithApplicant = None
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.children.toList must contain only expectedChildDetails
          }
        }
      }

      "when the child lives with someone else" - {

        "and the user does not know their details" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .withMinimalPaymentDetails
            .set(RelationshipStatusPage, Single).success.value
            .set(ChildLivesWithApplicantPage(Index(0)), false).success.value
            .set(GuardianNameKnownPage(Index(0)), false).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val expectedChildDetails = JourneyModel.Child(
            name = childName,
            nameChangedByDeedPoll = None,
            previousNames = Nil,
            biologicalSex = ChildBiologicalSex.Female,
            dateOfBirth = now,
            countryOfRegistration = ChildBirthRegistrationCountry.England,
            birthCertificateNumber = Some(systemNumber),
            birthCertificateDetailsMatched = Matched,
            relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
            adoptingThroughLocalAuthority = false,
            previousClaimant = None,
            guardian = Some(JourneyModel.Guardian(None, None)),
            previousGuardian = None,
            dateChildStartedLivingWithApplicant = None
          )

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors mustBe empty
          data.value.children.toList must contain only expectedChildDetails
        }

        "and the user knows their name but not their address" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .withMinimalPaymentDetails
            .set(RelationshipStatusPage, Single).success.value
            .set(ChildLivesWithApplicantPage(Index(0)), false).success.value
            .set(GuardianNameKnownPage(Index(0)), true).success.value
            .set(GuardianNamePage(Index(0)), adultName).success.value
            .set(GuardianAddressKnownPage(Index(0)), false).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val expectedChildDetails = JourneyModel.Child(
            name = childName,
            nameChangedByDeedPoll = None,
            previousNames = Nil,
            biologicalSex = ChildBiologicalSex.Female,
            dateOfBirth = now,
            countryOfRegistration = ChildBirthRegistrationCountry.England,
            birthCertificateNumber = Some(systemNumber),
            birthCertificateDetailsMatched = Matched,
            relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
            adoptingThroughLocalAuthority = false,
            previousClaimant = None,
            guardian = Some(JourneyModel.Guardian(Some(adultName), None)),
            previousGuardian = None,
            dateChildStartedLivingWithApplicant = None
          )

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors mustBe empty
          data.value.children.toList must contain only expectedChildDetails
        }

        "and the user knows their name and address" - {

          "and their address is in the UK" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = UserAnswers("id")
              .withMinimalApplicantDetails
              .withOneChild
              .withMinimalSingleIncomeDetails
              .withMinimalPaymentDetails
              .set(RelationshipStatusPage, Single).success.value
              .set(ChildLivesWithApplicantPage(Index(0)), false).success.value
              .set(GuardianNameKnownPage(Index(0)), true).success.value
              .set(GuardianNamePage(Index(0)), adultName).success.value
              .set(GuardianAddressKnownPage(Index(0)), true).success.value
              .set(GuardianAddressInUkPage(Index(0)), true).success.value
              .set(GuardianUkAddressPage(Index(0)), ukAddress).success.value
              .set(AdditionalInformationPage, NoInformation).success.value

            val expectedChildDetails = JourneyModel.Child(
              name = childName,
              nameChangedByDeedPoll = None,
              previousNames = Nil,
              biologicalSex = ChildBiologicalSex.Female,
              dateOfBirth = now,
              countryOfRegistration = ChildBirthRegistrationCountry.England,
              birthCertificateNumber = Some(systemNumber),
              birthCertificateDetailsMatched = Matched,
              relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
              adoptingThroughLocalAuthority = false,
              previousClaimant = None,
              guardian = Some(JourneyModel.Guardian(Some(adultName), Some(ukAddress))),
              previousGuardian = None,
              dateChildStartedLivingWithApplicant = None
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.children.toList must contain only expectedChildDetails
          }

          "and their address is not in the UK" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = UserAnswers("id")
              .withMinimalApplicantDetails
              .withOneChild
              .withMinimalSingleIncomeDetails
              .withMinimalPaymentDetails
              .set(RelationshipStatusPage, Single).success.value
              .set(ChildLivesWithApplicantPage(Index(0)), false).success.value
              .set(GuardianNameKnownPage(Index(0)), true).success.value
              .set(GuardianNamePage(Index(0)), adultName).success.value
              .set(GuardianAddressKnownPage(Index(0)), true).success.value
              .set(GuardianAddressInUkPage(Index(0)), false).success.value
              .set(GuardianInternationalAddressPage(Index(0)), internationalAddress).success.value
              .set(AdditionalInformationPage, NoInformation).success.value

            val expectedChildDetails = JourneyModel.Child(
              name = childName,
              nameChangedByDeedPoll = None,
              previousNames = Nil,
              biologicalSex = ChildBiologicalSex.Female,
              dateOfBirth = now,
              countryOfRegistration = ChildBirthRegistrationCountry.England,
              birthCertificateNumber = Some(systemNumber),
              birthCertificateDetailsMatched = Matched,
              relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
              adoptingThroughLocalAuthority = false,
              previousClaimant = None,
              guardian = Some(JourneyModel.Guardian(Some(adultName), Some(internationalAddress))),
              previousGuardian = None,
              dateChildStartedLivingWithApplicant = None
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.children.toList must contain only expectedChildDetails
          }
        }
      }

      "when the child lived with someone else in the past year" - {

        "and the user does not know their details" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .withMinimalPaymentDetails
            .set(RelationshipStatusPage, Single).success.value
            .set(ChildLivedWithAnyoneElsePage(Index(0)), true).success.value
            .set(PreviousGuardianNameKnownPage(Index(0)), false).success.value
            .set(DateChildStartedLivingWithApplicantPage(Index(0)), LocalDate.now).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val expectedChildDetails = JourneyModel.Child(
            name = childName,
            nameChangedByDeedPoll = None,
            previousNames = Nil,
            biologicalSex = ChildBiologicalSex.Female,
            dateOfBirth = now,
            countryOfRegistration = ChildBirthRegistrationCountry.England,
            birthCertificateNumber = Some(systemNumber),
            birthCertificateDetailsMatched = Matched,
            relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
            adoptingThroughLocalAuthority = false,
            previousClaimant = None,
            guardian = None,
            previousGuardian = Some(JourneyModel.PreviousGuardian(None, None, None)),
            dateChildStartedLivingWithApplicant = Some(LocalDate.now)
          )

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors mustBe empty
          data.value.children.toList must contain only expectedChildDetails
        }

        "and the user knows their name but not their address or phone number" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .withMinimalPaymentDetails
            .set(RelationshipStatusPage, Single).success.value
            .set(ChildLivedWithAnyoneElsePage(Index(0)), true).success.value
            .set(PreviousGuardianNameKnownPage(Index(0)), true).success.value
            .set(PreviousGuardianNamePage(Index(0)), adultName).success.value
            .set(PreviousGuardianAddressKnownPage(Index(0)), false).success.value
            .set(PreviousGuardianPhoneNumberKnownPage(Index(0)), false).success.value
            .set(DateChildStartedLivingWithApplicantPage(Index(0)), LocalDate.now).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val expectedChildDetails = JourneyModel.Child(
            name = childName,
            nameChangedByDeedPoll = None,
            previousNames = Nil,
            biologicalSex = ChildBiologicalSex.Female,
            dateOfBirth = now,
            countryOfRegistration = ChildBirthRegistrationCountry.England,
            birthCertificateNumber = Some(systemNumber),
            birthCertificateDetailsMatched = Matched,
            relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
            adoptingThroughLocalAuthority = false,
            previousClaimant = None,
            guardian = None,
            previousGuardian = Some(JourneyModel.PreviousGuardian(Some(adultName), None, None)),
            dateChildStartedLivingWithApplicant = Some(LocalDate.now)
          )

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors mustBe empty
          data.value.children.toList must contain only expectedChildDetails
        }

        "and the user knows their name and address, but not their phone number" - {

          "and their address is in the UK" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = UserAnswers("id")
              .withMinimalApplicantDetails
              .withOneChild
              .withMinimalSingleIncomeDetails
              .withMinimalPaymentDetails
              .set(RelationshipStatusPage, Single).success.value
              .set(ChildLivedWithAnyoneElsePage(Index(0)), true).success.value
              .set(PreviousGuardianNameKnownPage(Index(0)), true).success.value
              .set(PreviousGuardianNamePage(Index(0)), adultName).success.value
              .set(PreviousGuardianAddressKnownPage(Index(0)), true).success.value
              .set(PreviousGuardianAddressInUkPage(Index(0)), true).success.value
              .set(PreviousGuardianUkAddressPage(Index(0)), ukAddress).success.value
              .set(PreviousGuardianPhoneNumberKnownPage(Index(0)), false).success.value
              .set(DateChildStartedLivingWithApplicantPage(Index(0)), LocalDate.now).success.value
              .set(AdditionalInformationPage, NoInformation).success.value

            val expectedChildDetails = JourneyModel.Child(
              name = childName,
              nameChangedByDeedPoll = None,
              previousNames = Nil,
              biologicalSex = ChildBiologicalSex.Female,
              dateOfBirth = now,
              countryOfRegistration = ChildBirthRegistrationCountry.England,
              birthCertificateNumber = Some(systemNumber),
              birthCertificateDetailsMatched = Matched,
              relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
              adoptingThroughLocalAuthority = false,
              previousClaimant = None,
              guardian = None,
              previousGuardian = Some(JourneyModel.PreviousGuardian(Some(adultName), Some(ukAddress), None)),
              dateChildStartedLivingWithApplicant = Some(LocalDate.now)
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.children.toList must contain only expectedChildDetails
          }

          "and their address is not in the UK" in {

            when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

            val answers = UserAnswers("id")
              .withMinimalApplicantDetails
              .withOneChild
              .withMinimalSingleIncomeDetails
              .withMinimalPaymentDetails
              .set(RelationshipStatusPage, Single).success.value
              .set(ChildLivedWithAnyoneElsePage(Index(0)), true).success.value
              .set(PreviousGuardianNameKnownPage(Index(0)), true).success.value
              .set(PreviousGuardianNamePage(Index(0)), adultName).success.value
              .set(PreviousGuardianAddressKnownPage(Index(0)), true).success.value
              .set(PreviousGuardianAddressInUkPage(Index(0)), false).success.value
              .set(PreviousGuardianInternationalAddressPage(Index(0)), internationalAddress).success.value
              .set(PreviousGuardianPhoneNumberKnownPage(Index(0)), false).success.value
              .set(DateChildStartedLivingWithApplicantPage(Index(0)), LocalDate.now).success.value
              .set(AdditionalInformationPage, NoInformation).success.value

            val expectedChildDetails = JourneyModel.Child(
              name = childName,
              nameChangedByDeedPoll = None,
              previousNames = Nil,
              biologicalSex = ChildBiologicalSex.Female,
              dateOfBirth = now,
              countryOfRegistration = ChildBirthRegistrationCountry.England,
              birthCertificateNumber = Some(systemNumber),
              birthCertificateDetailsMatched = Matched,
              relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
              adoptingThroughLocalAuthority = false,
              previousClaimant = None,
              guardian = None,
              previousGuardian = Some(JourneyModel.PreviousGuardian(Some(adultName), Some(internationalAddress), None)),
              dateChildStartedLivingWithApplicant = Some(LocalDate.now)
            )

            val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

            errors mustBe empty
            data.value.children.toList must contain only expectedChildDetails
          }
        }

        "and the user knows their name and phone number" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .withMinimalPaymentDetails
            .set(RelationshipStatusPage, Single).success.value
            .set(ChildLivedWithAnyoneElsePage(Index(0)), true).success.value
            .set(PreviousGuardianNameKnownPage(Index(0)), true).success.value
            .set(PreviousGuardianNamePage(Index(0)), adultName).success.value
            .set(PreviousGuardianAddressKnownPage(Index(0)), false).success.value
            .set(PreviousGuardianPhoneNumberKnownPage(Index(0)), true).success.value
            .set(PreviousGuardianPhoneNumberPage(Index(0)), phoneNumber).success.value
            .set(DateChildStartedLivingWithApplicantPage(Index(0)), LocalDate.now).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val expectedChildDetails = JourneyModel.Child(
            name = childName,
            nameChangedByDeedPoll = None,
            previousNames = Nil,
            biologicalSex = ChildBiologicalSex.Female,
            dateOfBirth = now,
            countryOfRegistration = ChildBirthRegistrationCountry.England,
            birthCertificateNumber = Some(systemNumber),
            birthCertificateDetailsMatched = Matched,
            relationshipToApplicant = ApplicantRelationshipToChild.BirthChild,
            adoptingThroughLocalAuthority = false,
            previousClaimant = None,
            guardian = None,
            previousGuardian = Some(JourneyModel.PreviousGuardian(Some(adultName), None, Some(phoneNumber))),
            dateChildStartedLivingWithApplicant = Some(LocalDate.now)
          )

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors mustBe empty
          data.value.children.toList must contain only expectedChildDetails
        }
      }
    }

    "must fail and report the missing pages" - {

      "when any mandatory data is missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .remove(ApplicantNamePage).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain theSameElementsAs Seq(ApplicantNamePage, AdditionalInformationPage)
        data mustBe empty
      }

      "when the applicant is married and partner details are missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalCoupleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Married).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only(
          PartnerNamePage,
          PartnerDateOfBirthPage,
          AllPartnerNationalities,
          PartnerEmploymentStatusPage,
          PartnerIsHmfOrCivilServantPage,
          PartnerWorkedAbroadPage,
          PartnerReceivedBenefitsAbroadPage,
          PartnerNinoKnownPage,
          PartnerClaimingChildBenefitPage
        )
        data mustBe empty
      }

      "when the applicant is cohabiting and cohabitation date is missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalCoupleIncomeDetails
          .withMinimalPaymentDetails
          .withMinimalPartnerDetails
          .set(RelationshipStatusPage, Cohabiting).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only CohabitationDatePage
        data mustBe empty
      }

      "when the applicant is cohabiting and partner details are missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalCoupleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Cohabiting).success.value
          .set(CohabitationDatePage, now).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only(
          PartnerNamePage,
          PartnerDateOfBirthPage,
          AllPartnerNationalities,
          PartnerEmploymentStatusPage,
          PartnerIsHmfOrCivilServantPage,
          PartnerWorkedAbroadPage,
          PartnerReceivedBenefitsAbroadPage,
          PartnerNinoKnownPage,
          PartnerClaimingChildBenefitPage
        )
        data mustBe empty
      }

      "when the applicant is separated and separation date is missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Separated).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only SeparationDatePage
        data mustBe empty
      }

      "when the applicant's partner worked abroad but the countries are missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalCoupleIncomeDetails
          .withMinimalPaymentDetails
          .withMinimalPartnerDetails
          .set(RelationshipStatusPage, Married).success.value
          .set(PartnerWorkedAbroadPage, true).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only AllCountriesPartnerWorked
        data mustBe empty
      }

      "when the applicant's partner received benefits abroad but the countries are missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalCoupleIncomeDetails
          .withMinimalPaymentDetails
          .withMinimalPartnerDetails
          .set(RelationshipStatusPage, Married).success.value
          .set(PartnerReceivedBenefitsAbroadPage, true).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only AllCountriesPartnerReceivedBenefits
        data mustBe empty
      }

      "when the applicant is currently receiving child benefit" - {

        "and whether they want to be paid is missing" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .set(RelationshipStatusPage, Single).success.value
            .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.GettingPayments).success.value
            .set(EldestChildNamePage, eldestChildName).success.value
            .set(EldestChildDateOfBirthPage, now).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors.value.toChain.toList must contain only WantToBePaidPage
          data mustBe empty
        }

        "and they want to be paid but their benefits details are missing" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .set(RelationshipStatusPage, Single).success.value
            .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.GettingPayments).success.value
            .set(EldestChildNamePage, eldestChildName).success.value
            .set(EldestChildDateOfBirthPage, now).success.value
            .set(WantToBePaidPage, true).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors.value.toChain.toList must contain theSameElementsAs Seq(ApplicantBenefitsPage, WantToBePaidToExistingAccountPage)
          data mustBe empty
        }

        "and they want to be paid but their or their partner's benefits details are missing" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalCoupleIncomeDetails
            .withMinimalPartnerDetails
            .set(RelationshipStatusPage, Married).success.value
            .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.GettingPayments).success.value
            .set(EldestChildNamePage, eldestChildName).success.value
            .set(EldestChildDateOfBirthPage, now).success.value
            .set(WantToBePaidPage, true).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors.value.toChain.toList must contain theSameElementsAs Seq(ApplicantOrPartnerBenefitsPage, WantToBePaidToExistingAccountPage)
          data mustBe empty
        }

        "and whether they want to be paid to their existing account is missing" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .set(RelationshipStatusPage, Single).success.value
            .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.GettingPayments).success.value
            .set(EldestChildNamePage, eldestChildName).success.value
            .set(EldestChildDateOfBirthPage, now).success.value
            .set(WantToBePaidPage, true).success.value
            .set(ApplicantBenefitsPage, applicantBenefits).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors.value.toChain.toList must contain only WantToBePaidToExistingAccountPage
          data mustBe empty
        }

        "and their eldest child's details are missing" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .set(RelationshipStatusPage, Single).success.value
            .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.GettingPayments).success.value
            .set(WantToBePaidPage, true).success.value
            .set(ApplicantBenefitsPage, applicantBenefits).success.value
            .set(WantToBePaidToExistingAccountPage, true).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors.value.toChain.toList must contain theSameElementsInOrderAs Seq(
            EldestChildNamePage,
            EldestChildDateOfBirthPage
          )

          data mustBe empty
        }

        "and they do not want to be paid to their existing account but whether they have a suitable account is missing" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .set(RelationshipStatusPage, Single).success.value
            .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.GettingPayments).success.value
            .set(EldestChildNamePage, eldestChildName).success.value
            .set(EldestChildDateOfBirthPage, now).success.value
            .set(WantToBePaidPage, true).success.value
            .set(ApplicantBenefitsPage, applicantBenefits).success.value
            .set(WantToBePaidToExistingAccountPage, false).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors.value.toChain.toList must contain only ApplicantHasSuitableAccountPage
          data mustBe empty
        }

        "and they do not want to be paid to their existing account but the account details are missing" in {

          when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

          val answers = UserAnswers("id")
            .withMinimalApplicantDetails
            .withOneChild
            .withMinimalSingleIncomeDetails
            .set(RelationshipStatusPage, Single).success.value
            .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.GettingPayments).success.value
            .set(EldestChildNamePage, eldestChildName).success.value
            .set(EldestChildDateOfBirthPage, now).success.value
            .set(WantToBePaidPage, true).success.value
            .set(ApplicantBenefitsPage, applicantBenefits).success.value
            .set(WantToBePaidToExistingAccountPage, false).success.value
            .set(ApplicantHasSuitableAccountPage, true).success.value
            .set(AdditionalInformationPage, NoInformation).success.value

          val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

          errors.value.toChain.toList must contain only AccountTypePage
          data mustBe empty
        }
      }

      "when the applicant wants to be paid but whether they have a suitable account is missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.NotClaiming).success.value
          .set(WantToBePaidPage, true).success.value
          .set(ApplicantBenefitsPage, applicantBenefits).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only ApplicantHasSuitableAccountPage
        data mustBe empty
      }

      "when the applicant wants to be paid but their account details are missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.NotClaiming).success.value
          .set(WantToBePaidPage, true).success.value
          .set(ApplicantBenefitsPage, applicantBenefits).success.value
          .set(ApplicantHasSuitableAccountPage, true).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only AccountTypePage
        data mustBe empty
      }

      "when the applicant wants to be paid to a bank account but their account details are missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.NotClaiming).success.value
          .set(WantToBePaidPage, true).success.value
          .set(ApplicantBenefitsPage, applicantBenefits).success.value
          .set(ApplicantHasSuitableAccountPage, true).success.value
          .set(BankAccountHolderPage, bankAccountHolder).success.value
          .set(AccountTypePage, AccountType.SortCodeAccountNumber).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain theSameElementsAs Seq(BankAccountDetailsPage)
        data mustBe empty
      }

      "when the applicant wants to be paid to a building society but their account details are missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.NotClaiming).success.value
          .set(WantToBePaidPage, true).success.value
          .set(ApplicantBenefitsPage, applicantBenefits).success.value
          .set(ApplicantHasSuitableAccountPage, true).success.value
          .set(BankAccountHolderPage, bankAccountHolder).success.value
          .set(AccountTypePage, AccountType.BuildingSocietyRollNumber).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain theSameElementsAs Seq(BuildingSocietyDetailsPage)
        data mustBe empty
      }

      "when the applicant says they have previous names but none are provided" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantHasPreviousFamilyNamePage, true).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only AllPreviousFamilyNames
        data mustBe empty
      }

      "when the applicant says they know their NINO but none is provided" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantNinoKnownPage, true).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only ApplicantNinoPage
        data mustBe empty
      }

      "when the applicant did not give a NINO but whether they have lived at their current address is missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .remove(ApplicantLivedAtCurrentAddressOneYearPage).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only ApplicantLivedAtCurrentAddressOneYearPage
        data mustBe empty
      }

      "when the applicant said they haven't always lived in the UK, but whether they currently live in the UK is missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantResidencePage, ApplicantResidence.UkAndAbroad).success.value
          .set(ApplicantUsuallyLivesInUkPage, true).success.value
          .set(ApplicantLivedAtCurrentAddressOneYearPage, true).success.value
          .set(ApplicantEmploymentStatusPage, EmploymentStatus.activeStatuses).success.value
          .set(ApplicantWorkedAbroadPage, false).success.value
          .set(ApplicantReceivedBenefitsAbroadPage, false).success.value
          .set(AdditionalInformationPage, NoInformation).success.value
          .remove(ApplicantCurrentAddressInUkPage).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only ApplicantCurrentAddressInUkPage
        data mustBe empty
      }

      "when the applicant said they haven't always lived in the UK, but whether they usually live in the UK is missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantResidencePage, ApplicantResidence.UkAndAbroad).success.value
          .set(ApplicantCurrentAddressInUkPage, true).success.value
          .set(ApplicantCurrentUkAddressPage, ukAddress).success.value
          .set(ApplicantLivedAtCurrentAddressOneYearPage, true).success.value
          .set(ApplicantEmploymentStatusPage, EmploymentStatus.activeStatuses).success.value
          .set(ApplicantWorkedAbroadPage, false).success.value
          .set(ApplicantReceivedBenefitsAbroadPage, false).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain theSameElementsAs Seq(ApplicantUsuallyLivesInUkPage, ApplicantArrivedInUkPage)
        data mustBe empty
      }

      "when the applicant said they usually live in the UK, but the date they arrived in the UK is missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantResidencePage, ApplicantResidence.UkAndAbroad).success.value
          .set(ApplicantCurrentAddressInUkPage, true).success.value
          .set(ApplicantCurrentUkAddressPage, ukAddress).success.value
          .set(ApplicantLivedAtCurrentAddressOneYearPage, true).success.value
          .set(ApplicantUsuallyLivesInUkPage, true).success.value
          .set(ApplicantEmploymentStatusPage, EmploymentStatus.activeStatuses).success.value
          .set(ApplicantWorkedAbroadPage, false).success.value
          .set(ApplicantReceivedBenefitsAbroadPage, false).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only ApplicantArrivedInUkPage
        data mustBe empty
      }

      "when the applicant said they usually live abroad, but their usual country of residence and date they arrived in the UK are missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantResidencePage, ApplicantResidence.UkAndAbroad).success.value
          .set(ApplicantCurrentAddressInUkPage, true).success.value
          .set(ApplicantCurrentUkAddressPage, ukAddress).success.value
          .set(ApplicantUsuallyLivesInUkPage, false).success.value
          .set(ApplicantLivedAtCurrentAddressOneYearPage, true).success.value
          .set(ApplicantEmploymentStatusPage, EmploymentStatus.activeStatuses).success.value
          .set(ApplicantWorkedAbroadPage, false).success.value
          .set(ApplicantReceivedBenefitsAbroadPage, false).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain theSameElementsAs Seq(ApplicantUsualCountryOfResidencePage, ApplicantArrivedInUkPage)
        data mustBe empty
      }

      "when the applicant has lived in the UK and abroad but whether they have worked or received benefits abroad are missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantResidencePage, ApplicantResidence.UkAndAbroad).success.value
          .set(ApplicantCurrentAddressInUkPage, true).success.value
          .set(ApplicantCurrentUkAddressPage, ukAddress).success.value
          .set(ApplicantUsuallyLivesInUkPage, true).success.value
          .set(ApplicantArrivedInUkPage, LocalDate.now).success.value
          .set(ApplicantLivedAtCurrentAddressOneYearPage, true).success.value
          .set(ApplicantEmploymentStatusPage, EmploymentStatus.activeStatuses).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain theSameElementsAs Seq(ApplicantWorkedAbroadPage, ApplicantReceivedBenefitsAbroadPage)
        data mustBe empty
      }

      "when the applicant has lived in the UK and abroad, worked and received benefits abroad, but the countries are missing are missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantResidencePage, ApplicantResidence.UkAndAbroad).success.value
          .set(ApplicantCurrentAddressInUkPage, true).success.value
          .set(ApplicantCurrentUkAddressPage, ukAddress).success.value
          .set(ApplicantUsuallyLivesInUkPage, true).success.value
          .set(ApplicantArrivedInUkPage, LocalDate.now).success.value
          .set(ApplicantLivedAtCurrentAddressOneYearPage, true).success.value
          .set(ApplicantEmploymentStatusPage, EmploymentStatus.activeStatuses).success.value
          .set(ApplicantWorkedAbroadPage, true).success.value
          .set(ApplicantReceivedBenefitsAbroadPage, true).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain theSameElementsAs Seq(AllCountriesApplicantWorked, AllCountriesApplicantReceivedBenefits)
        data mustBe empty
      }

      "when the applicant has always lived abroad but whether they have worked or received benefits abroad are missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantResidencePage, ApplicantResidence.AlwaysAbroad).success.value
          .set(ApplicantUsualCountryOfResidencePage, country).success.value
          .set(ApplicantCurrentInternationalAddressPage, internationalAddress).success.value
          .set(ApplicantLivedAtCurrentAddressOneYearPage, true).success.value
          .set(ApplicantEmploymentStatusPage, EmploymentStatus.activeStatuses).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain theSameElementsAs Seq(ApplicantWorkedAbroadPage, ApplicantReceivedBenefitsAbroadPage)
        data mustBe empty
      }

      "when the applicant has always lived abroad, worked and received benefits abroad, but the countries are missing are missing" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantResidencePage, ApplicantResidence.AlwaysAbroad).success.value
          .set(ApplicantUsualCountryOfResidencePage, country).success.value
          .set(ApplicantCurrentInternationalAddressPage, internationalAddress).success.value
          .set(ApplicantLivedAtCurrentAddressOneYearPage, true).success.value
          .set(ApplicantEmploymentStatusPage, EmploymentStatus.activeStatuses).success.value
          .set(ApplicantWorkedAbroadPage, true).success.value
          .set(ApplicantReceivedBenefitsAbroadPage, true).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain theSameElementsAs Seq(AllCountriesApplicantWorked, AllCountriesApplicantReceivedBenefits)
        data mustBe empty
      }

      "when the applicant said they have lived at their current address less than a year but no previous UK address is provided" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantLivedAtCurrentAddressOneYearPage, false).success.value
          .set(ApplicantPreviousAddressInUkPage, true).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only ApplicantPreviousUkAddressPage
        data mustBe empty
      }

      "when the applicant said they have lived at their current address less than a year but no previous international address is provided" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(ApplicantResidencePage, ApplicantResidence.UkAndAbroad).success.value
          .set(ApplicantCurrentAddressInUkPage, true).success.value
          .set(ApplicantUsuallyLivesInUkPage, true).success.value
          .set(ApplicantArrivedInUkPage, LocalDate.now).success.value
          .set(ApplicantCurrentUkAddressPage, ukAddress).success.value
          .set(RelationshipStatusPage, Single).success.value
          .set(ApplicantLivedAtCurrentAddressOneYearPage, false).success.value
          .set(ApplicantPreviousAddressInUkPage, false).success.value
          .set(ApplicantEmploymentStatusPage, EmploymentStatus.activeStatuses).success.value
          .set(ApplicantWorkedAbroadPage, false).success.value
          .set(ApplicantReceivedBenefitsAbroadPage, false).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only ApplicantPreviousInternationalAddressPage
        data mustBe empty
      }

      "when the applicant said they know their partner's NINO but one is not provided" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalCoupleIncomeDetails
          .withMinimalPaymentDetails
          .withMinimalPartnerDetails
          .set(RelationshipStatusPage, Married).success.value
          .set(PartnerNinoKnownPage, true).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only PartnerNinoPage
        data mustBe empty
      }

      "when the applicant said their partner is entitled to Child Benefit but their partner's eldest child's details are not provided" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalCoupleIncomeDetails
          .withMinimalPaymentDetails
          .withMinimalPartnerDetails
          .set(RelationshipStatusPage, Married).success.value
          .set(PartnerClaimingChildBenefitPage, partnerClaiming).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain theSameElementsInOrderAs Seq(
          PartnerEldestChildNamePage,
          PartnerEldestChildDateOfBirthPage
        )

        data mustBe empty
      }

      "when the applicant said their partner is waiting to hear about entitlement to Child Benefit but their partner's eldest child's details are not provided" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalCoupleIncomeDetails
          .withMinimalPaymentDetails
          .withMinimalPartnerDetails
          .set(RelationshipStatusPage, Married).success.value
          .set(PartnerClaimingChildBenefitPage, partnerClaiming).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only(
          PartnerEldestChildNamePage,
          PartnerEldestChildDateOfBirthPage
        )

        data mustBe empty
      }

      "when the applicant said a child had previous names, but none are provided" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildHasPreviousNamePage(Index(0)), true).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only(
          ChildNameChangedByDeedPollPage(Index(0)),
          AllChildPreviousNames(Index(0))
        )

        data mustBe empty
      }

      "when a child's birth was registered in England or Wales, the user said their birth certificate has a system number, but none is provided" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val country = Gen.oneOf(BirthCountry.England, BirthCountry.Wales).sample.value

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildBirthRegistrationCountryPage(Index(0)), country).success.value
          .set(BirthCertificateHasSystemNumberPage(Index(0)), true).success.value
          .remove(ChildBirthCertificateSystemNumberPage(Index(0))).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only ChildBirthCertificateSystemNumberPage(Index(0))

        data mustBe empty
      }

      "when a child's birth was registered in Scotland, the user said their birth certificate had details, but none are provided" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ScottishBirthCertificateHasNumbersPage(Index(0)), true).success.value
          .set(ChildBirthRegistrationCountryPage(Index(0)), BirthCountry.Scotland).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only ChildScottishBirthCertificateDetailsPage(Index(0))

        data mustBe empty
      }

      "when someone has claimed for this child before, but whether the user knows them is not present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(AnyoneClaimedForChildBeforePage(Index(0)), true).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only PreviousClaimantNameKnownPage(Index(0))

        data mustBe empty
      }

      "when someone has claimed for this child before and the user says they know their name, but their name is not present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(AnyoneClaimedForChildBeforePage(Index(0)), true).success.value
          .set(PreviousClaimantNameKnownPage(Index(0)), true).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only(
          PreviousClaimantNamePage(Index(0)),
          PreviousClaimantAddressKnownPage(Index(0))
        )

        data mustBe empty
      }

      "when someone has claimed for this child before and the user says they know their name and address, but whether their address is in the UK is not present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(AnyoneClaimedForChildBeforePage(Index(0)), true).success.value
          .set(PreviousClaimantNameKnownPage(Index(0)), true).success.value
          .set(PreviousClaimantNamePage(Index(0)), adultName).success.value
          .set(PreviousClaimantAddressKnownPage(Index(0)), true).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only PreviousClaimantAddressInUkPage(Index(0))

        data mustBe empty
      }

      "when someone has claimed for this child before and the user says they know their name and address, but their UK address is not present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(AnyoneClaimedForChildBeforePage(Index(0)), true).success.value
          .set(PreviousClaimantNameKnownPage(Index(0)), true).success.value
          .set(PreviousClaimantNamePage(Index(0)), adultName).success.value
          .set(PreviousClaimantAddressKnownPage(Index(0)), true).success.value
          .set(PreviousClaimantAddressInUkPage(Index(0)), true).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only PreviousClaimantUkAddressPage(Index(0))

        data mustBe empty
      }

      "when someone has claimed for this child before and the user says they know their name and address, but their international address is not present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(AnyoneClaimedForChildBeforePage(Index(0)), true).success.value
          .set(PreviousClaimantNameKnownPage(Index(0)), true).success.value
          .set(PreviousClaimantNamePage(Index(0)), adultName).success.value
          .set(PreviousClaimantAddressKnownPage(Index(0)), true).success.value
          .set(PreviousClaimantAddressInUkPage(Index(0)), false).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only PreviousClaimantInternationalAddressPage(Index(0))

        data mustBe empty
      }

      "when the child lives with someone else, but whether the user knows their details is not present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildLivesWithApplicantPage(Index(0)), false).success.value

          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only GuardianNameKnownPage(Index(0))

        data mustBe empty
      }

      "when the child lives with someone else and the user says they know their name, but the name is not present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildLivesWithApplicantPage(Index(0)), false).success.value
          .set(GuardianNameKnownPage(Index(0)), true).success.value

          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain theSameElementsAs Seq(
          GuardianNamePage(Index(0)),
          GuardianAddressKnownPage(Index(0))
        )

        data mustBe empty
      }

      "when the child lives with someone else and the user says they know their name and address, but whether their address is in the UK is not present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildLivesWithApplicantPage(Index(0)), false).success.value
          .set(GuardianNameKnownPage(Index(0)), true).success.value
          .set(GuardianNamePage(Index(0)), adultName).success.value
          .set(GuardianAddressKnownPage(Index(0)), true).success.value

          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only GuardianAddressInUkPage(Index(0))

        data mustBe empty
      }

      "when the child lives with someone else and the user says they know their name and address, but their UK address are not present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildLivesWithApplicantPage(Index(0)), false).success.value
          .set(GuardianNameKnownPage(Index(0)), true).success.value
          .set(GuardianNamePage(Index(0)), adultName).success.value
          .set(GuardianAddressKnownPage(Index(0)), true).success.value
          .set(GuardianAddressInUkPage(Index(0)), true).success.value

          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only GuardianUkAddressPage(Index(0))

        data mustBe empty
      }

      "when the child lives with someone else and the user says they know their name and address, but their international address are not present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildLivesWithApplicantPage(Index(0)), false).success.value
          .set(GuardianNameKnownPage(Index(0)), true).success.value
          .set(GuardianNamePage(Index(0)), adultName).success.value
          .set(GuardianAddressKnownPage(Index(0)), true).success.value
          .set(GuardianAddressInUkPage(Index(0)), false).success.value

          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only GuardianInternationalAddressPage(Index(0))

        data mustBe empty
      }

      "when the child lived with someone else, but whether the user knows them is not present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildLivedWithAnyoneElsePage(Index(0)), true).success.value
          .set(DateChildStartedLivingWithApplicantPage(Index(0)), LocalDate.now).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only PreviousGuardianNameKnownPage(Index(0))

        data mustBe empty
      }

      "when the child lived with someone else and the user says they know them, but their name is not present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildLivedWithAnyoneElsePage(Index(0)), true).success.value
          .set(DateChildStartedLivingWithApplicantPage(Index(0)), LocalDate.now).success.value
          .set(PreviousGuardianNameKnownPage(Index(0)), true).success.value
          .set(PreviousGuardianPhoneNumberKnownPage(Index(0)), false).success.value

          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain theSameElementsAs Seq(
          PreviousGuardianNamePage(Index(0)),
          PreviousGuardianAddressKnownPage(Index(0))
        )

        data mustBe empty
      }

      "when the child lived with someone else and the user says they know their name and address, but whether their address is in the UK is not present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildLivedWithAnyoneElsePage(Index(0)), true).success.value
          .set(PreviousGuardianNameKnownPage(Index(0)), true).success.value
          .set(PreviousGuardianNamePage(Index(0)), adultName).success.value
          .set(PreviousGuardianAddressKnownPage(Index(0)), true).success.value
          .set(PreviousGuardianPhoneNumberKnownPage(Index(0)), true).success.value
          .set(PreviousGuardianPhoneNumberPage(Index(0)), phoneNumber).success.value
          .set(DateChildStartedLivingWithApplicantPage(Index(0)), LocalDate.now).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only PreviousGuardianAddressInUkPage(Index(0))

        data mustBe empty
      }
      "when the child lived with someone else and the user says they know their name and address, but their UK address is not present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildLivedWithAnyoneElsePage(Index(0)), true).success.value
          .set(PreviousGuardianNameKnownPage(Index(0)), true).success.value
          .set(PreviousGuardianNamePage(Index(0)), adultName).success.value
          .set(PreviousGuardianAddressKnownPage(Index(0)), true).success.value
          .set(PreviousGuardianAddressInUkPage(Index(0)), true).success.value
          .set(PreviousGuardianPhoneNumberKnownPage(Index(0)), true).success.value
          .set(PreviousGuardianPhoneNumberPage(Index(0)), phoneNumber).success.value
          .set(DateChildStartedLivingWithApplicantPage(Index(0)), LocalDate.now).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only PreviousGuardianUkAddressPage(Index(0))

        data mustBe empty
      }

      "when the child lived with someone else and the user says they know their name and address, but their international address is not present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildLivedWithAnyoneElsePage(Index(0)), true).success.value
          .set(PreviousGuardianNameKnownPage(Index(0)), true).success.value
          .set(PreviousGuardianNamePage(Index(0)), adultName).success.value
          .set(PreviousGuardianAddressKnownPage(Index(0)), true).success.value
          .set(PreviousGuardianAddressInUkPage(Index(0)), false).success.value
          .set(PreviousGuardianPhoneNumberKnownPage(Index(0)), true).success.value
          .set(PreviousGuardianPhoneNumberPage(Index(0)), phoneNumber).success.value
          .set(DateChildStartedLivingWithApplicantPage(Index(0)), LocalDate.now).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only PreviousGuardianInternationalAddressPage(Index(0))

        data mustBe empty
      }

      "when the child lived with someone else in the past year and the user said they know their phone number, but the phone number is not present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withOneChild
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(ChildLivedWithAnyoneElsePage(Index(0)), true).success.value
          .set(PreviousGuardianNameKnownPage(Index(0)), true).success.value
          .set(PreviousGuardianNamePage(Index(0)), adultName).success.value
          .set(PreviousGuardianAddressKnownPage(Index(0)), true).success.value
          .set(PreviousGuardianAddressInUkPage(Index(0)), true).success.value
          .set(PreviousGuardianUkAddressPage(Index(0)), ukAddress).success.value
          .set(PreviousGuardianPhoneNumberKnownPage(Index(0)), true).success.value
          .set(DateChildStartedLivingWithApplicantPage(Index(0)), LocalDate.now).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only PreviousGuardianPhoneNumberPage(Index(0))

        data mustBe empty
      }

      "when no children are present" in {

        when(mockBrmsService.matchChild(any())(any(), any())) thenReturn Future.successful(Matched)

        val answers = UserAnswers("id")
          .withMinimalApplicantDetails
          .withMinimalSingleIncomeDetails
          .withMinimalPaymentDetails
          .set(RelationshipStatusPage, Single).success.value
          .set(AdditionalInformationPage, NoInformation).success.value

        val (errors, data) = journeyModelProvider.buildFromUserAnswers(answers).futureValue.pad

        errors.value.toChain.toList must contain only AllChildSummaries

        data mustBe empty
      }
    }
  }

  implicit class UserAnswersOps(answers: UserAnswers) {

    def withMinimalApplicantDetails: UserAnswers =
      answers
        .set(ApplicantNinoKnownPage, false).success.value
        .set(ApplicantNamePage, applicantName).success.value
        .set(ApplicantHasPreviousFamilyNamePage, false).success.value
        .set(ApplicantDateOfBirthPage, now).success.value
        .set(ApplicantPhoneNumberPage, phoneNumber).success.value
        .set(ApplicantNationalityPage(Index(0)), applicantNationality).success.value
        .set(ApplicantResidencePage, ApplicantResidence.AlwaysUk).success.value
        .set(ApplicantCurrentUkAddressPage, ukAddress).success.value
        .set(ApplicantLivedAtCurrentAddressOneYearPage, true).success.value
        .set(ApplicantIsHmfOrCivilServantPage, false).success.value
        .set(CurrentlyReceivingChildBenefitPage, CurrentlyReceivingChildBenefit.NotClaiming).success.value

    def withMinimalPartnerDetails: UserAnswers =
      answers
        .set(PartnerNamePage, partnerName).success.value
        .set(PartnerNinoKnownPage, false).success.value
        .set(PartnerDateOfBirthPage, now).success.value
        .set(PartnerNationalityPage(Index(0)), partnerNationality).success.value
        .set(PartnerEmploymentStatusPage, EmploymentStatus.activeStatuses).success.value
        .set(PartnerIsHmfOrCivilServantPage, false).success.value
        .set(PartnerWorkedAbroadPage, false).success.value
        .set(PartnerReceivedBenefitsAbroadPage, false).success.value
        .set(PartnerClaimingChildBenefitPage, PartnerClaimingChildBenefit.NotClaiming).success.value

    def withOneChild: UserAnswers =
      answers
        .set(ChildNamePage(Index(0)), childName).success.value
        .set(ChildHasPreviousNamePage(Index(0)), false).success.value
        .set(ChildBiologicalSexPage(Index(0)), biologicalSex).success.value
        .set(ChildDateOfBirthPage(Index(0)), now).success.value
        .set(ChildBirthRegistrationCountryPage(Index(0)), ChildBirthRegistrationCountry.England).success.value
        .set(BirthCertificateHasSystemNumberPage(Index(0)), true).success.value
        .set(ChildBirthCertificateSystemNumberPage(Index(0)), systemNumber).success.value
        .set(ApplicantRelationshipToChildPage(Index(0)), relationshipToChild).success.value
        .set(AdoptingThroughLocalAuthorityPage(Index(0)), false).success.value
        .set(AnyoneClaimedForChildBeforePage(Index(0)), false).success.value
        .set(ChildLivesWithApplicantPage(Index(0)), true).success.value
        .set(ChildLivedWithAnyoneElsePage(Index(0)), false).success.value

    def withMinimalSingleIncomeDetails: UserAnswers =
      answers
        .set(ApplicantIncomePage, Income.BelowLowerThreshold).success.value

    def withMinimalCoupleIncomeDetails: UserAnswers =
      answers
        .set(ApplicantOrPartnerIncomePage, Income.BelowLowerThreshold).success.value

    def withMinimalPaymentDetails: UserAnswers =
      answers
        .set(WantToBePaidPage, false).success.value
  }
}
