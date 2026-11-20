package com.example.reconciliation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused JVM unit test suite for the plain-code ReconciliationModule (Rules 1 through 7).
 */
class ReconciliationModuleTest {

    @Test
    fun rule1_groupsDuplicatesByFileHashAndEventKeyAndCountsOnce() {
        val primary = ReconciliationIncomeInput(
            id = "inc_rideco_1",
            partyId = "p_rideco",
            partyName = "RideCo",
            payerName = "RideCo",
            recordKind = ReconciliationRecordKind.PLATFORM,
            earnedDate = "2026-11-17",
            periodStart = "2026-11-09",
            periodEnd = "2026-11-15",
            grossAmountRupees = 12000L,
            deductionsRupees = 1000L,
            netAmountRupees = 11000L,
            amountRupees = 11000L,
            referenceCode = "RC-46-001",
            fileHash = "hash_pdf_01",
            hasDocument = true,
            workerConfirmed = true
        )
        val photoDupByEventKey = primary.copy(
            id = "inc_rideco_2_photo",
            fileHash = "hash_photo_02",
            periodLabel = "9–15 Nov 2026 (2nd photo)"
        )
        val sameHashDup = primary.copy(
            id = "inc_rideco_3_hash",
            referenceCode = "OTHER-REF",
            fileHash = "hash_pdf_01"
        )

        val result = ReconciliationModule.reconcile(
            ReconciliationInput(incomes = listOf(primary, photoDupByEventKey, sameHashDup))
        )

        assertEquals(1, result.duplicateGroups.size)
        val group = result.duplicateGroups.first()
        assertEquals(3, group.memberRecordIds.size)
        assertEquals("inc_rideco_1", group.primaryRecordId)
        assertEquals(3, result.incomeResults["inc_rideco_1"]?.linkedItemCount)
        assertEquals("inc_rideco_1", result.incomeResults["inc_rideco_2_photo"]?.groupedIntoRecordId)
        assertEquals("inc_rideco_1", result.incomeResults["inc_rideco_3_hash"]?.groupedIntoRecordId)
        assertEquals(11000L, result.countedIncomeTotalRupees)
    }

