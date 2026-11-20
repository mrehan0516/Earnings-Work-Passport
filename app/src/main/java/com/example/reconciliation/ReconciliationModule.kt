package com.example.reconciliation

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Pure plain-code Reconciliation module.
 *
 * Strictly NO UI code, NO Android SDK imports, NO Firebase imports, and NO AI calls.
 * All 7 reconciliation rules are deterministic, side-effect free, and unit-tested.
 */

enum class ReconciliationRecordKind {
    PLATFORM,
    CLIENT_INVOICE,
    MANUAL_OTHER
}

enum class ReconciliationEvidenceLabel(val code: String, val displayLabel: String) {
    SELF_DECLARED("SELF_DECLARED", "Self-declared"),
    DOCUMENT_EXTRACTED("DOCUMENT_EXTRACTED", "Document-extracted"),
    SOURCE_MATCHED("SOURCE_MATCHED", "Source-matched"),
    SOURCE_VERIFIED_SIMULATED("SOURCE_VERIFIED_SIMULATED", "Source-verified (Simulated)"),
    DISPUTED("DISPUTED", "Disputed");

    companion object {
        fun fromCode(raw: String?): ReconciliationEvidenceLabel =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: SELF_DECLARED
    }
}

enum class MatchOutcome(val code: String, val label: String) {
    STRONG("STRONG", "Strong match"),
    POSSIBLE("POSSIBLE", "Possible match"),
    MULTIPLE("MULTIPLE", "Multiple matches"),
    NONE("NONE", "No match");

    companion object {
        fun fromCode(raw: String?): MatchOutcome =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: NONE
    }
}

enum class BankCreditClassification(val code: String, val label: String) {
    UNCLASSIFIED("UNCLASSIFIED", "Unclassified"),
    POSSIBLE_PERSONAL_TRANSFER("POSSIBLE_PERSONAL_TRANSFER", "Possible personal transfer"),
    MATCHED("MATCHED", "Matched"),
    WORK_INCOME("WORK_INCOME", "Work income"),
    PERSONAL_TRANSFER("PERSONAL_TRANSFER", "Personal transfer"),
    NON_WORK_IGNORED("NON_WORK_IGNORED", "Non-work / ignored");

    companion object {
        fun fromCode(raw: String?): BankCreditClassification =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: UNCLASSIFIED
    }
}

enum class PaymentStatus(val code: String, val label: String) {
    UNKNOWN("UNKNOWN", ""),
    PAYMENT_SENT("PAYMENT_SENT", "Payment sent"),
    PAYER_CONFIRMED("payer_confirmed", "Payer confirmed"),
    PAYMENT_PENDING("PAYMENT_PENDING", "Payment not yet");

