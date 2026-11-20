package com.example.domain.rules

import com.example.model.BankCredit
import com.example.model.ConfirmationRequest
import com.example.model.EvidenceStatus
import com.example.model.IncomeRecord
import com.example.model.PartyRelationshipStatus
import com.example.model.Reconciliation
import com.example.model.ReconciliationDecision
import com.example.model.WorkRelationship
import com.example.reconciliation.BankCreditClassification
import com.example.reconciliation.ReconciliationResult

data class LedgerSummaryMetrics(
    val receivedBankMatchedRupees: Long,
    val receivedBankMatchedCount: Int,
    val claimedUnpaidOrDisputedRupees: Long,
    val claimedUnpaidRupees: Long,
    val claimedUnpaidCount: Int,
    val disputedRupees: Long,
    val disputedCount: Int,
    val selfDeclaredRupees: Long,
    val selfDeclaredCount: Int,
    val notCountedUnclassifiedCreditsRupees: Long,
    val notCountedUnclassifiedCreditsCount: Int,
    val totalPrimaryIncomeCount: Int
)

data class MonthlyReceivedBar(
    val yearMonth: String,
    val shortLabel: String,
    val fullMonthLabel: String,
    val amountRupees: Long,
    val matchedCount: Int
)

data class EvidenceLedgerRow(
    val recordId: String,
    val sourceName: String,
    val periodDisplay: String,
    val earnedDate: String,
    val primaryAmountLabel: String,
    val amountBreakdownLines: List<String>,
    val evidenceStatus: EvidenceStatus,
    val relationshipStatusLabel: String,
    val paymentStatusLabel: String,
    val linkedFileCount: Int,
    val simulatedApi: Boolean,
    val whyHeadline: String,
    val whyExplanation: String,
    val whyFacts: List<Pair<String, String>>
)

data class PassportLedgerModel(
    val summary: LedgerSummaryMetrics,
    val monthlyBars: List<MonthlyReceivedBar>,
    val rows: List<EvidenceLedgerRow>,
    val hasAnyActivity: Boolean
)

/**
 * Pure deterministic presentation rules for the Passport home Evidence Ledger.
 * Builds the 4 defined summary lines (never a single undifferentiated "monthly income" total),
 * the 6-month received (bank-matched) bar series, and 6-column ledger rows with plain-language
 * "Why?" explanations grounded strictly in reconciliation evidence.
 */
object PassportLedgerBuilder {

    const val LABEL_PRINCIPLE_TEXT = "A label rises only when an independent record supports it."
    private const val CHART_MONTH_WINDOW = 6

    private val MONTH_SHORT_NAMES = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    private data class YearMonthKey(val year: Int, val month: Int) : Comparable<YearMonthKey> {
        override fun compareTo(other: YearMonthKey): Int {
            val yc = year.compareTo(other.year)
            return if (yc != 0) yc else month.compareTo(other.month)
        }

        fun minusMonths(delta: Int): YearMonthKey {
            val totalMonths = (year * 12 + (month - 1)) - delta
            val newYear = Math.floorDiv(totalMonths, 12)
            val newMonth = Math.floorMod(totalMonths, 12) + 1
            return YearMonthKey(newYear, newMonth)
        }

        fun toIsoString(): String = "%04d-%02d".format(java.util.Locale.ENGLISH, year, month)
        fun shortMonth(): String = MONTH_SHORT_NAMES.getOrElse(month - 1) { "" }
        fun fullMonth(): String = "${shortMonth()} $year"
    }

