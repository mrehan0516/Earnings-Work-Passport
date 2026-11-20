package com.example

import com.example.domain.rules.DemoDataFactory
import com.example.domain.rules.IssueDetector
import com.example.domain.rules.IssueType
import com.example.domain.rules.IssuesFilter
import com.example.domain.rules.MatchCandidate
import com.example.model.BankCredit
import com.example.model.ConfirmationRequest
import com.example.model.EvidenceStatus
import com.example.model.IncomeRecord
import com.example.model.Reconciliation
import com.example.reconciliation.MatchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueDetectorTest {

    @Test
    fun detectsAllSixCategoriesWithSideBySideDisputesAndSuccessfulMatches() {
        val seed = DemoDataFactory.buildAshaDemoSeed("test_user")
        val incomes = seed.incomeRecords.map { m ->
            IncomeRecord(
                id = m["_docId"] as String,
                userId = m["userId"] as String,
                workerId = m["workerId"] as String,
                partyId = m["partyId"] as String,
                partyName = m["partyName"] as String,
                documentId = m["documentId"] as String,
                amountRupees = (m["amountRupees"] as Number).toLong(),
                earnedDate = m["earnedDate"] as String,
                periodLabel = m["periodLabel"] as String,
                referenceCode = m["referenceCode"] as String,
                invoiceNumber = m["invoiceNumber"] as String,
                grossAmountRupees = (m["grossAmountRupees"] as Number).toLong(),
                deductionsRupees = (m["deductionsRupees"] as Number).toLong(),
                netAmountRupees = (m["netAmountRupees"] as Number).toLong(),
                invoiceAmountRupees = (m["invoiceAmountRupees"] as Number).toLong(),
                evidenceStatus = m["evidenceStatus"] as String,
                workerConfirmed = m["workerConfirmed"] as Boolean,
                simulatedApi = m["simulatedApi"] as Boolean,
                duplicateGroupKey = m["duplicateGroupKey"] as String,
                groupedIntoRecordId = m["groupedIntoRecordId"] as String,
                paymentStatus = m["paymentStatus"] as String,
                paymentDate = m["paymentDate"] as String,
                conflictReason = m["conflictReason"] as String,
                manuallyDisputed = m["manuallyDisputed"] as Boolean,
                notes = m["notes"] as String
            )
        }.toMutableList()

        // Add a "numbers do not add up" platform statement (10000 - 500 = 9500 != 9100)
        val mathBroken = IncomeRecord(
            id = "inc_math_broken",
            partyName = "MetroDrop",
            amountRupees = 9100L,
            earnedDate = "2026-11-09",
            periodLabel = "2–8 Nov 2026",
            referenceCode = "MD-45-771",
            grossAmountRupees = 10000L,
            deductionsRupees = 500L,
            netAmountRupees = 9100L,
            evidenceStatus = EvidenceStatus.DISPUTED.code,
            conflictReason = "Gross (Rs 10,000) − deductions (Rs 500) = Rs 9,500, differs from net Rs 9,100 by Rs 400."
        )
        incomes.add(mathBroken)

        val credits = seed.bankCredits.map { m ->
            BankCredit(
                id = m["_docId"] as String,
                bankLabel = m["bankLabel"] as String,
                creditDate = m["creditDate"] as String,
                amountRupees = (m["amountRupees"] as Number).toLong(),
                narration = m["narration"] as String,
                utrReference = m["utrReference"] as String,
                matchedIncomeRecordId = m["matchedIncomeRecordId"] as String,
                classificationStatus = m["classificationStatus"] as String,
                evidenceStatus = m["evidenceStatus"] as String
            )
        }

        val recons = seed.reconciliations.map { m ->
            Reconciliation(
                id = m["_docId"] as String,
                incomeRecordId = m["incomeRecordId"] as String,
                bankCreditId = m["bankCreditId"] as String,
                matchRule = m["matchRule"] as String,
                amountDeltaRupees = (m["amountDeltaRupees"] as Number).toLong(),
                dateGapDays = (m["dateGapDays"] as Number).toLong(),
                decisionStatus = m["decisionStatus"] as String,
                matchOutcome = m["matchOutcome"] as String
            )
        }

        val confirmations = seed.confirmationRequests.map { m ->
            ConfirmationRequest(
                id = m["_docId"] as String,
                incomeRecordId = m["incomeRecordId"] as String,
                partyId = m["partyId"] as String,
                partyName = m["partyName"] as String,
                requestedAmountRupees = (m["requestedAmountRupees"] as Number).toLong(),
                status = m["status"] as String,
                responseNote = m["responseNote"] as String,
                workDelivered = m["workDelivered"] as Boolean?,
                confirmedAmountRupees = (m["confirmedAmountRupees"] as Number?)?.toLong(),
                paymentSent = m["paymentSent"] as Boolean?,
                paymentDate = m["paymentDate"] as String
            )
        }

        // Also include a POSSIBLE match candidate needing worker choice
        val simRideCo = incomes.first { it.id.endsWith("_inc_rideco_sim_api") }
        val possibleCandidate = MatchCandidate(
            incomeRecord = simRideCo,
            bankCredit = BankCredit(
                id = "bc_possible_sim",
                bankLabel = "Demo Bank ••4821",
                creditDate = "2026-11-11",
                amountRupees = 8800L,
                narration = "IMPS PAYOUT",
                utrReference = "UTR-8800"
            ),
            amountDeltaRupees = 0L,
            dateGapDays = 1L,
            score = 45,
            matchRule = "Amount within Rs 1 and payout within ±3 days (low name overlap)",
            outcome = MatchOutcome.POSSIBLE
        )

        val allItems = IssueDetector.detectIssues(
            incomeRecords = incomes,
            documents = emptyList(),
            bankCredits = credits,
            reconciliations = recons,
            candidates = listOf(possibleCandidate),
            confirmationRequests = confirmations,
            includeSuccessfulMatches = true
        )

        // 1. "Numbers do not add up" with side-by-side comparison
        val mathIssue = allItems.firstOrNull { it.type == IssueType.NUMBERS_DO_NOT_ADD_UP }
        assertNotNull(mathIssue)
        assertNotNull(mathIssue!!.disputeComparison)
        assertEquals("Rs 9,100", mathIssue.disputeComparison!!.leftVersion.amountDisplay)
        assertEquals("Rs 9,500", mathIssue.disputeComparison!!.rightVersion.amountDisplay)

        // 2. Client mismatch (Kumar Stores: Rs 5,000 vs Rs 4,500) side by side
        val kumarIssue = allItems.firstOrNull {
            it.type == IssueType.DISPUTED_RECORD && it.incomeRecordId?.endsWith("_inc_kumar_inv103") == true
        }
        assertNotNull(kumarIssue)
        assertNotNull(kumarIssue!!.disputeComparison)
        assertEquals("Rs 5,000", kumarIssue.disputeComparison!!.leftVersion.amountDisplay)
        assertEquals("Rs 4,500", kumarIssue.disputeComparison!!.rightVersion.amountDisplay)

        // 3. Duplicate copy surfaced
        val dupIssue = allItems.firstOrNull { it.type == IssueType.POTENTIAL_DUPLICATE }
        assertNotNull(dupIssue)

        // 4. Unmatched / unclassified credit (UPI MEENA RAO Rs 3,000)
        val creditIssue = allItems.firstOrNull { it.type == IssueType.UNMATCHED_BANK_CREDIT }
        assertNotNull(creditIssue)
        assertEquals(3000L, creditIssue!!.amountRupees)

        // 5. Possible match needing a choice
        val choiceIssue = allItems.firstOrNull { it.type == IssueType.SUGGESTED_BANK_MATCH }
        assertNotNull(choiceIssue)

        // 6. Successful matches (RideCo 11,000 + Ravi 12,000)
        val matchedIssues = allItems.filter { it.type == IssueType.SUCCESSFUL_MATCH }
        assertEquals(2, matchedIssues.size)
        assertTrue(matchedIssues.all { !it.type.isActionable })
        assertTrue(matchedIssues.any { it.explanation.contains("counted once") })

        // Filter chips work accurately
        assertEquals(2, allItems.count { it.matchesFilter(IssuesFilter.DISPUTED) })
        assertEquals(2, allItems.count { it.matchesFilter(IssuesFilter.MATCHED) })
        assertTrue(allItems.filter { it.matchesFilter(IssuesFilter.NEEDS_ACTION) }.none { it.type == IssueType.SUCCESSFUL_MATCH })
    }

    @Test
    fun ignoreDuplicateHidesAlertWithoutDeletingRecord() {
        val primary = IncomeRecord(
            id = "inc_primary",
            partyName = "RideCo",
            amountRupees = 11000L,
            earnedDate = "2026-11-17",
            periodLabel = "9–15 Nov 2026",
            evidenceStatus = EvidenceStatus.SOURCE_MATCHED.code
        )
        val secondaryIgnored = IncomeRecord(
            id = "inc_dup_copy",
            partyName = "RideCo",
            amountRupees = 11000L,
            earnedDate = "2026-11-17",
            periodLabel = "9–15 Nov 2026 (2nd photo)",
            groupedIntoRecordId = "inc_primary",
            duplicateIgnored = true,
            evidenceStatus = EvidenceStatus.DOCUMENT_EXTRACTED.code
        )

        val issues = IssueDetector.detectIssues(
            incomeRecords = listOf(primary, secondaryIgnored),
            documents = emptyList(),
            bankCredits = emptyList(),
            reconciliations = emptyList(),
            candidates = emptyList()
        )

        assertFalse(issues.any { it.type == IssueType.POTENTIAL_DUPLICATE })
    }
}
