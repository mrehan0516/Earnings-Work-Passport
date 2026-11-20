package com.example

import com.example.domain.rules.DemoDataFactory
import com.example.domain.rules.PassportLedgerBuilder
import com.example.model.BankCredit
import com.example.model.ConfirmationRequest
import com.example.model.EvidenceStatus
import com.example.model.IncomeRecord
import com.example.model.Reconciliation
import com.example.model.WorkRelationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassportLedgerBuilderTest {

    private fun buildAshaDemoWorkspace(): Triple<List<IncomeRecord>, List<BankCredit>, Triple<List<WorkRelationship>, List<Reconciliation>, List<ConfirmationRequest>>> {
        val seed = DemoDataFactory.buildAshaDemoSeed("test_user")

        val incomeRecords = seed.incomeRecords.map { m ->
            IncomeRecord(
                id = m["_docId"] as String,
                userId = m["userId"] as String,
                workerId = m["workerId"] as String,
                partyId = m["partyId"] as String,
                partyName = m["partyName"] as String,
                workGiverPartyId = m["workGiverPartyId"] as String,
                workGiverPartyName = m["workGiverPartyName"] as String,
                payerPartyId = m["payerPartyId"] as String,
                payerPartyName = m["payerPartyName"] as String,
                documentId = m["documentId"] as String,
                amountRupees = (m["amountRupees"] as Number).toLong(),
                earnedDate = m["earnedDate"] as String,
                periodLabel = m["periodLabel"] as String,
                referenceCode = m["referenceCode"] as String,
                invoiceNumber = m["invoiceNumber"] as String,
                periodStart = m["periodStart"] as String,
                periodEnd = m["periodEnd"] as String,
                grossAmountRupees = (m["grossAmountRupees"] as Number).toLong(),
                deductionsRupees = (m["deductionsRupees"] as Number).toLong(),
                netAmountRupees = (m["netAmountRupees"] as Number).toLong(),
                invoiceAmountRupees = (m["invoiceAmountRupees"] as Number).toLong(),
                evidenceStatus = m["evidenceStatus"] as String,
                workerConfirmed = m["workerConfirmed"] as Boolean,
                simulatedApi = m["simulatedApi"] as Boolean,
                duplicateGroupKey = m["duplicateGroupKey"] as String,
                groupedIntoRecordId = m["groupedIntoRecordId"] as String,
                matchOutcome = m["matchOutcome"] as String,
                paymentStatus = m["paymentStatus"] as String,
                paymentDate = m["paymentDate"] as String,
                conflictReason = m["conflictReason"] as String,
                manuallyDisputed = m["manuallyDisputed"] as Boolean,
                notes = m["notes"] as String
            )
        }

        val bankCredits = seed.bankCredits.map { m ->
            BankCredit(
                id = m["_docId"] as String,
                userId = m["userId"] as String,
                workerId = m["workerId"] as String,
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

        val relationships = seed.relationships.map { m ->
            WorkRelationship(
                id = m["_docId"] as String,
                userId = m["userId"] as String,
                workerId = m["workerId"] as String,
                partyId = m["partyId"] as String,
                roleTitle = m["roleTitle"] as String,
                payoutCycle = m["payoutCycle"] as String,
                status = m["status"] as String,
                relationshipStatus = m["relationshipStatus"] as String,
                startedOn = m["startedOn"] as String
            )
        }

        val reconciliations = seed.reconciliations.map { m ->
            Reconciliation(
                id = m["_docId"] as String,
                userId = m["userId"] as String,
                workerId = m["workerId"] as String,
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
                userId = m["userId"] as String,
                workerId = m["workerId"] as String,
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

        return Triple(incomeRecords, bankCredits, Triple(relationships, reconciliations, confirmations))
    }

    @Test
    fun ashaDemoSeedProducesFourDefinedSummaryLinesAndDeduplicatedRows() {
        val (incomes, credits, extra) = buildAshaDemoWorkspace()
        val (rels, recons, confs) = extra

        val model = PassportLedgerBuilder.build(
            incomeRecords = incomes,
            bankCredits = credits,
            relationships = rels,
            reconciliations = recons,
            confirmations = confs
        )

        assertTrue(model.hasAnyActivity)

        // 1. Received and bank-matched: RideCo 11,000 (counted once) + Ravi Design Studio 12,000 = 23,000
        assertEquals(23000L, model.summary.receivedBankMatchedRupees)
        assertEquals(2, model.summary.receivedBankMatchedCount)

        // 2. Claimed but unpaid or disputed: RideCo simulated API 8,800 (unpaid) + Kumar Stores 5,000 (disputed) = 13,800
        assertEquals(13800L, model.summary.claimedUnpaidOrDisputedRupees)
        assertEquals(8800L, model.summary.claimedUnpaidRupees)
        assertEquals(1, model.summary.claimedUnpaidCount)
        assertEquals(5000L, model.summary.disputedRupees)
        assertEquals(1, model.summary.disputedCount)

        // 3. Self-declared: Cash Tips 800
        assertEquals(800L, model.summary.selfDeclaredRupees)
        assertEquals(1, model.summary.selfDeclaredCount)

        // 4. Not counted (unclassified credits): UPI MEENA RAO 3,000
        assertEquals(3000L, model.summary.notCountedUnclassifiedCreditsRupees)
        assertEquals(1, model.summary.notCountedUnclassifiedCreditsCount)

        // Deduplicated rows: 6 total income docs collapse to 5 primary rows (RideCo duplicate grouped)
        assertEquals(5, model.rows.size)
        assertEquals(5, model.summary.totalPrimaryIncomeCount)
    }

    @Test
    fun monthlyBarChartPlotsSixMonthsEndingAtLatestMatchedCredit() {
        val (incomes, credits, extra) = buildAshaDemoWorkspace()
        val (rels, recons, confs) = extra

        val model = PassportLedgerBuilder.build(
            incomeRecords = incomes,
            bankCredits = credits,
            relationships = rels,
            reconciliations = recons,
            confirmations = confs
        )

        assertEquals(6, model.monthlyBars.size)
        assertEquals(
            listOf("2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11"),
            model.monthlyBars.map { it.yearMonth }
        )
        assertEquals(
            listOf("Jun", "Jul", "Aug", "Sep", "Oct", "Nov"),
            model.monthlyBars.map { it.shortLabel }
        )
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 23000L), model.monthlyBars.map { it.amountRupees })
        assertEquals(2, model.monthlyBars.last().matchedCount)
    }

    @Test
    fun whyExplanationsAndColumnChipsMatchPlainLanguageRules() {
        val (incomes, credits, extra) = buildAshaDemoWorkspace()
        val (rels, recons, confs) = extra

        val model = PassportLedgerBuilder.build(
            incomeRecords = incomes,
            bankCredits = credits,
            relationships = rels,
            reconciliations = recons,
            confirmations = confs
        )

        // RideCo matched statement (9-15 Nov): grouped duplicate + bank credit on 18 Nov
        val rideCoMatched = model.rows.first { it.recordId.endsWith("_inc_rideco_nov9") }
        assertEquals(EvidenceStatus.SOURCE_MATCHED, rideCoMatched.evidenceStatus)
        assertEquals(2, rideCoMatched.linkedFileCount)
        assertEquals("Net Rs 11,000", rideCoMatched.primaryAmountLabel)
        assertTrue(rideCoMatched.amountBreakdownLines.any { it.contains("Gross Rs 12,000") })
        assertEquals("Received · 18 Nov", rideCoMatched.paymentStatusLabel)
        assertTrue(rideCoMatched.whyExplanation.contains("Matched to a Rs 11,000 bank credit on 18 Nov"))
        assertTrue(rideCoMatched.whyExplanation.contains("Platform report and bank credit are one payment, counted once."))
        assertTrue(rideCoMatched.whyExplanation.contains("2 uploaded representations"))

        // Ravi Design Studio INV-102: invoice + client confirmed + bank-matched
        val raviRow = model.rows.first { it.recordId.endsWith("_inc_ravi_inv102") }
        assertEquals(EvidenceStatus.SOURCE_MATCHED, raviRow.evidenceStatus)
        assertEquals("Invoice Rs 12,000", raviRow.primaryAmountLabel)
        assertEquals("Confirmed (Simulated)", raviRow.relationshipStatusLabel)
        assertEquals("Received · 19 Nov", raviRow.paymentStatusLabel)
        assertTrue(raviRow.whyExplanation.contains("Matched to a Rs 12,000 bank credit on 19 Nov"))
        assertTrue(raviRow.whyExplanation.contains("Invoice and bank credit are one payment, counted once."))

        // Kumar Stores INV-103: disputed override + corrected amount shown
        val kumarRow = model.rows.first { it.recordId.endsWith("_inc_kumar_inv103") }
        assertEquals(EvidenceStatus.DISPUTED, kumarRow.evidenceStatus)
        assertEquals("Disputed", kumarRow.relationshipStatusLabel)
        assertEquals("Payment not yet", kumarRow.paymentStatusLabel)
        assertTrue(kumarRow.amountBreakdownLines.any { it.contains("Client: Rs 4,500") })
        assertTrue(kumarRow.whyExplanation.contains("Disputed overrides every other label"))

        // Cash tips: self-declared + principle text
        val tipsRow = model.rows.first { it.recordId.endsWith("_inc_cash_tips") }
        assertEquals(EvidenceStatus.SELF_DECLARED, tipsRow.evidenceStatus)
        assertEquals("No party linked", tipsRow.relationshipStatusLabel)
        assertEquals("Self-reported", tipsRow.paymentStatusLabel)
        assertTrue(tipsRow.whyExplanation.contains(PassportLedgerBuilder.LABEL_PRINCIPLE_TEXT))

        // RideCo simulated API: Source-verified (Simulated) + simulatedApi pill + no bank credit yet
        val simRow = model.rows.first { it.recordId.endsWith("_inc_rideco_sim_api") }
        assertEquals(EvidenceStatus.SOURCE_VERIFIED_SIMULATED, simRow.evidenceStatus)
        assertTrue(simRow.simulatedApi)
        assertEquals("No bank credit yet", simRow.paymentStatusLabel)
        assertTrue(simRow.whyExplanation.contains("Simulated"))
    }

    @Test
    fun emptyWorkspaceReturnsFriendlyEmptyModel() {
        val model = PassportLedgerBuilder.build(
            incomeRecords = emptyList(),
            bankCredits = emptyList(),
            relationships = emptyList(),
            reconciliations = emptyList(),
            confirmations = emptyList()
        )

        assertFalse(model.hasAnyActivity)
        assertTrue(model.rows.isEmpty())
        assertTrue(model.monthlyBars.isEmpty())
        assertEquals(0L, model.summary.receivedBankMatchedRupees)
        assertEquals(0L, model.summary.claimedUnpaidOrDisputedRupees)
        assertEquals(0L, model.summary.selfDeclaredRupees)
        assertEquals(0L, model.summary.notCountedUnclassifiedCreditsRupees)
    }
}