    fun build(
        incomeRecords: List<IncomeRecord>,
        bankCredits: List<BankCredit>,
        relationships: List<WorkRelationship>,
        reconciliations: List<Reconciliation>,
        confirmations: List<ConfirmationRequest>,
        reconciliationResult: ReconciliationResult? = null
    ): PassportLedgerModel {
        // Rule 1: Grouped duplicate representations are always counted once (primary items only)
        val primaryRecords = incomeRecords
            .filter { !it.isSecondaryDuplicate() }
            .sortedByDescending { it.earnedDate }

        val creditsById = bankCredits.associateBy { it.id }
        val creditByMatchedRecordId = bankCredits
            .filter { it.matchedIncomeRecordId.isNotBlank() }
            .associateBy { it.matchedIncomeRecordId }
        val confirmedReconByRecordId = reconciliations
            .filter { ReconciliationDecision.fromCode(it.decisionStatus) == ReconciliationDecision.CONFIRMED }
            .associateBy { it.incomeRecordId }
        val relByPartyId = relationships.associateBy { it.partyId }
        val latestConfirmationByRecordId = confirmations
            .groupBy { it.incomeRecordId }
            .mapValues { (_, list) -> list.first() }

        fun findMatchedCredit(rec: IncomeRecord): BankCredit? {
            val fromResultId = reconciliationResult?.incomeResults?.get(rec.id)?.matchedBankCreditId.orEmpty()
            if (fromResultId.isNotBlank()) {
                creditsById[fromResultId]?.let { return it }
            }
            val fromReconId = confirmedReconByRecordId[rec.id]?.bankCreditId.orEmpty()
            if (fromReconId.isNotBlank()) {
                creditsById[fromReconId]?.let { return it }
            }
            return creditByMatchedRecordId[rec.id]
        }

        // --- 1. Build the 4 strictly defined summary lines ---
        var receivedBankMatchedRupees = 0L
        var receivedBankMatchedCount = 0
        var claimedUnpaidRupees = 0L
        var claimedUnpaidCount = 0
        var disputedRupees = 0L
        var disputedCount = 0
        var selfDeclaredRupees = 0L
        var selfDeclaredCount = 0

        primaryRecords.forEach { rec ->
            val status = rec.resolvedEvidence()
            when (status) {
                EvidenceStatus.SOURCE_MATCHED -> {
                    receivedBankMatchedRupees += rec.amountRupees
                    receivedBankMatchedCount++
                }
                EvidenceStatus.DISPUTED -> {
                    disputedRupees += rec.amountRupees
                    disputedCount++
                }
                EvidenceStatus.DOCUMENT_EXTRACTED,
                EvidenceStatus.SOURCE_VERIFIED_SIMULATED -> {
                    claimedUnpaidRupees += rec.amountRupees
                    claimedUnpaidCount++
                }
                EvidenceStatus.SELF_DECLARED -> {
                    selfDeclaredRupees += rec.amountRupees
                    selfDeclaredCount++
                }
            }
        }

        val unclassifiedCredits = bankCredits.filter { credit ->
            val cls = reconciliationResult?.bankCreditResults?.get(credit.id)?.classification
                ?: BankCreditClassification.fromCode(credit.classificationStatus)
            val isUnclassified = cls == BankCreditClassification.UNCLASSIFIED ||
                cls == BankCreditClassification.POSSIBLE_PERSONAL_TRANSFER
            credit.matchedIncomeRecordId.isBlank() && isUnclassified
        }

        val summary = LedgerSummaryMetrics(
            receivedBankMatchedRupees = receivedBankMatchedRupees,
            receivedBankMatchedCount = receivedBankMatchedCount,
            claimedUnpaidOrDisputedRupees = claimedUnpaidRupees + disputedRupees,
            claimedUnpaidRupees = claimedUnpaidRupees,
            claimedUnpaidCount = claimedUnpaidCount,
            disputedRupees = disputedRupees,
            disputedCount = disputedCount,
            selfDeclaredRupees = selfDeclaredRupees,
            selfDeclaredCount = selfDeclaredCount,
            notCountedUnclassifiedCreditsRupees = unclassifiedCredits.sumOf { it.amountRupees },
            notCountedUnclassifiedCreditsCount = unclassifiedCredits.size,
            totalPrimaryIncomeCount = primaryRecords.size
        )

        // --- 2. Build 6-month received (bank-matched) bar chart series ---
        val monthlyBars = buildMonthlyReceivedBars(primaryRecords, ::findMatchedCredit)

        // --- 3. Build 6-column ledger rows with plain-language "Why?" explanations ---
        val rows = primaryRecords.map { rec ->
            val matchedCredit = findMatchedCredit(rec)
            val confirmation = latestConfirmationByRecordId[rec.id]
            val linkedCount = reconciliationResult
                ?.incomeResults
                ?.get(rec.id)
                ?.linkedItemCount
                ?.takeIf { it > 1 }
                ?: (1 + incomeRecords.count { it.groupedIntoRecordId == rec.id })

            val partyKey = rec.payerPartyId.ifBlank { rec.partyId }
            val rel = relByPartyId[partyKey] ?: relByPartyId[rec.partyId]
            val relationshipStatusLabel = resolveRelationshipStatusLabel(rec, rel, reconciliationResult)
            val paymentStatusLabel = resolvePaymentStatusLabel(rec, matchedCredit)

            val (primaryAmtLabel, breakdownLines) = formatAmountColumn(rec, confirmation)
            val periodDisplay = formatPeriodDisplay(rec)

            val (whyHeadline, whyExplanation, whyFacts) = buildWhyExplanation(
                rec = rec,
                matchedCredit = matchedCredit,
                confirmation = confirmation,
                linkedCount = linkedCount,
                relationshipStatusLabel = relationshipStatusLabel,
                paymentStatusLabel = paymentStatusLabel
            )

            EvidenceLedgerRow(
                recordId = rec.id,
                sourceName = rec.partyName.ifBlank { "Unnamed Source" },
                periodDisplay = periodDisplay,
                earnedDate = rec.earnedDate,
                primaryAmountLabel = primaryAmtLabel,
                amountBreakdownLines = breakdownLines,
                evidenceStatus = rec.resolvedEvidence(),
                relationshipStatusLabel = relationshipStatusLabel,
                paymentStatusLabel = paymentStatusLabel,
                linkedFileCount = linkedCount,
                simulatedApi = rec.simulatedApi,
                whyHeadline = whyHeadline,
                whyExplanation = whyExplanation,
                whyFacts = whyFacts
            )
        }

        return PassportLedgerModel(
            summary = summary,
            monthlyBars = monthlyBars,
            rows = rows,
            hasAnyActivity = primaryRecords.isNotEmpty() || bankCredits.isNotEmpty()
        )
    }