    @Test
    fun rule2And3And4_expectedAmountsDateWindowsNameRankingAndOneToOneExclusivity() {
        // Platform payout: expected=net 11000, window ±3 days, strong match on RideCo descriptor
        val platformInc = ReconciliationIncomeInput(
            id = "inc_plat",
            partyName = "RideCo",
            payerName = "RideCo",
            recordKind = ReconciliationRecordKind.PLATFORM,
            earnedDate = "2026-11-17",
            grossAmountRupees = 12000L,
            deductionsRupees = 1000L,
            netAmountRupees = 11000L,
            amountRupees = 11000L,
            referenceCode = "RC-46-001",
            hasDocument = true,
            workerConfirmed = true
        )
        // Client invoice with stated payment date: window ±7 days of 2026-11-19 -> matches 2026-11-24 (5d apart)
        val clientWithPayDate = ReconciliationIncomeInput(
            id = "inc_client_paydate",
            partyId = "p_ravi",
            partyName = "Ravi Design Studio",
            payerName = "Ravi Design Studio",
            recordKind = ReconciliationRecordKind.CLIENT_INVOICE,
            earnedDate = "2026-11-01",
            invoiceAmountRupees = 12000L,
            amountRupees = 12000L,
            invoiceNumber = "INV-102",
            hasDocument = true,
            workerConfirmed = true
        )
        val raviConfirmation = ReconciliationConfirmationInput(
            id = "conf_ravi",
            incomeRecordId = "inc_client_paydate",
            partyId = "p_ravi",
            status = ConfirmationResponseStatus.CONFIRMED_SIMULATED,
            workDelivered = true,
            confirmedAmountRupees = 12000L,
            paymentSent = true,
            paymentDate = "2026-11-19"
        )
        // Client invoice WITHOUT stated payment date: 0..30 days after invoice date (2026-11-01 -> 2026-11-25 is 24d)
        val clientNoPayDate = ReconciliationIncomeInput(
            id = "inc_client_nopay",
            partyName = "Vani Events",
            payerName = "Vani Events",
            recordKind = ReconciliationRecordKind.CLIENT_INVOICE,
            earnedDate = "2026-11-01",
            invoiceAmountRupees = 4000L,
            amountRupees = 4000L,
            hasDocument = true,
            workerConfirmed = true
        )
        // Possible match (amount + date match, zero name/ref overlap)
        val possibleInc = ReconciliationIncomeInput(
            id = "inc_possible",
            partyName = "MetroDrop",
            payerName = "MetroDrop",
            recordKind = ReconciliationRecordKind.PLATFORM,
            earnedDate = "2026-11-10",
            netAmountRupees = 2500L,
            amountRupees = 2500L,
            hasDocument = true,
            workerConfirmed = true
        )
        // Multiple match (2 equal credits in window)
        val multipleInc = ReconciliationIncomeInput(
            id = "inc_multi",
            partyName = "QuickKart",
            payerName = "QuickKart",
            recordKind = ReconciliationRecordKind.PLATFORM,
            earnedDate = "2026-11-12",
            netAmountRupees = 1500L,
            amountRupees = 1500L,
            hasDocument = true,
            workerConfirmed = true
        )

        val credits = listOf(
            // Rs 1 tolerance check: 10999 matches expected 11000 (abs diff == 1)
            ReconciliationBankCreditInput(id = "bc_plat", creditDate = "2026-11-18", amountRupees = 10999L, descriptor = "RIDECO PAYOUT"),
            ReconciliationBankCreditInput(id = "bc_ravi", creditDate = "2026-11-24", amountRupees = 12000L, descriptor = "NEFT RAVI DESIGN STUDIO INV102"),
            ReconciliationBankCreditInput(id = "bc_vani", creditDate = "2026-11-25", amountRupees = 4000L, descriptor = "IMPS VANI EVENTS"),
            ReconciliationBankCreditInput(id = "bc_possible", creditDate = "2026-11-11", amountRupees = 2500L, descriptor = "NEFT GENERIC SETTLEMENT 998"),
            ReconciliationBankCreditInput(id = "bc_multi_1", creditDate = "2026-11-12", amountRupees = 1500L, descriptor = "IMPS QUICKKART A"),
            ReconciliationBankCreditInput(id = "bc_multi_2", creditDate = "2026-11-13", amountRupees = 1500L, descriptor = "IMPS QUICKKART B")
        )

        val result = ReconciliationModule.reconcile(
            ReconciliationInput(
                incomes = listOf(platformInc, clientWithPayDate, clientNoPayDate, possibleInc, multipleInc),
                bankCredits = credits,
                confirmations = listOf(raviConfirmation)
            )
        )

        // Strong matches promote BOTH income record AND bank credit to SOURCE_MATCHED
        assertEquals(MatchOutcome.STRONG, result.incomeResults["inc_plat"]?.matchOutcome)
        assertEquals(ReconciliationEvidenceLabel.SOURCE_MATCHED, result.incomeResults["inc_plat"]?.evidenceLabel)
        assertEquals(ReconciliationEvidenceLabel.SOURCE_MATCHED, result.bankCreditResults["bc_plat"]?.evidenceLabel)

        assertEquals(MatchOutcome.STRONG, result.incomeResults["inc_client_paydate"]?.matchOutcome)
        assertEquals(ReconciliationEvidenceLabel.SOURCE_MATCHED, result.incomeResults["inc_client_paydate"]?.evidenceLabel)
        assertEquals(PaymentStatus.PAYMENT_SENT, result.incomeResults["inc_client_paydate"]?.paymentStatus)
        assertEquals("2026-11-19", result.incomeResults["inc_client_paydate"]?.paymentDate)

        assertEquals(MatchOutcome.STRONG, result.incomeResults["inc_client_nopay"]?.matchOutcome)
        assertEquals("bc_vani", result.incomeResults["inc_client_nopay"]?.matchedBankCreditId)

        // Possible and Multiple stay unmatched for worker review
        assertEquals(MatchOutcome.POSSIBLE, result.incomeResults["inc_possible"]?.matchOutcome)
        assertEquals(ReconciliationEvidenceLabel.DOCUMENT_EXTRACTED, result.incomeResults["inc_possible"]?.evidenceLabel)

        assertEquals(MatchOutcome.MULTIPLE, result.incomeResults["inc_multi"]?.matchOutcome)
        assertEquals(2, result.incomeResults["inc_multi"]?.candidates?.size)

        // Rule 4: One credit cannot match two records
        val matchedCreditIds = result.incomeResults.values.map { it.matchedBankCreditId }.filter { it.isNotBlank() }
        assertEquals(matchedCreditIds.size, matchedCreditIds.toSet().size)
    }

