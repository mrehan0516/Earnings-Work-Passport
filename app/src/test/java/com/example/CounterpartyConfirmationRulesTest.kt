package com.example

import com.example.domain.rules.CounterpartyAnswerChoice
import com.example.domain.rules.CounterpartyConfirmationRules
import com.example.domain.rules.DemoOtpState
import com.example.domain.rules.IssueDetector
import com.example.domain.rules.IssueType
import com.example.domain.rules.OtpVerifyOutcome
import com.example.domain.rules.ParsedCounterpartyResponses
import com.example.domain.rules.ReconciliationEngine
import com.example.domain.rules.WorkerConfirmationResolution
import com.example.model.ConfirmationRequest
import com.example.model.ConfirmationStatus
import com.example.model.EvidenceStatus
import com.example.model.IncomeRecord
import com.example.model.Party
import com.example.model.WorkRelationship
import com.example.reconciliation.PaymentStatus
import com.example.reconciliation.ReconciliationEvidenceLabel
import com.example.reconciliation.RelationshipOutcomeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterpartyConfirmationRulesTest {

    @Test
    fun randomToken_isAtLeast32CharsUrlSafeAndUnique() {
        val token1 = CounterpartyConfirmationRules.generateRandomToken()
        val token2 = CounterpartyConfirmationRules.generateRandomToken()
        assertTrue(token1.length >= 32)
        assertTrue(token2.length >= 32)
        assertNotEquals(token1, token2)
        assertTrue(token1.matches(Regex("^[A-Za-z0-9]+$")))
    }

    @Test
    fun extractTokenFromInput_handlesDeepLinkHttpsAndRawToken() {
        val token = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789abcd"
        assertEquals(token, CounterpartyConfirmationRules.extractTokenFromInput("workpassport://confirm/$token"))
        assertEquals(token, CounterpartyConfirmationRules.extractTokenFromInput("https://workpassport.demo/confirm/$token?foo=1"))
        assertEquals(token, CounterpartyConfirmationRules.extractTokenFromInput("  $token  "))
    }

    @Test
    fun requestExpiry_isExactlySevenDays() {
        val created = 1_700_000_000_000L
        val expires = CounterpartyConfirmationRules.computeExpiryEpochMs(created)
        assertEquals(7L * 24L * 60L * 60L * 1000L, expires - created)
        assertFalse(CounterpartyConfirmationRules.isRequestExpired(expires, expires - 1000L))
        assertTrue(CounterpartyConfirmationRules.isRequestExpired(expires, expires + 1000L))
    }

    @Test
    fun demoOtp_expiresAfter5MinutesAndCanRegenerate() {
        val t0 = 1_700_000_000_000L
        val initial = DemoOtpState(activeCode = "482910", issuedAtEpochMs = t0)

        // Valid within 5 minutes
        val (verified, okOutcome) = CounterpartyConfirmationRules.verifyDemoOtp(
            state = initial,
            enteredCode = "482910",
            nowEpochMs = t0 + 4 * 60_000L
        )
        assertTrue(okOutcome is OtpVerifyOutcome.Verified)
        assertTrue(verified.isVerified())

        // Expired at 5 minutes
        val (_, expOutcome) = CounterpartyConfirmationRules.verifyDemoOtp(
            state = initial,
            enteredCode = "482910",
            nowEpochMs = t0 + 5 * 60_000L
        )
        assertTrue(expOutcome is OtpVerifyOutcome.Expired)

        // Regenerate creates fresh 5-minute window
        val regen = CounterpartyConfirmationRules.regenerateDemoOtp(
            state = initial,
            newCode = "991122",
            nowEpochMs = t0 + 5 * 60_000L
        )
        assertEquals("991122", regen.activeCode)
        assertFalse(regen.isExpired(t0 + 6 * 60_000L))
    }

    @Test
    fun demoOtp_locksFor5MinutesAfter3WrongAttempts() {
        val t0 = 1_700_000_000_000L
        var state = DemoOtpState(activeCode = "123456", issuedAtEpochMs = t0)

        val (s1, o1) = CounterpartyConfirmationRules.verifyDemoOtp(state, "000001", t0 + 1000L)
        assertEquals(OtpVerifyOutcome.WrongCode(2), o1)
        state = s1

        val (s2, o2) = CounterpartyConfirmationRules.verifyDemoOtp(state, "000002", t0 + 2000L)
        assertEquals(OtpVerifyOutcome.WrongCode(1), o2)
        state = s2

        val (s3, o3) = CounterpartyConfirmationRules.verifyDemoOtp(state, "000003", t0 + 3000L)
        assertTrue(o3 is OtpVerifyOutcome.Locked)
        assertEquals(3, s3.totalFailedAttempts)
        assertTrue(s3.isLocked(t0 + 60_000L))

        // Even the right code fails while locked
        val (_, lockedOutcome) = CounterpartyConfirmationRules.verifyDemoOtp(s3, "123456", t0 + 60_000L)
        assertTrue(lockedOutcome is OtpVerifyOutcome.Locked)

        // Unlocks after 5 minutes (re-issuing a fresh OTP works)
        val afterLock = t0 + 3000L + CounterpartyConfirmationRules.OTP_LOCKOUT_MS + 1000L
        assertFalse(s3.isLocked(afterLock))
        val refreshed = CounterpartyConfirmationRules.regenerateDemoOtp(s3, "654321", afterLock)
        val (s4, o4) = CounterpartyConfirmationRules.verifyDemoOtp(refreshed, "654321", afterLock + 1000L)
        assertTrue(o4 is OtpVerifyOutcome.Verified)
        assertTrue(s4.isVerified())
    }

    @Test
    fun positiveCounterpartyConfirmation_updatesStatusesWithoutChangingEvidenceLabel() {
        val party = Party(id = "p_client", workerId = "w1", name = "Meera Boutique")
        val rel = WorkRelationship(id = "rel_1", workerId = "w1", partyId = "p_client", relationshipStatus = "worker_declared")
        val record = IncomeRecord(
            id = "inc_1",
            workerId = "w1",
            partyId = "p_client",
            partyName = "Meera Boutique",
            amountRupees = 8500L,
            earnedDate = "2026-11-15",
            evidenceStatus = EvidenceStatus.SELF_DECLARED.code
        )
        val request = ConfirmationRequest(
            id = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789abcd",
            workerId = "w1",
            incomeRecordId = "inc_1",
            partyId = "p_client",
            partyName = "Meera Boutique",
            requestedAmountRupees = 8500L,
            claimVersion = 1,
            status = ConfirmationStatus.RESPONDED.code,
            responses = ParsedCounterpartyResponses(
                workDelivered = CounterpartyAnswerChoice.YES,
                amountCorrect = CounterpartyAnswerChoice.YES,
                paidStatus = CounterpartyAnswerChoice.YES,
                paidDate = "2026-11-16",
                claimVersionShown = 1,
                otpVerifiedAtEpochMs = 1_700_000_000_000L
            ).toFirestoreMap()
        )

        val outcome = CounterpartyConfirmationRules.evaluateRequest(request)
        assertTrue(outcome.contactVerified)
        assertTrue(outcome.workConfirmed)
        assertTrue(outcome.paymentConfirmed)
        assertFalse(outcome.triggersDispute)

        val recon = ReconciliationEngine.reconcileWorkspace(
            workerDisplayName = "Asha Rao",
            incomeRecords = listOf(record),
            documents = emptyList(),
            parties = listOf(party),
            bankCredits = emptyList(),
            confirmations = listOf(request),
            relationships = listOf(rel),
            reconciliations = emptyList()
        )

        val evaluated = requireNotNull(recon.incomeResults["inc_1"])
        // CRITICAL: Evidence label MUST NOT rise from client confirmation alone
        assertEquals(ReconciliationEvidenceLabel.SELF_DECLARED, evaluated.evidenceLabel)
        assertEquals(PaymentStatus.PAYER_CONFIRMED, evaluated.paymentStatus)
        assertEquals(
            RelationshipOutcomeStatus.COUNTERPARTY_ACKNOWLEDGED,
            recon.relationshipResults["rel_1"]?.relationshipStatus
        )
    }

    @Test
    fun noOrCorrectedAmount_triggersDisputeAndWorkerAcceptanceClearsIt() {
        val party = Party(id = "p_client", workerId = "w1", name = "Studio K")
        val record = IncomeRecord(
            id = "inc_2",
            workerId = "w1",
            partyId = "p_client",
            partyName = "Studio K",
            amountRupees = 12000L,
            earnedDate = "2026-11-14",
            evidenceStatus = EvidenceStatus.DOCUMENT_EXTRACTED.code,
            documentId = "doc_1"
        )
        val disputedReq = ConfirmationRequest(
            id = "ZyXwVuTsRqPoNmLkJiHgFeDcBa9876543210wxyz",
            workerId = "w1",
            incomeRecordId = "inc_2",
            partyId = "p_client",
            partyName = "Studio K",
            requestedAmountRupees = 12000L,
            claimVersion = 1,
            status = ConfirmationStatus.RESPONDED.code,
            responses = ParsedCounterpartyResponses(
                workDelivered = CounterpartyAnswerChoice.YES,
                amountCorrect = CounterpartyAnswerChoice.CORRECTED_AMOUNT,
                correctedAmountRupees = 10500L,
                paidStatus = CounterpartyAnswerChoice.YES,
                paidDate = "2026-11-15",
                claimVersionShown = 1,
                otpVerifiedAtEpochMs = 1_700_000_000_000L
            ).toFirestoreMap()
        )

        val reconDisputed = ReconciliationEngine.reconcileWorkspace(
            workerDisplayName = "Asha Rao",
            incomeRecords = listOf(record),
            documents = emptyList(),
            parties = listOf(party),
            bankCredits = emptyList(),
            confirmations = listOf(disputedReq),
            relationships = emptyList(),
            reconciliations = emptyList()
        )
        assertEquals(
            ReconciliationEvidenceLabel.DISPUTED,
            reconDisputed.incomeResults["inc_2"]?.evidenceLabel
        )

        // IssueDetector surfaces side-by-side comparison for the disputed confirmation
        val issues = IssueDetector.detectIssues(
            incomeRecords = listOf(record.copy(evidenceStatus = EvidenceStatus.DISPUTED.code)),
            documents = emptyList(),
            bankCredits = emptyList(),
            reconciliations = emptyList(),
            candidates = emptyList(),
            reconciliationResult = reconDisputed,
            confirmationRequests = listOf(disputedReq)
        )
        val disputedIssue = issues.firstOrNull { it.type == IssueType.DISPUTED_RECORD }
        assertNotNull(disputedIssue)
        assertNotNull(disputedIssue?.disputeComparison)
        assertTrue(disputedIssue!!.disputeComparison!!.rightVersion.amountLabel.contains("10,500"))

        // Worker accepts correction -> updates amount to 10500 and sets CORRECTION_ACCEPTED
        val acceptedReq = disputedReq.copy(
            workerResolution = WorkerConfirmationResolution.CORRECTION_ACCEPTED.code
        )
        val updatedRecord = record.copy(amountRupees = 10500L)
        val reconAccepted = ReconciliationEngine.reconcileWorkspace(
            workerDisplayName = "Asha Rao",
            incomeRecords = listOf(updatedRecord),
            documents = emptyList(),
            parties = listOf(party),
            bankCredits = emptyList(),
            confirmations = listOf(acceptedReq),
            relationships = emptyList(),
            reconciliations = emptyList()
        )
        val evaluatedAccepted = requireNotNull(reconAccepted.incomeResults["inc_2"])
        assertEquals(ReconciliationEvidenceLabel.DOCUMENT_EXTRACTED, evaluatedAccepted.evidenceLabel)
        assertEquals(PaymentStatus.PAYER_CONFIRMED, evaluatedAccepted.paymentStatus)
    }

    @Test
    fun partialAndDeclinedResponses_doNotTriggerFalseDispute() {
        val partialReq = ConfirmationRequest(
            id = "0123456789abcdef0123456789abcdef01234567",
            workerId = "w1",
            incomeRecordId = "inc_3",
            partyId = "p_client",
            partyName = "Kiran Studio",
            requestedAmountRupees = 5000L,
            status = ConfirmationStatus.RESPONDED.code,
            responses = ParsedCounterpartyResponses(
                workDelivered = CounterpartyAnswerChoice.YES,
                amountCorrect = CounterpartyAnswerChoice.UNSURE,
                paidStatus = null,
                claimVersionShown = 1,
                otpVerifiedAtEpochMs = 1_700_000_000_000L
            ).toFirestoreMap()
        )
        val partialOutcome = CounterpartyConfirmationRules.evaluateRequest(partialReq)
        assertTrue(partialOutcome.workConfirmed)
        assertFalse(partialOutcome.paymentConfirmed)
        assertFalse(partialOutcome.triggersDispute)

        val declinedReq = partialReq.copy(
            status = ConfirmationStatus.DECLINED.code,
            responses = ParsedCounterpartyResponses(
                workDelivered = CounterpartyAnswerChoice.DECLINED,
                amountCorrect = CounterpartyAnswerChoice.DECLINED,
                paidStatus = CounterpartyAnswerChoice.DECLINED,
                claimVersionShown = 1,
                otpVerifiedAtEpochMs = 1_700_000_000_000L
            ).toFirestoreMap()
        )
        val declinedOutcome = CounterpartyConfirmationRules.evaluateRequest(declinedReq)
        assertFalse(declinedOutcome.workConfirmed)
        assertFalse(declinedOutcome.paymentConfirmed)
        assertFalse(declinedOutcome.triggersDispute)
    }
}