    private fun buildMonthlyReceivedBars(
        primaryRecords: List<IncomeRecord>,
        findMatchedCredit: (IncomeRecord) -> BankCredit?
    ): List<MonthlyReceivedBar> {
        val matchedRecords = primaryRecords.filter { it.resolvedEvidence() == EvidenceStatus.SOURCE_MATCHED }
        if (matchedRecords.isEmpty()) return emptyList()

        val totalsByMonth = mutableMapOf<YearMonthKey, Pair<Long, Int>>()
        matchedRecords.forEach { rec ->
            val credit = findMatchedCredit(rec)
            val dateStr = credit?.creditDate?.takeIf { it.isNotBlank() } ?: rec.earnedDate
            val ym = parseYearMonth(dateStr) ?: return@forEach
            val prev = totalsByMonth[ym] ?: (0L to 0)
            totalsByMonth[ym] = (prev.first + rec.amountRupees) to (prev.second + 1)
        }

        val latestMonth = totalsByMonth.keys.maxOrNull() ?: return emptyList()
        return (CHART_MONTH_WINDOW - 1 downTo 0).map { offset ->
            val ym = latestMonth.minusMonths(offset)
            val (amt, count) = totalsByMonth[ym] ?: (0L to 0)
            MonthlyReceivedBar(
                yearMonth = ym.toIsoString(),
                shortLabel = ym.shortMonth(),
                fullMonthLabel = ym.fullMonth(),
                amountRupees = amt,
                matchedCount = count
            )
        }
    }

    private fun formatAmountColumn(
        rec: IncomeRecord,
        confirmation: ConfirmationRequest?
    ): Pair<String, List<String>> {
        val breakdown = mutableListOf<String>()
        return when {
            rec.grossAmountRupees > 0L -> {
                val net = if (rec.netAmountRupees > 0L) rec.netAmountRupees else rec.amountRupees
                breakdown.add("Gross ${RupeeFormatter.format(rec.grossAmountRupees)}")
                if (rec.deductionsRupees > 0L) {
                    breakdown.add("Ded. ${RupeeFormatter.format(rec.deductionsRupees)}")
                }
                "Net ${RupeeFormatter.format(net)}" to breakdown
            }
            rec.invoiceAmountRupees > 0L || rec.invoiceNumber.isNotBlank() -> {
                val invAmt = if (rec.invoiceAmountRupees > 0L) rec.invoiceAmountRupees else rec.amountRupees
                if (rec.invoiceNumber.isNotBlank()) {
                    breakdown.add(rec.invoiceNumber)
                }
                val clientCorrected = confirmation?.confirmedAmountRupees
                if (clientCorrected != null && clientCorrected > 0L && clientCorrected != invAmt) {
                    breakdown.add("Client: ${RupeeFormatter.format(clientCorrected)}")
                }
                "Invoice ${RupeeFormatter.format(invAmt)}" to breakdown
            }
            else -> {
                RupeeFormatter.format(rec.amountRupees) to breakdown
            }
        }
    }