    @Test
    fun rule5_unmatchedCreditsClassifiedAndPersonalTransfersNeverCountedAsIncome() {
        val credits = listOf(
            ReconciliationBankCreditInput(id = "bc_meena", creditDate = "2026-11-12", amountRupees = 3000L, descriptor = "UPI MEENA RAO"),
            ReconciliationBankCreditInput(id = "bc_unknown", creditDate = "2026-11-13", amountRupees = 1800L, descriptor = "NEFT ACME SERVICES 771")
        )

        val result = ReconciliationModule.reconcile(
            ReconciliationInput(workerDisplayName = "Asha Rao", bankCredits = credits)
        )

        assertEquals(BankCreditClassification.POSSIBLE_PERSONAL_TRANSFER, result.bankCreditResults["bc_meena"]?.classification)
        assertFalse(result.bankCreditResults["bc_meena"]!!.countedAsWorkIncome)
        assertNull(result.bankCreditResults["bc_meena"]?.evidenceLabel)

        assertEquals(BankCreditClassification.UNCLASSIFIED, result.bankCreditResults["bc_unknown"]?.classification)
        assertFalse(result.bankCreditResults["bc_unknown"]!!.countedAsWorkIncome)
        assertEquals(0L, result.countedIncomeTotalRupees)
    }