    companion object {
        fun fromCode(raw: String?): PaymentStatus =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

enum class RelationshipOutcomeStatus(val code: String, val label: String) {
    WORKER_DECLARED("worker_declared", "Worker declared"),
    COUNTERPARTY_ACKNOWLEDGED("counterparty_acknowledged", "Counterparty acknowledged"),
    CONFIRMED_SIMULATED("confirmed_simulated", "Confirmed (Simulated)"),
    DISPUTED("disputed", "Disputed");

    companion object {
        fun fromCode(raw: String?): RelationshipOutcomeStatus =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: WORKER_DECLARED
    }
}

enum class ConfirmationResponseStatus {
    PENDING,
    RESPONDED,
    DECLINED,
    CONFIRMED_SIMULATED,
    DISPUTED
}

enum class ExistingDecisionStatus {
    SUGGESTED,
    CONFIRMED,
    REJECTED
}

data class ReconciliationIncomeInput(
    val id: String,
    val partyId: String = "",
    val partyName: String = "",
    val workGiverName: String = "",
    val payerName: String = "",
    val documentId: String = "",
    val fileHash: String = "",
    val recordKind: ReconciliationRecordKind = ReconciliationRecordKind.MANUAL_OTHER,
    val earnedDate: String = "",
    val periodStart: String = "",
    val periodEnd: String = "",
    val periodLabel: String = "",
    val referenceCode: String = "",
    val invoiceNumber: String = "",
    val amountRupees: Long = 0L,
    val grossAmountRupees: Long = 0L,
    val deductionsRupees: Long = 0L,
    val netAmountRupees: Long = 0L,
    val invoiceAmountRupees: Long = 0L,
    val workerConfirmed: Boolean = true,
    val simulatedApi: Boolean = false,
    val hasDocument: Boolean = false,
    val manuallyDisputed: Boolean = false,
    val currentEvidenceLabel: ReconciliationEvidenceLabel = ReconciliationEvidenceLabel.SELF_DECLARED,
    val currentPaymentStatus: PaymentStatus = PaymentStatus.UNKNOWN,
    val currentPaymentDate: String = "",
    val currentGroupedIntoRecordId: String = "",
    val currentDuplicateGroupKey: String = "",
    val currentMatchOutcome: MatchOutcome = MatchOutcome.NONE,
    val currentConflictReason: String = "",
    // Rule 7 invariant: AI confidence is accepted in input solely to guarantee it NEVER alters any label.
    val aiConfidenceScore: Double? = null
)

data class ReconciliationBankCreditInput(
    val id: String,
    val creditDate: String = "",
    val amountRupees: Long = 0L,
    val descriptor: String = "",
    val utrReference: String = "",
    val matchedIncomeRecordId: String = "",
    val currentClassification: BankCreditClassification = BankCreditClassification.UNCLASSIFIED,
    val currentEvidenceLabel: ReconciliationEvidenceLabel? = null
)

data class ReconciliationConfirmationInput(
    val id: String,
    val incomeRecordId: String,
    val partyId: String,
    val status: ConfirmationResponseStatus = ConfirmationResponseStatus.PENDING,
    val workDelivered: Boolean? = null,
    val confirmedAmountRupees: Long? = null,
    val paymentSent: Boolean? = null,
    val paymentDate: String = "",
    val isPublicCounterparty: Boolean = false,
    val triggersDispute: Boolean = false,
    val disputeReason: String = "",
    val correctionAcceptedByWorker: Boolean = false
)

data class ReconciliationRelationshipInput(
    val id: String,
    val partyId: String,
    val currentRelationshipStatus: RelationshipOutcomeStatus = RelationshipOutcomeStatus.WORKER_DECLARED
)

data class ReconciliationDecisionInput(
    val incomeRecordId: String,
    val bankCreditId: String,
    val decisionStatus: ExistingDecisionStatus
)

data class ReconciliationInput(
    val workerDisplayName: String = "",
    val incomes: List<ReconciliationIncomeInput> = emptyList(),
    val bankCredits: List<ReconciliationBankCreditInput> = emptyList(),
    val confirmations: List<ReconciliationConfirmationInput> = emptyList(),
    val relationships: List<ReconciliationRelationshipInput> = emptyList(),
    val existingDecisions: List<ReconciliationDecisionInput> = emptyList()
)

data class DuplicateGroup(
    val groupKey: String,
    val primaryRecordId: String,
    val memberRecordIds: List<String>,
    val matchReason: String
)

data class CreditCandidate(
    val incomeRecordId: String,
    val bankCreditId: String,
    val amountDeltaRupees: Long,
    val dateGapDays: Long,
    val nameOverlapScore: Int,
    val referenceMatched: Boolean,
    val matchRule: String
)

data class IncomeReconciliationResult(
    val incomeRecordId: String,
    val duplicateGroupKey: String,
    val groupedIntoRecordId: String,
    val linkedItemCount: Int,
    val expectedReceiptAmountRupees: Long,
    val matchOutcome: MatchOutcome,
    val matchedBankCreditId: String,
    val candidates: List<CreditCandidate>,
    val conflictReason: String,
    val evidenceLabel: ReconciliationEvidenceLabel,
    val paymentStatus: PaymentStatus,
    val paymentDate: String,
    val countedInTotals: Boolean
)

data class BankCreditReconciliationResult(
    val bankCreditId: String,
    val matchedIncomeRecordId: String,
    val classification: BankCreditClassification,
    val evidenceLabel: ReconciliationEvidenceLabel?,
    val countedAsWorkIncome: Boolean
)

data class RelationshipReconciliationResult(
    val relationshipId: String,
    val partyId: String,
    val relationshipStatus: RelationshipOutcomeStatus
)

data class StrongMatchAssignment(
    val incomeRecordId: String,
    val bankCreditId: String,
    val candidate: CreditCandidate
)

data class AuditChange(
    val actionType: String,
    val targetCollection: String,
    val targetId: String,
    val fieldName: String,
    val previousValue: String,
    val newValue: String,
    val summary: String
)

data class ReconciliationResult(
    val duplicateGroups: List<DuplicateGroup>,
    val incomeResults: Map<String, IncomeReconciliationResult>,
    val bankCreditResults: Map<String, BankCreditReconciliationResult>,
    val relationshipResults: Map<String, RelationshipReconciliationResult>,
    val newStrongMatches: List<StrongMatchAssignment>,
    val auditChanges: List<AuditChange>
) {
    val countedIncomeTotalRupees: Long
        get() = incomeResults.values
            .filter { it.countedInTotals }
            .sumOf { it.expectedReceiptAmountRupees }
}

object ReconciliationModule {

    const val AMOUNT_TOLERANCE_RUPEES = 1L
    const val PLATFORM_WINDOW_DAYS = 3L
    const val CLIENT_PAYMENT_WINDOW_DAYS = 7L
    const val CLIENT_INVOICE_MAX_DAYS = 30L

    fun reconcile(input: ReconciliationInput): ReconciliationResult {
        val confirmationsByIncome = input.confirmations.groupBy { it.incomeRecordId }
        val confirmedDecisions = input.existingDecisions.filter { it.decisionStatus == ExistingDecisionStatus.CONFIRMED }
        val rejectedPairs = input.existingDecisions
            .filter { it.decisionStatus == ExistingDecisionStatus.REJECTED }
            .map { it.incomeRecordId to it.bankCreditId }
            .toSet()

        val existingMatchedCreditByIncome = mutableMapOf<String, String>()
        val existingMatchedIncomeByCredit = mutableMapOf<String, String>()
        val validIncomeIds = input.incomes.map { it.id }.toSet()
        val validCreditIds = input.bankCredits.map { it.id }.toSet()

        confirmedDecisions.forEach { dec ->
            if (dec.incomeRecordId in validIncomeIds && dec.bankCreditId in validCreditIds) {
                if (dec.incomeRecordId !in existingMatchedCreditByIncome && dec.bankCreditId !in existingMatchedIncomeByCredit) {
                    existingMatchedCreditByIncome[dec.incomeRecordId] = dec.bankCreditId
                    existingMatchedIncomeByCredit[dec.bankCreditId] = dec.incomeRecordId
                }
            }
        }
        // Also respect direct matchedIncomeRecordId on bank credit if not already mapped and valid
        input.bankCredits.forEach { credit ->
            val targetId = credit.matchedIncomeRecordId
            if (targetId.isNotBlank() &&
                targetId in validIncomeIds &&
                credit.id !in existingMatchedIncomeByCredit &&
                targetId !in existingMatchedCreditByIncome
            ) {
                existingMatchedCreditByIncome[targetId] = credit.id
                existingMatchedIncomeByCredit[credit.id] = targetId
            }
        }

        // --- Rule 1: Duplicate grouping (same fileHash OR same normalized event key) ---
        val duplicateGrouping = groupDuplicates(input.incomes, existingMatchedCreditByIncome.keys)
        val primaryByRecordId = duplicateGrouping.primaryByRecordId
        val groupKeyByRecordId = duplicateGrouping.groupKeyByRecordId
        val membersByPrimaryId = duplicateGrouping.membersByPrimaryId

        // Clean any confirmed match that accidentally pointed to a secondary duplicate
        val secondaryIds = input.incomes.filter { primaryByRecordId[it.id] != it.id }.map { it.id }.toSet()
        secondaryIds.forEach { secId ->
            existingMatchedCreditByIncome.remove(secId)?.let { creditId ->
                existingMatchedIncomeByCredit.remove(creditId)
            }
        }

        // --- Rule 2 & Rule 6: Expected receipt amount, payment status, and conflicts ---
        val expectedAmountById = mutableMapOf<String, Long>()
        val conflictReasonById = mutableMapOf<String, String>()
        val paymentStatusById = mutableMapOf<String, Pair<PaymentStatus, String>>()
        val confirmationByIncomeLatest = mutableMapOf<String, ReconciliationConfirmationInput>()

        input.incomes.forEach { inc ->
            val latestConf = confirmationsByIncome[inc.id]?.lastOrNull()
            if (latestConf != null) {
                confirmationByIncomeLatest[inc.id] = latestConf
            }

            // Rule 2: Expected receipt amount
            val expected = computeExpectedAmount(inc, latestConf)
            expectedAmountById[inc.id] = expected

            // Rule 7: Payment status from client confirmation (does NOT alter evidence label)
            paymentStatusById[inc.id] = resolvePaymentStatus(inc, latestConf)

            // Rule 6: Conflict detection (gross - deductions != net beyond Rs 1; client amount != invoice amount)
            conflictReasonById[inc.id] = detectConflict(inc, latestConf)
        }

        // --- Rule 3 & Rule 4: Bank credit candidate scoring, outcomes & 1-to-1 matching ---
        val matchedCreditByIncome = existingMatchedCreditByIncome.toMutableMap()
        val matchedIncomeByCredit = existingMatchedIncomeByCredit.toMutableMap()
        val matchOutcomeByIncome = mutableMapOf<String, MatchOutcome>()
        val candidatesByIncome = mutableMapOf<String, List<CreditCandidate>>()
        val newStrongMatches = mutableListOf<StrongMatchAssignment>()

        // Pre-confirmed primary records are already STRONG matched
        matchedCreditByIncome.keys.forEach { incId ->
            matchOutcomeByIncome[incId] = MatchOutcome.STRONG
        }

        // Credits locked out from auto-matching: already matched, or explicitly marked personal/non-work by worker
        val blockedCreditIds = input.bankCredits
            .filter { credit ->
                credit.id in matchedIncomeByCredit ||
                    credit.currentClassification == BankCreditClassification.PERSONAL_TRANSFER ||
                    credit.currentClassification == BankCreditClassification.NON_WORK_IGNORED
            }
            .map { it.id }
            .toSet()

        val availableCredits = input.bankCredits.filter { it.id !in blockedCreditIds }
        val unmatchedPrimaryIncomes = input.incomes.filter { inc ->
            inc.id !in secondaryIds &&
                inc.id !in matchedCreditByIncome &&
                conflictReasonById[inc.id].isNullOrBlank()
        }

        // Compute ranked candidate list for every unmatched primary income record
        val rawCandidatesByIncome = mutableMapOf<String, List<CreditCandidate>>()
        for (inc in unmatchedPrimaryIncomes) {
            val expected = expectedAmountById[inc.id] ?: inc.amountRupees
            if (expected <= 0L) {
                rawCandidatesByIncome[inc.id] = emptyList()
                continue
            }
            val latestConf = confirmationByIncomeLatest[inc.id]
            val cands = availableCredits
                .asSequence()
                .filter { credit -> (inc.id to credit.id) !in rejectedPairs }
                .mapNotNull { credit -> evaluateCandidate(inc, expected, latestConf, credit) }
                .sortedWith(
                    compareByDescending<CreditCandidate> { it.nameOverlapScore }
                        .thenByDescending { it.referenceMatched }
                        .thenBy { abs(it.amountDeltaRupees) }
                        .thenBy { abs(it.dateGapDays) }
                        .thenBy { it.bankCreditId }
                )
                .toList()
            rawCandidatesByIncome[inc.id] = cands
        }

        // Determine per-income strong candidates while respecting Rule 4 (1 credit -> max 1 income)
        // First detect if two different income records are tied for the exact same top credit
        val strongProposals = mutableListOf<Pair<String, CreditCandidate>>()
        val competingByCredit = mutableMapOf<String, MutableList<Pair<String, CreditCandidate>>>()

        for ((incId, cands) in rawCandidatesByIncome) {
            if (cands.isEmpty()) continue
            val top = cands.first()
            val second = cands.getOrNull(1)
            val isUniquelyStrongForIncome = isStrongCandidate(top, second)
            if (isUniquelyStrongForIncome) {
                competingByCredit.getOrPut(top.bankCreditId) { mutableListOf() }.add(incId to top)
            }
        }

        val tiedContestedIncomeIds = mutableSetOf<String>()
        for ((_, contenders) in competingByCredit) {
            if (contenders.size == 1) {
                strongProposals.add(contenders.first())
            } else {
                val sorted = contenders.sortedWith(
                    compareByDescending<Pair<String, CreditCandidate>> { it.second.nameOverlapScore }
                        .thenByDescending { it.second.referenceMatched }
                        .thenBy { abs(it.second.amountDeltaRupees) }
                        .thenBy { abs(it.second.dateGapDays) }
                )
                val first = sorted[0]
                val second = sorted[1]
                // Only auto-pick if first clearly beats second on name/reference overlap; otherwise flag MULTIPLE
                if (first.second.nameOverlapScore > second.second.nameOverlapScore) {
                    strongProposals.add(first)
                } else {
                    contenders.forEach { tiedContestedIncomeIds.add(it.first) }
                }
            }
        }

        // Commit 1-to-1 STRONG assignments
        val claimedCreditIds = matchedIncomeByCredit.keys.toMutableSet()
        for ((incId, cand) in strongProposals.sortedByDescending { it.second.nameOverlapScore }) {
            if (incId !in matchedCreditByIncome && cand.bankCreditId !in claimedCreditIds) {
                matchedCreditByIncome[incId] = cand.bankCreditId
                matchedIncomeByCredit[cand.bankCreditId] = incId
                claimedCreditIds.add(cand.bankCreditId)
                matchOutcomeByIncome[incId] = MatchOutcome.STRONG
                candidatesByIncome[incId] = listOf(cand)
                newStrongMatches.add(StrongMatchAssignment(incId, cand.bankCreditId, cand))
            }
        }

        // Finalize outcomes (POSSIBLE / MULTIPLE / NONE) for remaining unmatched primary records
        for (inc in input.incomes) {
            if (inc.id in secondaryIds) {
                val primaryId = primaryByRecordId[inc.id] ?: inc.id
                matchOutcomeByIncome[inc.id] = matchOutcomeByIncome[primaryId] ?: MatchOutcome.NONE
                candidatesByIncome[inc.id] = emptyList()
                continue
            }
            if (inc.id in matchedCreditByIncome) {
                matchOutcomeByIncome[inc.id] = MatchOutcome.STRONG
                candidatesByIncome.putIfAbsent(inc.id, emptyList())
                continue
            }
            val remaining = rawCandidatesByIncome[inc.id]
                .orEmpty()
                .filter { it.bankCreditId !in claimedCreditIds }
            candidatesByIncome[inc.id] = remaining
            matchOutcomeByIncome[inc.id] = when {
                remaining.isEmpty() -> MatchOutcome.NONE
                inc.id in tiedContestedIncomeIds -> MatchOutcome.MULTIPLE
                remaining.size > 1 -> MatchOutcome.MULTIPLE
                else -> MatchOutcome.POSSIBLE
            }
        }

        // --- Rule 7: Evidence labels, bank credit classifications, relationship statuses & audit diffs ---
        val auditChanges = mutableListOf<AuditChange>()
        val incomeResults = linkedMapOf<String, IncomeReconciliationResult>()

        for (inc in input.incomes) {
            val primaryId = primaryByRecordId[inc.id] ?: inc.id
            val isSecondary = primaryId != inc.id
            val groupedInto = if (isSecondary) primaryId else ""
            val groupKey = groupKeyByRecordId[inc.id].orEmpty()
            val linkedCount = if (isSecondary) 0 else (membersByPrimaryId[inc.id]?.size ?: 1)
            val expectedAmt = expectedAmountById[inc.id] ?: inc.amountRupees
            val conflictReason = conflictReasonById[inc.id].orEmpty()
            val matchedCreditId = matchedCreditByIncome[inc.id].orEmpty()
            val outcome = matchOutcomeByIncome[inc.id] ?: MatchOutcome.NONE
            val (payStatus, payDate) = paymentStatusById[inc.id] ?: (PaymentStatus.UNKNOWN to "")

            // Rule 7 strict precedence (AI confidence NEVER changes any label; client confirmation NEVER changes label):
            // 1. Disputed overrides every other label
            // 2. Simulated import -> source_verified (always displays Simulated)
            // 3. Strong/confirmed bank match -> source_matched
            // 4. AI-read + worker-confirmed document -> document_extracted
            // 5. Manual -> self_declared
            val resolvedLabel = resolveIncomeEvidenceLabel(
                hasConflict = conflictReason.isNotBlank(),
                simulatedApi = inc.simulatedApi,
                hasStrongOrConfirmedBankMatch = matchedCreditId.isNotBlank(),
                hasDocument = inc.hasDocument,
                workerConfirmed = inc.workerConfirmed
            )

            val counted = !isSecondary && resolvedLabel != ReconciliationEvidenceLabel.DISPUTED

            incomeResults[inc.id] = IncomeReconciliationResult(
                incomeRecordId = inc.id,
                duplicateGroupKey = groupKey,
                groupedIntoRecordId = groupedInto,
                linkedItemCount = linkedCount,
                expectedReceiptAmountRupees = expectedAmt,
                matchOutcome = outcome,
                matchedBankCreditId = matchedCreditId,
                candidates = candidatesByIncome[inc.id].orEmpty(),
                conflictReason = conflictReason,
                evidenceLabel = resolvedLabel,
                paymentStatus = payStatus,
                paymentDate = payDate,
                countedInTotals = counted
            )

            // Emit audit entries for every label or status change on this income record
            if (resolvedLabel != inc.currentEvidenceLabel) {
                val reasonSuffix = when {
                    conflictReason.isNotBlank() -> " ($conflictReason)"
                    matchedCreditId.isNotBlank() -> " (strong bank credit match)"
                    inc.simulatedApi -> " (simulated API import)"
                    else -> ""
                }
                auditChanges.add(
                    AuditChange(
                        actionType = "EVIDENCE_LABEL_CHANGED",
                        targetCollection = "income_records",
                        targetId = inc.id,
                        fieldName = "evidenceStatus",
                        previousValue = inc.currentEvidenceLabel.code,
                        newValue = resolvedLabel.code,
                        summary = "${inc.partyName.ifBlank { inc.id }} label changed: ${inc.currentEvidenceLabel.displayLabel} -> ${resolvedLabel.displayLabel}$reasonSuffix.".take(300)
                    )
                )
            }
            if (payStatus != inc.currentPaymentStatus ||
                ((payStatus == PaymentStatus.PAYMENT_SENT || payStatus == PaymentStatus.PAYER_CONFIRMED) &&
                    payDate != inc.currentPaymentDate)
            ) {
                val datePart = if (payDate.isNotBlank()) " ($payDate)" else ""
                auditChanges.add(
                    AuditChange(
                        actionType = "PAYMENT_STATUS_CHANGED",
                        targetCollection = "income_records",
                        targetId = inc.id,
                        fieldName = "paymentStatus",
                        previousValue = inc.currentPaymentStatus.code,
                        newValue = payStatus.code,
                        summary = "${inc.partyName.ifBlank { inc.id }} payment status updated to ${payStatus.label.ifBlank { payStatus.code }}$datePart (evidence label unchanged).".take(300)
                    )
                )
            }
            if (groupedInto != inc.currentGroupedIntoRecordId && groupedInto.isNotBlank()) {
                auditChanges.add(
                    AuditChange(
                        actionType = "DUPLICATE_GROUPED",
                        targetCollection = "income_records",
                        targetId = inc.id,
                        fieldName = "groupedIntoRecordId",
                        previousValue = inc.currentGroupedIntoRecordId,
                        newValue = groupedInto,
                        summary = "Grouped duplicate representation of ${inc.partyName.ifBlank { inc.id }} with primary item $groupedInto so it is counted once.".take(300)
                    )
                )
            }
        }

        // --- Rule 5 & Rule 7: Bank credit classifications & labels ---
        val bankCreditResults = linkedMapOf<String, BankCreditReconciliationResult>()
        for (credit in input.bankCredits) {
            val matchedIncId = matchedIncomeByCredit[credit.id].orEmpty()
            val matchedIncResult = incomeResults[matchedIncId]
            val isMatched = matchedIncId.isNotBlank() &&
                matchedIncResult != null &&
                matchedIncResult.evidenceLabel != ReconciliationEvidenceLabel.DISPUTED

            val resolvedClassification: BankCreditClassification
            val resolvedCreditLabel: ReconciliationEvidenceLabel?
            val countedAsWork: Boolean

            when {
                isMatched -> {
                    resolvedClassification = BankCreditClassification.MATCHED
                    resolvedCreditLabel = ReconciliationEvidenceLabel.SOURCE_MATCHED
                    countedAsWork = true
                }
                credit.currentClassification == BankCreditClassification.PERSONAL_TRANSFER -> {
                    resolvedClassification = BankCreditClassification.PERSONAL_TRANSFER
                    resolvedCreditLabel = null
                    countedAsWork = false
                }
                credit.currentClassification == BankCreditClassification.NON_WORK_IGNORED -> {
                    resolvedClassification = BankCreditClassification.NON_WORK_IGNORED
                    resolvedCreditLabel = null
                    countedAsWork = false
                }
                credit.currentClassification == BankCreditClassification.WORK_INCOME -> {
                    resolvedClassification = BankCreditClassification.WORK_INCOME
                    resolvedCreditLabel = null
                    countedAsWork = true
                }
                looksLikePersonalTransfer(credit.descriptor, input.workerDisplayName) -> {
                    resolvedClassification = BankCreditClassification.POSSIBLE_PERSONAL_TRANSFER
                    resolvedCreditLabel = null
                    countedAsWork = false
                }
                else -> {
                    resolvedClassification = BankCreditClassification.UNCLASSIFIED
                    resolvedCreditLabel = null
                    countedAsWork = false
                }
            }

            bankCreditResults[credit.id] = BankCreditReconciliationResult(
                bankCreditId = credit.id,
                matchedIncomeRecordId = if (isMatched) matchedIncId else "",
                classification = resolvedClassification,
                evidenceLabel = resolvedCreditLabel,
                countedAsWorkIncome = countedAsWork
            )

            if (resolvedClassification != credit.currentClassification) {
                auditChanges.add(
                    AuditChange(
                        actionType = "CREDIT_CLASSIFICATION_CHANGED",
                        targetCollection = "bank_credits",
                        targetId = credit.id,
                        fieldName = "classificationStatus",
                        previousValue = credit.currentClassification.code,
                        newValue = resolvedClassification.code,
                        summary = "Bank credit '${credit.descriptor.ifBlank { credit.id }}' (Rs ${credit.amountRupees}) status: ${credit.currentClassification.label} -> ${resolvedClassification.label}.".take(300)
                    )
                )
            }
            if (resolvedCreditLabel != credit.currentEvidenceLabel && resolvedCreditLabel != null) {
                auditChanges.add(
                    AuditChange(
                        actionType = "CREDIT_LABEL_CHANGED",
                        targetCollection = "bank_credits",
                        targetId = credit.id,
                        fieldName = "evidenceStatus",
                        previousValue = credit.currentEvidenceLabel?.code.orEmpty(),
                        newValue = resolvedCreditLabel.code,
                        summary = "Bank credit '${credit.descriptor.ifBlank { credit.id }}' labeled ${resolvedCreditLabel.displayLabel} via strong match.".take(300)
                    )
                )
            }
        }

        // --- Rule 7: Client confirmations update Relationship status (not income evidence label) ---
        val relationshipResults = linkedMapOf<String, RelationshipReconciliationResult>()
        val confirmationsByParty = input.confirmations.groupBy { it.partyId }
        for (rel in input.relationships) {
            val partyConfs = confirmationsByParty[rel.partyId].orEmpty()
            val resolvedRelStatus = when {
                partyConfs.any { it.triggersDispute || it.status == ConfirmationResponseStatus.DISPUTED } ->
                    RelationshipOutcomeStatus.DISPUTED
                partyConfs.any { it.isPublicCounterparty && it.workDelivered == true } ->
                    RelationshipOutcomeStatus.COUNTERPARTY_ACKNOWLEDGED
                partyConfs.any { it.status == ConfirmationResponseStatus.CONFIRMED_SIMULATED } ->
                    RelationshipOutcomeStatus.CONFIRMED_SIMULATED
                else -> rel.currentRelationshipStatus
            }
            relationshipResults[rel.id] = RelationshipReconciliationResult(
                relationshipId = rel.id,
                partyId = rel.partyId,
                relationshipStatus = resolvedRelStatus
            )
            if (resolvedRelStatus != rel.currentRelationshipStatus) {
                auditChanges.add(
                    AuditChange(
                        actionType = "RELATIONSHIP_STATUS_CHANGED",
                        targetCollection = "relationships",
                        targetId = rel.id,
                        fieldName = "relationshipStatus",
                        previousValue = rel.currentRelationshipStatus.code,
                        newValue = resolvedRelStatus.code,
                        summary = "Relationship status updated: ${rel.currentRelationshipStatus.label} -> ${resolvedRelStatus.label} (evidence label unchanged).".take(300)
                    )
                )
            }
        }

        return ReconciliationResult(
            duplicateGroups = duplicateGrouping.groups,
            incomeResults = incomeResults,
            bankCreditResults = bankCreditResults,
            relationshipResults = relationshipResults,
            newStrongMatches = newStrongMatches,
            auditChanges = auditChanges
        )
    }

    // --- Rule 1 Helpers: Duplicate Grouping ---

    private data class DuplicateGroupingOutput(
        val groups: List<DuplicateGroup>,
        val primaryByRecordId: Map<String, String>,
        val groupKeyByRecordId: Map<String, String>,
        val membersByPrimaryId: Map<String, List<String>>
    )

    private fun groupDuplicates(
        incomes: List<ReconciliationIncomeInput>,
        confirmedIncomeIds: Set<String>
    ): DuplicateGroupingOutput {
        if (incomes.isEmpty()) {
            return DuplicateGroupingOutput(emptyList(), emptyMap(), emptyMap(), emptyMap())
        }

        val parent = mutableMapOf<String, String>()
        incomes.forEach { parent[it.id] = it.id }

        fun find(x: String): String {
            var curr = x
            while (parent[curr] != curr) {
                curr = parent[curr]!!
            }
            var walk = x
            while (parent[walk] != curr) {
                val next = parent[walk]!!
                parent[walk] = curr
                walk = next
            }
            return curr
        }

        fun union(a: String, b: String) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        val reasonByPair = mutableMapOf<Pair<String, String>, String>()
        val eventKeyById = incomes.associate { it.id to buildNormalizedEventKey(it) }

        // Group by identical fileHash
        val byFileHash = incomes
            .filter { it.fileHash.isNotBlank() }
            .groupBy { it.fileHash.trim().lowercase() }
        byFileHash.values.filter { it.size > 1 }.forEach { list ->
            val first = list.first()
            list.drop(1).forEach { other ->
                union(first.id, other.id)
                reasonByPair[first.id to other.id] = "same_file_hash"
            }
        }

        // Group by identical normalized event key (payer + period + net + reference)
        val byEventKey = incomes
            .filter { eventKeyById[it.id].orEmpty().isNotBlank() }
            .groupBy { eventKeyById[it.id]!! }
        byEventKey.values.filter { it.size > 1 }.forEach { list ->
            val first = list.first()
            list.drop(1).forEach { other ->
                union(first.id, other.id)
                reasonByPair.putIfAbsent(first.id to other.id, "same_event_key")
            }
        }

        val clusters = incomes.groupBy { find(it.id) }.values
        val groups = mutableListOf<DuplicateGroup>()
        val primaryByRecordId = mutableMapOf<String, String>()
        val groupKeyByRecordId = mutableMapOf<String, String>()
        val membersByPrimaryId = mutableMapOf<String, List<String>>()

        for (cluster in clusters) {
            // Deterministic primary selection: confirmed bank match > simulatedApi > hasDocument > earlier ID
            val sorted = cluster.sortedWith(
                compareByDescending<ReconciliationIncomeInput> { it.id in confirmedIncomeIds }
                    .thenByDescending { it.currentEvidenceLabel == ReconciliationEvidenceLabel.SOURCE_MATCHED }
                    .thenByDescending { it.simulatedApi }
                    .thenByDescending { it.hasDocument }
                    .thenBy { it.currentGroupedIntoRecordId.isNotBlank() }
                    .thenBy { it.id }
            )
            val primary = sorted.first()
            val groupKey = eventKeyById[primary.id]
                ?.takeIf { it.isNotBlank() }
                ?: primary.fileHash.trim().lowercase().takeIf { it.isNotBlank() }
                ?: primary.id

            membersByPrimaryId[primary.id] = sorted.map { it.id }
            sorted.forEach { member ->
                primaryByRecordId[member.id] = primary.id
                groupKeyByRecordId[member.id] = groupKey
            }
            if (sorted.size > 1) {
                val hasHashReason = sorted.any { a ->
                    sorted.any { b -> a.id != b.id && a.fileHash.isNotBlank() && a.fileHash.equals(b.fileHash, ignoreCase = true) }
                }
                groups.add(
                    DuplicateGroup(
                        groupKey = groupKey,
                        primaryRecordId = primary.id,
                        memberRecordIds = sorted.map { it.id },
                        matchReason = if (hasHashReason) "same_file_hash" else "same_event_key"
                    )
                )
            }
        }

        return DuplicateGroupingOutput(groups, primaryByRecordId, groupKeyByRecordId, membersByPrimaryId)
    }

    fun buildNormalizedEventKey(inc: ReconciliationIncomeInput): String {
        val payer = normalizeKeyPart(inc.payerName.ifBlank { inc.partyName })
        val net = when {
            inc.netAmountRupees > 0L -> inc.netAmountRupees
            inc.invoiceAmountRupees > 0L -> inc.invoiceAmountRupees
            else -> inc.amountRupees
        }
        val period = normalizeKeyPart(
            when {
                inc.periodStart.isNotBlank() || inc.periodEnd.isNotBlank() -> "${inc.periodStart}_${inc.periodEnd}"
                inc.periodLabel.isNotBlank() -> inc.periodLabel.replace(Regex("\\([^)]*\\)"), "")
                else -> inc.earnedDate
            }
        )
        val ref = normalizeKeyPart(inc.referenceCode.ifBlank { inc.invoiceNumber })
        if (payer.isBlank() || net <= 0L) return ""
        if (ref.isBlank() && period.isBlank()) return ""
        return "$payer|$period|$net|$ref"
    }

    // --- Rule 2 Helper: Expected Receipt Amount ---

    fun computeExpectedAmount(
        inc: ReconciliationIncomeInput,
        confirmation: ReconciliationConfirmationInput?
    ): Long {
        return when (inc.recordKind) {
            ReconciliationRecordKind.PLATFORM -> {
                if (inc.netAmountRupees > 0L) inc.netAmountRupees else inc.amountRupees
            }
            ReconciliationRecordKind.CLIENT_INVOICE -> {
                val clientCorrected = confirmation?.confirmedAmountRupees
                when {
                    clientCorrected != null && clientCorrected > 0L -> clientCorrected
                    inc.invoiceAmountRupees > 0L -> inc.invoiceAmountRupees
                    else -> inc.amountRupees
                }
            }
            ReconciliationRecordKind.MANUAL_OTHER -> inc.amountRupees
        }
    }

    // --- Rule 6 Helper: Conflicts ---

    fun detectConflict(
        inc: ReconciliationIncomeInput,
        confirmation: ReconciliationConfirmationInput?
    ): String {
        // 1. gross - deductions != net beyond Rs 1
        if (inc.grossAmountRupees > 0L && inc.netAmountRupees > 0L) {
            val expectedNet = inc.grossAmountRupees - inc.deductionsRupees
            val diff = abs(expectedNet - inc.netAmountRupees)
            if (diff > AMOUNT_TOLERANCE_RUPEES) {
                return "Gross (Rs ${inc.grossAmountRupees}) − deductions (Rs ${inc.deductionsRupees}) = Rs $expectedNet, differs from net Rs ${inc.netAmountRupees} by Rs $diff."
            }
        }
        // 2. Client confirmed amount != invoice amount beyond Rs 1 (unless worker already accepted correction)
        if (confirmation?.correctionAcceptedByWorker != true) {
            val clientAmt = confirmation?.confirmedAmountRupees
            if (clientAmt != null && clientAmt > 0L) {
                val invoiceAmt = if (inc.invoiceAmountRupees > 0L) inc.invoiceAmountRupees else inc.amountRupees
                if (invoiceAmt > 0L && abs(clientAmt - invoiceAmt) > AMOUNT_TOLERANCE_RUPEES) {
                    return confirmation.disputeReason.ifBlank {
                        "Client confirmed Rs $clientAmt vs invoiced Rs $invoiceAmt."
                    }
                }
            }
            // 3. Explicit client dispute or any "No" answer from counterparty confirmation
            if (confirmation?.triggersDispute == true || confirmation?.status == ConfirmationResponseStatus.DISPUTED) {
                return confirmation.disputeReason.ifBlank { "Client flagged a dispute on this payment." }
            }
        }
        if (inc.manuallyDisputed) {
            return inc.currentConflictReason.ifBlank { "Flagged as disputed by worker." }
        }
        return ""
    }

    // --- Rule 7 Helper: Strict Evidence Label Precedence ---

    fun resolveIncomeEvidenceLabel(
        hasConflict: Boolean,
        simulatedApi: Boolean,
        hasStrongOrConfirmedBankMatch: Boolean,
        hasDocument: Boolean,
        workerConfirmed: Boolean
    ): ReconciliationEvidenceLabel {
        if (hasConflict) return ReconciliationEvidenceLabel.DISPUTED
        if (simulatedApi) return ReconciliationEvidenceLabel.SOURCE_VERIFIED_SIMULATED
        if (hasStrongOrConfirmedBankMatch) return ReconciliationEvidenceLabel.SOURCE_MATCHED
        if (hasDocument && workerConfirmed) return ReconciliationEvidenceLabel.DOCUMENT_EXTRACTED
        return ReconciliationEvidenceLabel.SELF_DECLARED
    }

    private fun resolvePaymentStatus(
        inc: ReconciliationIncomeInput,
        confirmation: ReconciliationConfirmationInput?
    ): Pair<PaymentStatus, String> {
        if (confirmation != null) {
            return when (confirmation.paymentSent) {
                true -> {
                    val status = if (confirmation.isPublicCounterparty) {
                        PaymentStatus.PAYER_CONFIRMED
                    } else {
                        PaymentStatus.PAYMENT_SENT
                    }
                    status to confirmation.paymentDate.trim()
                }
                false -> PaymentStatus.PAYMENT_PENDING to ""
                null -> inc.currentPaymentStatus to inc.currentPaymentDate
            }
        }
        return inc.currentPaymentStatus to inc.currentPaymentDate
    }

    // --- Rule 3 Helpers: Candidate Matching & Name Overlap Ranking ---

    private fun evaluateCandidate(
        inc: ReconciliationIncomeInput,
        expectedAmount: Long,
        confirmation: ReconciliationConfirmationInput?,
        credit: ReconciliationBankCreditInput
    ): CreditCandidate? {
        val delta = credit.amountRupees - expectedAmount
        if (abs(delta) > AMOUNT_TOLERANCE_RUPEES) return null

        val creditDate = parseIsoDate(credit.creditDate) ?: return null

        val signedGapDays: Long
        val windowRule: String

        when (inc.recordKind) {
            ReconciliationRecordKind.PLATFORM -> {
                val payoutDate = parseIsoDate(inc.earnedDate) ?: return null
                signedGapDays = ChronoUnit.DAYS.between(payoutDate, creditDate)
                if (abs(signedGapDays) > PLATFORM_WINDOW_DAYS) return null
                windowRule = "Platform payout within ±${PLATFORM_WINDOW_DAYS}d (${abs(signedGapDays)}d apart)"
            }
            ReconciliationRecordKind.CLIENT_INVOICE -> {
                val clientPayDate = confirmation?.paymentDate?.let(::parseIsoDate)
                if (clientPayDate != null) {
                    signedGapDays = ChronoUnit.DAYS.between(clientPayDate, creditDate)
                    if (abs(signedGapDays) > CLIENT_PAYMENT_WINDOW_DAYS) return null
                    windowRule = "Client payment within ±${CLIENT_PAYMENT_WINDOW_DAYS}d of stated date (${abs(signedGapDays)}d apart)"
                } else {
                    val invoiceDate = parseIsoDate(inc.earnedDate) ?: return null
                    signedGapDays = ChronoUnit.DAYS.between(invoiceDate, creditDate)
                    if (signedGapDays < 0L || signedGapDays > CLIENT_INVOICE_MAX_DAYS) return null
                    windowRule = "Client payment ${signedGapDays}d after invoice (0–${CLIENT_INVOICE_MAX_DAYS}d window)"
                }
            }
            ReconciliationRecordKind.MANUAL_OTHER -> {
                val recDate = parseIsoDate(inc.earnedDate) ?: return null
                signedGapDays = ChronoUnit.DAYS.between(recDate, creditDate)
                if (abs(signedGapDays) > PLATFORM_WINDOW_DAYS) return null
                windowRule = "Manual record within ±${PLATFORM_WINDOW_DAYS}d (${abs(signedGapDays)}d apart)"
            }
        }

        val overlapScore = computeNameOverlapScore(
            descriptor = "${credit.descriptor} ${credit.utrReference}",
            candidateNames = listOf(inc.payerName, inc.partyName, inc.workGiverName)
        )
        val refMatched = hasReferenceOverlap(
            recordRef = inc.referenceCode.ifBlank { inc.invoiceNumber },
            descriptor = credit.descriptor,
            utr = credit.utrReference
        )

        val overlapLabel = when {
            overlapScore > 0 && refMatched -> "name + ref match"
            overlapScore > 0 -> "name overlap ($overlapScore)"
            refMatched -> "reference match"
            else -> "amount & date only"
        }

        return CreditCandidate(
            incomeRecordId = inc.id,
            bankCreditId = credit.id,
            amountDeltaRupees = delta,
            dateGapDays = abs(signedGapDays),
            nameOverlapScore = overlapScore,
            referenceMatched = refMatched,
            matchRule = "$windowRule · $overlapLabel"
        )
    }

    private fun isStrongCandidate(top: CreditCandidate, second: CreditCandidate?): Boolean {
        val topHasStrongSignal = top.nameOverlapScore > 0 || top.referenceMatched
        if (!topHasStrongSignal) return false
        if (second == null) return true
        // Multiple candidates exist: only STRONG if top strictly beats second by name/ref signal while second has none
        val secondHasSignal = second.nameOverlapScore > 0 || second.referenceMatched
        return !secondHasSignal && (top.nameOverlapScore >= 1 || top.referenceMatched)
    }

    fun computeNameOverlapScore(descriptor: String, candidateNames: List<String>): Int {
        val descUpper = descriptor.uppercase()
        val descCompact = normalizeKeyPart(descriptor)
        val descTokens = tokenizeName(descriptor)
        if (descTokens.isEmpty() && descCompact.isBlank()) return 0

        val nameTokens = candidateNames
            .flatMap { tokenizeName(it) }
            .distinct()
        if (nameTokens.isEmpty()) return 0

        var score = 0
        for (token in nameTokens) {
            if (token in descTokens || descUpper.contains(token) || (token.length >= 4 && descCompact.contains(token.lowercase()))) {
                score++
            }
        }
        return score
    }

    private fun hasReferenceOverlap(recordRef: String, descriptor: String, utr: String): Boolean {
        val cleanRef = normalizeKeyPart(recordRef)
        if (cleanRef.length < 4) return false
        val hayCompact = normalizeKeyPart("$descriptor $utr")
        return hayCompact.contains(cleanRef)
    }

    // --- Rule 5 Helper: Personal Transfer Heuristic ---

    fun looksLikePersonalTransfer(descriptor: String, workerDisplayName: String): Boolean {
        val clean = descriptor.trim()
        if (clean.isBlank()) return false
        val upper = clean.uppercase()
        val tokens = upper.split(Regex("[^A-Z0-9]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false

        // If any business, platform, or invoice marker is present, treat as unclassified (not personal)
        if (tokens.any { it in COMMERCIAL_MARKERS }) return false
        if (tokens.any { it.startsWith("INV") && it.length >= 4 && it.any(Char::isDigit) }) return false

        // Extract purely alphabetic name-like tokens (ignoring rail prefixes like UPI/IMPS/NEFT)
        val nameTokens = tokens.filter { tok ->
            tok.length >= 2 &&
                tok.all(Char::isLetter) &&
                tok !in RAIL_PREFIXES &&
                tok !in GENERIC_STOP_WORDS
        }

        // Check shared family surname with worker (e.g. "UPI MEENA RAO" for worker "Asha Rao")
        val workerSurname = workerDisplayName.trim()
            .uppercase()
            .split(Regex("[^A-Z]+"))
            .filter { it.length >= 3 }
            .lastOrNull()
        if (workerSurname != null && workerSurname in nameTokens) {
            return true
        }

        // Typical personal transfer: starts with UPI/P2P/IMPS and has 2-3 purely alphabetic person name tokens
        val startsWithPersonalRail = tokens.firstOrNull() in PERSONAL_RAILS
        return startsWithPersonalRail && nameTokens.size in 2..3
    }

    private fun normalizeKeyPart(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun tokenizeName(raw: String): List<String> =
        raw.uppercase()
            .split(Regex("[^A-Z0-9]+"))
            .filter { it.length >= 3 && it !in GENERIC_STOP_WORDS && it !in RAIL_PREFIXES }

    private fun parseIsoDate(raw: String): LocalDate? {
        return try {
            val cleaned = raw.trim().take(10)
            if (cleaned.length != 10) null else LocalDate.parse(cleaned)
        } catch (_: Exception) {
            null
        }
    }

    private val RAIL_PREFIXES = setOf("UPI", "IMPS", "NEFT", "RTGS", "P2P", "NACH", "ACH", "ATM", "POS", "BANK")
    private val PERSONAL_RAILS = setOf("UPI", "P2P", "IMPS")
    private val GENERIC_STOP_WORDS = setOf(
        "PAYOUT", "PAYMENT", "TRANSFER", "SETTLEMENT", "CREDIT", "FROM", "THE", "AND", "FOR",
        "INDIA", "PVT", "LTD", "LIMITED", "PRIVATE", "WEEKLY", "MONTHLY", "DEMO", "CASH"
    )
    private val COMMERCIAL_MARKERS = setOf(
        "PAYOUT", "PAYROLL", "SALARY", "SETTLEMENT", "STUDIO", "STORES", "STORE", "TRADERS",
        "ENTERPRISES", "SERVICES", "SOLUTIONS", "TECH", "TECHNOLOGIES", "DELIVERY", "LOGISTICS",
        "PLATFORM", "PARTNER", "INVOICE", "DESIGN", "PVT", "LTD", "LLP", "CORP", "INC",
        "AGENCY", "CONSULTING", "RIDECO", "METRODROP", "MERCHANT", "SHOP", "CATERERS"
    )
}