    private fun formatPeriodDisplay(rec: IncomeRecord): String {
        val shortDate = formatShortDayMonth(rec.earnedDate)
        return when {
            rec.periodLabel.isNotBlank() && shortDate.isNotBlank() &&
                !rec.periodLabel.contains(shortDate, ignoreCase = true) -> {
                "${rec.periodLabel} · $shortDate"
            }
            rec.periodLabel.isNotBlank() -> rec.periodLabel
            shortDate.isNotBlank() -> shortDate
            else -> rec.earnedDate.ifBlank { "Date not set" }
        }
    }

    private fun resolveRelationshipStatusLabel(
        rec: IncomeRecord,
        rel: WorkRelationship?,
        reconciliationResult: ReconciliationResult?
    ): String {
        if (rel == null) {
            return if (rec.partyId.isBlank() && rec.payerPartyId.isBlank()) {
                "No party linked"
            } else {
                PartyRelationshipStatus.WORKER_DECLARED.label
            }
        }
        val computed = reconciliationResult?.relationshipResults?.get(rel.id)?.relationshipStatus
        return computed?.label ?: rel.resolvedRelationshipStatus().label
    }

    private fun resolvePaymentStatusLabel(
        rec: IncomeRecord,
        matchedCredit: BankCredit?
    ): String {
        if (rec.resolvedEvidence() == EvidenceStatus.SOURCE_MATCHED && matchedCredit != null) {
            val creditDateShort = formatShortDayMonth(matchedCredit.creditDate)
            return if (creditDateShort.isNotBlank()) "Received · $creditDateShort" else "Received"
        }
        return when (rec.paymentStatus.lowercase()) {
            "payer_confirmed" -> {
                val payDateShort = formatShortDayMonth(rec.paymentDate)
                if (payDateShort.isNotBlank()) "Payer confirmed · $payDateShort" else "Payer confirmed"
            }
            "payment_sent" -> {
                val payDateShort = formatShortDayMonth(rec.paymentDate)
                if (payDateShort.isNotBlank()) "Payment sent · $payDateShort" else "Payment sent"
            }
            "payment_pending" -> "Payment not yet"
            else -> {
                if (rec.resolvedEvidence() == EvidenceStatus.SELF_DECLARED) {
                    "Self-reported"
                } else {
                    "No bank credit yet"
                }
            }
        }
    }