    @Test
    fun rule6And7_conflictsOverrideAllLabelsClientConfirmDoesNotChangeLabelAndSimulatedAlwaysShowsSimulated() {
        // 1. Math conflict (gross - deductions != net beyond Rs 1) overrides even simulatedApi
        val mathConflict = ReconciliationIncomeInput(
            id = "inc_math_bad",
            partyName = "MetroDrop",
            recordKind = ReconciliationRecordKind.PLATFORM,
            earnedDate = "2026-11-09",
            grossAmountRupees = 10000L,
            deductionsRupees = 500L,
            netAmountRupees = 9100L,
            amountRupees = 9100L,
            hasDocument = true,
            workerConfirmed = true,
            simulatedApi = true
        )
        // 2. Client amount conflict (Kumar Stores: invoiced 5000, client confirmed 4500) -> DISPUTED
        val kumarInvoice = ReconciliationIncomeInput(
            id = "inc_kumar",
            partyId = "p_kumar",
            partyName = "Kumar Stores",
            recordKind = ReconciliationRecordKind.CLIENT_INVOICE,
            earnedDate = "2026-11-14",
            invoiceAmountRupees = 5000L,
            amountRupees = 5000L,
            hasDocument = true,
            workerConfirmed = true
        )
        val kumarConf = ReconciliationConfirmationInput(
            id = "conf_kumar",
            incomeRecordId = "inc_kumar",
            partyId = "p_kumar",
            status = ConfirmationResponseStatus.DISPUTED,
            workDelivered = true,
            confirmedAmountRupees = 4500L,
            paymentSent = false
        )
        // 3. Clean client confirmation WITHOUT bank match: changes relationship & payment status, NOT evidence label!
        val confirmedNoBank = ReconciliationIncomeInput(
            id = "inc_conf_only",
            partyId = "p_ravi",
            partyName = "Ravi Design Studio",
            recordKind = ReconciliationRecordKind.CLIENT_INVOICE,
            earnedDate = "2026-11-15",
            invoiceAmountRupees = 7000L,
            amountRupees = 7000L,
            hasDocument = true,
            workerConfirmed = true,
            aiConfidenceScore = 0.99 // AI confidence MUST never change label
        )
        val cleanConf = ReconciliationConfirmationInput(
            id = "conf_ravi",
            incomeRecordId = "inc_conf_only",
            partyId = "p_ravi",
            status = ConfirmationResponseStatus.CONFIRMED_SIMULATED,
            workDelivered = true,
            confirmedAmountRupees = 7000L,
            paymentSent = true,
            paymentDate = "2026-11-19"
        )
        // 4. Simulated API import (RideCo 2-8 Nov) -> SOURCE_VERIFIED_SIMULATED (always contains "Simulated")
        val simulatedRec = ReconciliationIncomeInput(
            id = "inc_sim",
            partyName = "RideCo",
            recordKind = ReconciliationRecordKind.PLATFORM,
            earnedDate = "2026-11-10",
            grossAmountRupees = 9500L,
            deductionsRupees = 700L,
            netAmountRupees = 8800L,
            amountRupees = 8800L,
            simulatedApi = true
        )
        // 5. Manual cash tips -> SELF_DECLARED (even with high AI confidence field)
        val manualCash = ReconciliationIncomeInput(
            id = "inc_cash",
            partyName = "Cash Tips",
            recordKind = ReconciliationRecordKind.MANUAL_OTHER,
            earnedDate = "2026-11-16",
            amountRupees = 800L,
            hasDocument = false,
            workerConfirmed = true,
            aiConfidenceScore = 1.0
        )

        val relRavi = ReconciliationRelationshipInput(
            id = "rel_ravi",
            partyId = "p_ravi",
            currentRelationshipStatus = RelationshipOutcomeStatus.WORKER_DECLARED
        )

        val result = ReconciliationModule.reconcile(
            ReconciliationInput(
                incomes = listOf(mathConflict, kumarInvoice, confirmedNoBank, simulatedRec, manualCash),
                confirmations = listOf(kumarConf, cleanConf),
                relationships = listOf(relRavi)
            )
        )

        assertEquals(ReconciliationEvidenceLabel.DISPUTED, result.incomeResults["inc_math_bad"]?.evidenceLabel)
        assertTrue(result.incomeResults["inc_math_bad"]!!.conflictReason.contains("Gross"))

        assertEquals(ReconciliationEvidenceLabel.DISPUTED, result.incomeResults["inc_kumar"]?.evidenceLabel)
        assertEquals(4500L, result.incomeResults["inc_kumar"]?.expectedReceiptAmountRupees)
        assertEquals(PaymentStatus.PAYMENT_PENDING, result.incomeResults["inc_kumar"]?.paymentStatus)

        // Client confirmation updates relationship & payment status, NOT evidence label!
        assertEquals(ReconciliationEvidenceLabel.DOCUMENT_EXTRACTED, result.incomeResults["inc_conf_only"]?.evidenceLabel)
        assertEquals(PaymentStatus.PAYMENT_SENT, result.incomeResults["inc_conf_only"]?.paymentStatus)
        assertEquals(RelationshipOutcomeStatus.CONFIRMED_SIMULATED, result.relationshipResults["rel_ravi"]?.relationshipStatus)

        // Simulated API -> source_verified and label ALWAYS contains "Simulated"
        val simLabel = result.incomeResults["inc_sim"]!!.evidenceLabel
        assertEquals(ReconciliationEvidenceLabel.SOURCE_VERIFIED_SIMULATED, simLabel)
        assertTrue(simLabel.displayLabel.contains("Simulated"))

        // Manual -> self_declared
        assertEquals(ReconciliationEvidenceLabel.SELF_DECLARED, result.incomeResults["inc_cash"]?.evidenceLabel)

        // Audit log entries emitted for label & status changes
        assertTrue(result.auditChanges.any { it.targetId == "inc_math_bad" && it.actionType == "EVIDENCE_LABEL_CHANGED" })
        assertTrue(result.auditChanges.any { it.targetId == "rel_ravi" && it.actionType == "RELATIONSHIP_STATUS_CHANGED" })
    }
}