    private fun buildWhyExplanation(
        rec: IncomeRecord,
        matchedCredit: BankCredit?,
        confirmation: ConfirmationRequest?,
        linkedCount: Int,
        relationshipStatusLabel: String,
        paymentStatusLabel: String
    ): Triple<String, String, List<Pair<String, String>>> {
        val status = rec.resolvedEvidence()
        val isPlatform = rec.grossAmountRupees > 0L || rec.netAmountRupees > 0L
        val isInvoice = rec.invoiceAmountRupees > 0L || rec.invoiceNumber.isNotBlank()
        val recordTypeNoun = when {
            isPlatform -> "Platform report"
            isInvoice -> "Invoice"
            else -> "Income entry"
        }

        val headline = "${rec.partyName.ifBlank { "Entry" }} · ${status.label}"

        val explanation = buildString {
            when (status) {
                EvidenceStatus.SOURCE_MATCHED -> {
                    if (matchedCredit != null) {
                        val creditDateShort = formatShortDayMonth(matchedCredit.creditDate)
                            .ifBlank { matchedCredit.creditDate }
                        append(
                            "Matched to a ${RupeeFormatter.format(matchedCredit.amountRupees)} bank credit on $creditDateShort"
                        )
                        if (matchedCredit.narration.isNotBlank()) {
                            append(" (${matchedCredit.narration})")
                        }
                        append(". $recordTypeNoun and bank credit are one payment, counted once.")
                    } else {
                        append("Supported by an independent bank credit match. Record and bank credit are one payment, counted once.")
                    }
                    if (linkedCount > 1) {
                        append(" $linkedCount uploaded representations of this same payout are grouped together and counted once.")
                    }
                    val evaluatedConf = confirmation?.let { CounterpartyConfirmationRules.evaluateRequest(it) }
                    if (evaluatedConf != null && evaluatedConf.hasCounterpartySubmission) {
                        val payDate = formatShortDayMonth(evaluatedConf.paymentDate)
                            .ifBlank { evaluatedConf.paymentDate }
                        val dateClause = if (payDate.isNotBlank()) " on $payDate" else ""
                        if (evaluatedConf.paymentConfirmed || evaluatedConf.workConfirmed) {
                            append(" Client also responded (${evaluatedConf.summaryLine}$dateClause) — updated contact, relationship, and payment status without changing the evidence label.")
                        }
                    }
                }
                EvidenceStatus.DISPUTED -> {
                    val reason = rec.conflictReason.ifBlank { rec.notes }
                    if (reason.isNotBlank()) {
                        append("Marked Disputed because: $reason ")
                    } else {
                        append("Marked Disputed due to an unresolved mismatch. ")
                    }
                    append("Disputed overrides every other label and keeps this entry out of received totals until resolved.")
                }
                EvidenceStatus.SOURCE_VERIFIED_SIMULATED -> {
                    append("Imported from a simulated platform API feed (always labeled Simulated). ")
                    if (matchedCredit == null) {
                        append("No matching bank credit has arrived yet, so it appears under Claimed but unpaid or disputed.")
                    }
                }
                EvidenceStatus.DOCUMENT_EXTRACTED -> {
                    append("Extracted from an uploaded document and explicitly confirmed by you. No matching bank credit has been matched yet. ")
                    append(LABEL_PRINCIPLE_TEXT)
                }
                EvidenceStatus.SELF_DECLARED -> {
                    append("Entered manually by you without an independent document or bank credit yet. ")
                    append(LABEL_PRINCIPLE_TEXT)
                }
            }
        }

        val facts = mutableListOf<Pair<String, String>>()
        facts.add("Evidence label" to status.label)
        facts.add("Relationship status" to relationshipStatusLabel)
        facts.add("Payment status" to paymentStatusLabel)
        if (rec.grossAmountRupees > 0L) {
            facts.add(
                "Statement math" to "Gross ${RupeeFormatter.format(rec.grossAmountRupees)} − Deductions ${RupeeFormatter.format(rec.deductionsRupees)} = Net ${RupeeFormatter.format(rec.netAmountRupees)}"
            )
        } else if (rec.invoiceAmountRupees > 0L) {
            facts.add("Invoiced amount" to RupeeFormatter.format(rec.invoiceAmountRupees))
        }
        if (matchedCredit != null) {
            val desc = matchedCredit.narration.ifBlank { matchedCredit.bankLabel }
            val dateText = formatShortDayMonth(matchedCredit.creditDate).ifBlank { matchedCredit.creditDate }
            facts.add("Matched bank credit" to "${RupeeFormatter.format(matchedCredit.amountRupees)} on $dateText · $desc")
        }
        if (confirmation != null) {
            val evaluatedConf = CounterpartyConfirmationRules.evaluateRequest(confirmation)
            if (evaluatedConf.hasCounterpartySubmission) {
                facts.add("Client confirmation" to evaluatedConf.summaryLine)
            }
        }
        if (linkedCount > 1) {
            facts.add("Duplicate grouping" to "$linkedCount files grouped as one evidence item (counted once)")
        }
        if (rec.referenceCode.isNotBlank()) {
            facts.add("Reference" to rec.referenceCode)
        }

        return Triple(headline, explanation, facts)
    }

    fun formatShortDayMonth(isoDate: String): String {
        val parts = isoDate.trim().take(10).split("-")
        if (parts.size != 3) return ""
        val month = parts[1].toIntOrNull() ?: return ""
        val day = parts[2].toIntOrNull() ?: return ""
        if (month !in 1..12 || day !in 1..31) return ""
        return "$day ${MONTH_SHORT_NAMES[month - 1]}"
    }

    private fun parseYearMonth(raw: String): YearMonthKey? {
        val clean = raw.trim()
        val parts = clean.split("-")
        if (parts.size < 2) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].take(2).toIntOrNull() ?: return null
        if (year < 1970 || month !in 1..12) return null
        return YearMonthKey(year, month)
    }
}
