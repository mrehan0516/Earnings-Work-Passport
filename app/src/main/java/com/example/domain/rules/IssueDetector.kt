package com.example.domain.rules

import com.example.model.BankCredit
import com.example.model.ConfirmationRequest
import com.example.model.EvidenceStatus
import com.example.model.IncomeDocument
import com.example.model.IncomeRecord
import com.example.model.Reconciliation
import com.example.model.ReconciliationDecision
import com.example.model.ReviewStatus
import com.example.reconciliation.BankCreditClassification
import com.example.reconciliation.MatchOutcome
import com.example.reconciliation.ReconciliationResult
import kotlin.math.abs

enum class IssueType(
    val title: String,
    val isActionable: Boolean
) {
    PENDING_AI_REVIEW("Waiting for your confirmation", isActionable = true),
    NUMBERS_DO_NOT_ADD_UP("Numbers do not add up", isActionable = true),
    DISPUTED_RECORD("Disputed mismatch", isActionable = true),
    AMOUNT_MISMATCH("Bank credit mismatch", isActionable = true),
    SUGGESTED_BANK_MATCH("Possible match — choose", isActionable = true),
    POTENTIAL_DUPLICATE("Duplicate copy", isActionable = true),
    UNMATCHED_BANK_CREDIT("Unmatched / unclassified credit", isActionable = true),
    UNMATCHED_INCOME("No matching bank credit yet", isActionable = true),
    SUCCESSFUL_MATCH("Successful match", isActionable = false)
}

enum class IssuesFilter(val label: String) {
    ALL("All"),
    NEEDS_ACTION("Needs Action"),
    DISPUTED("Disputed"),
    MATCHED("Matched")
}

data class DisputeVersion(
    val title: String,
    val amountDisplay: String,
    val details: List<String>
)

data class DisputeComparison(
    val leftVersion: DisputeVersion,
    val rightVersion: DisputeVersion,
    val summaryNote: String
)

data class SuccessfulMatchDetail(
    val incomeRecord: IncomeRecord,
    val bankCredit: BankCredit,
    val matchRule: String,
    val dateGapDays: Long,
    val linkedFileCount: Int
)

data class PassportIssue(
    val id: String,
    val type: IssueType,
    val headline: String,
    val explanation: String,
    val amountRupees: Long,
    val evidenceStatus: EvidenceStatus,
    val incomeRecordId: String? = null,
    val primaryRecordId: String? = null,
    val documentId: String? = null,
    val bankCreditId: String? = null,
    val candidate: MatchCandidate? = null,
    val multipleCandidates: List<MatchCandidate> = emptyList(),
    val creditClassification: BankCreditClassification? = null,
    val disputeComparison: DisputeComparison? = null,
    val successfulMatch: SuccessfulMatchDetail? = null
) {
    fun matchesFilter(filter: IssuesFilter): Boolean = when (filter) {
        IssuesFilter.ALL -> true
        IssuesFilter.NEEDS_ACTION -> type.isActionable
        IssuesFilter.DISPUTED -> type == IssueType.DISPUTED_RECORD || type == IssueType.NUMBERS_DO_NOT_ADD_UP
        IssuesFilter.MATCHED -> type == IssueType.SUCCESSFUL_MATCH
    }
}

/**
 * Pure deterministic issue detector scanning records, documents, bank credits, confirmations,
 * reconciliations, and [ReconciliationResult] outputs for all Issues listings:
 * - Pending AI review drafts
 * - "Numbers do not add up" (gross - deductions != net > Rs 1)
 * - Mismatches & Disputes (with side-by-side version comparison; never deleted)
 * - Possible / multiple bank matches needing a worker choice
 * - Duplicates (excluding non-destructively archived duplicateIgnored copies)
 * - Unmatched / unclassified bank credits
 * - Unmatched income entries
 * - Successful bank-matched pairs
 */
object IssueDetector {

    fun detectIssues(
        incomeRecords: List<IncomeRecord>,
        documents: List<IncomeDocument>,
        bankCredits: List<BankCredit>,
        reconciliations: List<Reconciliation>,
        candidates: List<MatchCandidate>,
        reconciliationResult: ReconciliationResult? = null,
        confirmationRequests: List<ConfirmationRequest> = emptyList(),
        includeSuccessfulMatches: Boolean = false
    ): List<PassportIssue> {
        val issues = mutableListOf<PassportIssue>()
        val creditsById = bankCredits.associateBy { it.id }
        val recordsById = incomeRecords.associateBy { it.id }
        val confirmationByRecordId = confirmationRequests
            .groupBy { it.incomeRecordId }
            .mapValues { (_, list) -> list.first() }

        val confirmedReconsByRecordId = reconciliations
            .filter { ReconciliationDecision.fromCode(it.decisionStatus) == ReconciliationDecision.CONFIRMED }
            .associateBy { it.incomeRecordId }
        val confirmedRecordIds = confirmedReconsByRecordId.keys
        val confirmedCreditIds = reconciliations
            .filter { ReconciliationDecision.fromCode(it.decisionStatus) == ReconciliationDecision.CONFIRMED }
            .map { it.bankCreditId }
            .toSet()

        fun findMatchedCredit(rec: IncomeRecord): BankCredit? {
            val fromResultId = reconciliationResult?.incomeResults?.get(rec.id)?.matchedBankCreditId.orEmpty()
            if (fromResultId.isNotBlank()) creditsById[fromResultId]?.let { return it }
            val fromReconId = confirmedReconsByRecordId[rec.id]?.bankCreditId.orEmpty()
            if (fromReconId.isNotBlank()) creditsById[fromReconId]?.let { return it }
            return bankCredits.firstOrNull { it.matchedIncomeRecordId == rec.id }
        }

        // 1. Documents read by AI still waiting for mandatory worker confirmation
        documents
            .filter { ReviewStatus.fromCode(it.reviewStatus) == ReviewStatus.PENDING_REVIEW }
            .forEach { doc ->
                issues.add(
                    PassportIssue(
                        id = "pending_doc_${doc.id}",
                        type = IssueType.PENDING_AI_REVIEW,
                        headline = doc.title.ifBlank { "Uploaded document" },
                        explanation = "AI read ${RupeeFormatter.format(doc.extractedAmountRupees)} from ${doc.extractedPartyName.ifBlank { "this document" }}. Review and confirm every value before adding it to your Passport.",
                        amountRupees = doc.extractedAmountRupees,
                        evidenceStatus = EvidenceStatus.DOCUMENT_EXTRACTED,
                        documentId = doc.id
                    )
                )
            }

        // 2. Disputed records (split clearly into "Numbers do not add up" vs. client/bank/worker mismatches)
        //    Every disputed item includes side-by-side versions and is NEVER deleted.
        incomeRecords
            .filter { !it.isSecondaryDuplicate() && it.resolvedEvidence() == EvidenceStatus.DISPUTED }
            .forEach { record ->
                val isMathMismatch = record.grossAmountRupees > 0L &&
                    abs((record.grossAmountRupees - record.deductionsRupees) - record.netAmountRupees) > 1L

                val confirmation = confirmationByRecordId[record.id]
                val matchedCredit = findMatchedCredit(record)

                val reason = record.conflictReason
                    .ifBlank { reconciliationResult?.incomeResults?.get(record.id)?.conflictReason.orEmpty() }
                    .ifBlank {
                        if (isMathMismatch) {
                            val expectedNet = record.grossAmountRupees - record.deductionsRupees
                            "Gross (${RupeeFormatter.format(record.grossAmountRupees)}) minus deductions (${RupeeFormatter.format(record.deductionsRupees)}) equals ${RupeeFormatter.format(expectedNet)}, not ${RupeeFormatter.format(record.netAmountRupees)}."
                        } else {
                            record.notes.ifBlank { "This entry is marked Disputed and kept out of received totals until resolved." }
                        }
                    }

                val comparison = buildDisputeComparison(
                    record = record,
                    isMathMismatch = isMathMismatch,
                    confirmation = confirmation,
                    matchedCredit = matchedCredit,
                    reason = reason
                )

                val issueType = if (isMathMismatch) {
                    IssueType.NUMBERS_DO_NOT_ADD_UP
                } else {
                    IssueType.DISPUTED_RECORD
                }

                val headline = if (isMathMismatch) {
                    "${record.partyName} · Numbers do not add up"
                } else {
                    "${record.partyName} · ${record.periodLabel.ifBlank { record.earnedDate }}"
                }

                issues.add(
                    PassportIssue(
                        id = if (isMathMismatch) "math_${record.id}" else "disputed_${record.id}",
                        type = issueType,
                        headline = headline,
                        explanation = reason,
                        amountRupees = record.amountRupees,
                        evidenceStatus = EvidenceStatus.DISPUTED,
                        incomeRecordId = record.id,
                        disputeComparison = comparison
                    )
                )
            }

        // 3. POSSIBLE and MULTIPLE bank credit matches (STRONG matches are auto-applied by ReconciliationModule)
        val candidatesByRecord = candidates
            .filter { it.outcome != MatchOutcome.STRONG }
            .groupBy { it.incomeRecord.id }
        val candidateRecordIds = candidatesByRecord.keys
        val candidateCreditIds = candidates
            .filter { it.outcome != MatchOutcome.STRONG }
            .map { it.bankCredit.id }
            .toSet()

        candidatesByRecord.forEach { (recordId, recordCandidates) ->
            if (recordCandidates.isEmpty()) return@forEach
            val first = recordCandidates.first()
            val isMultiple = recordCandidates.size > 1 || first.outcome == MatchOutcome.MULTIPLE
            if (isMultiple) {
                issues.add(
                    PassportIssue(
                        id = "match_multi_$recordId",
                        type = IssueType.SUGGESTED_BANK_MATCH,
                        headline = "Multiple bank credits could match ${first.incomeRecord.partyName}",
                        explanation = "${recordCandidates.size} bank credits match ${RupeeFormatter.format(first.incomeRecord.amountRupees)} within the payout date window. Choose the right credit to accept the match.",
                        amountRupees = first.incomeRecord.amountRupees,
                        evidenceStatus = first.incomeRecord.resolvedEvidence(),
                        incomeRecordId = recordId,
                        bankCreditId = first.bankCredit.id,
                        candidate = first,
                        multipleCandidates = recordCandidates
                    )
                )
            } else {
                val isMismatch = first.amountDeltaRupees != 0L
                val creditDateShort = PassportLedgerBuilder.formatShortDayMonth(first.bankCredit.creditDate)
                    .ifBlank { first.bankCredit.creditDate }
                issues.add(
                    PassportIssue(
                        id = "match_${first.incomeRecord.id}_${first.bankCredit.id}",
                        type = if (isMismatch) IssueType.AMOUNT_MISMATCH else IssueType.SUGGESTED_BANK_MATCH,
                        headline = "${first.incomeRecord.partyName} ↔ ${first.bankCredit.narration.ifBlank { first.bankCredit.bankLabel }}",
                        explanation = if (isMismatch) {
                            "Record shows ${RupeeFormatter.format(first.incomeRecord.amountRupees)}, while the bank credit on $creditDateShort is ${RupeeFormatter.format(first.bankCredit.amountRupees)} (${RupeeFormatter.formatSignedDelta(first.amountDeltaRupees)} difference). ${first.matchRule}."
                        } else {
                            "Possible match on $creditDateShort (${first.matchRule}). Accept to label both records Source-matched and count once."
                        },
                        amountRupees = first.incomeRecord.amountRupees,
                        evidenceStatus = first.incomeRecord.resolvedEvidence(),
                        incomeRecordId = first.incomeRecord.id,
                        bankCreditId = first.bankCredit.id,
                        candidate = first,
                        multipleCandidates = listOf(first)
                    )
                )
            }
        }

        // 4. Grouped duplicate copies (Rule 1: counted once; dismissed when duplicateIgnored == true)
        val duplicateTargetIds = mutableSetOf<String>()

        val secondaryGrouped = incomeRecords.filter { rec ->
            rec.isSecondaryDuplicate() ||
                reconciliationResult?.incomeResults?.get(rec.id)?.groupedIntoRecordId?.isNotBlank() == true
        }
        secondaryGrouped.forEach { sec ->
            duplicateTargetIds.add(sec.id)
            if (sec.duplicateIgnored) return@forEach
            val primaryId = sec.groupedIntoRecordId.ifBlank {
                reconciliationResult?.incomeResults?.get(sec.id)?.groupedIntoRecordId.orEmpty()
            }
            val primary = recordsById[primaryId]
            val refHint = sec.referenceCode.takeIf { it.isNotBlank() }?.let { " (ref $it)" }.orEmpty()
            issues.add(
                PassportIssue(
                    id = "dup_${sec.id}",
                    type = IssueType.POTENTIAL_DUPLICATE,
                    headline = "Duplicate copy of ${sec.partyName} (${RupeeFormatter.format(sec.amountRupees)})",
                    explanation = "This second representation matches ${primary?.periodLabel?.ifBlank { primary.earnedDate } ?: "an existing entry"}$refHint. Both copies are grouped as one payment and counted once. Ignore this alert to archive it without deleting the file record.",
                    amountRupees = sec.amountRupees,
                    evidenceStatus = sec.resolvedEvidence(),
                    incomeRecordId = sec.id,
                    primaryRecordId = primaryId.ifBlank { null }
                )
            )
        }

        // Fallback duplicate detection when groupedIntoRecordId was not pre-populated
        if (reconciliationResult == null && secondaryGrouped.isEmpty()) {
            val flaggedPairs = mutableSetOf<String>()
            for (i in incomeRecords.indices) {
                for (j in i + 1 until incomeRecords.size) {
                    val a = incomeRecords[i]
                    val b = incomeRecords[j]
                    if (a.partyName.equals(b.partyName, ignoreCase = true) &&
                        a.amountRupees == b.amountRupees &&
                        a.amountRupees > 0L &&
                        (RupeeFormatter.daysBetween(a.earnedDate, b.earnedDate) ?: 99L) <= 1L
                    ) {
                        val pairKey = listOf(a.id, b.id).sorted().joinToString("_")
                        if (flaggedPairs.add(pairKey)) {
                            val aConfirmed = a.id in confirmedRecordIds
                            val bConfirmed = b.id in confirmedRecordIds
                            val duplicateTarget = when {
                                aConfirmed && !bConfirmed -> b
                                bConfirmed && !aConfirmed -> a
                                b.resolvedEvidence().sortRank >= a.resolvedEvidence().sortRank -> b
                                else -> a
                            }
                            duplicateTargetIds.add(duplicateTarget.id)
                            if (duplicateTarget.duplicateIgnored) continue
                            val primaryTarget = if (duplicateTarget.id == a.id) b else a
                            val refHint = duplicateTarget.referenceCode.takeIf { it.isNotBlank() }?.let { " (ref $it)" }.orEmpty()
                            issues.add(
                                PassportIssue(
                                    id = "dup_$pairKey",
                                    type = IssueType.POTENTIAL_DUPLICATE,
                                    headline = "Two ${RupeeFormatter.format(a.amountRupees)} entries for ${a.partyName}",
                                    explanation = "Found two representations of ${a.partyName} ${RupeeFormatter.format(a.amountRupees)} on ${a.earnedDate}$refHint. Grouped so this payment is counted once.",
                                    amountRupees = a.amountRupees,
                                    evidenceStatus = duplicateTarget.resolvedEvidence(),
                                    incomeRecordId = duplicateTarget.id,
                                    primaryRecordId = primaryTarget.id
                                )
                            )
                        }
                    }
                }
            }
        }

        // 5. Unmatched / unclassified bank credits (Rule 5: never counted as work income until worker classifies)
        bankCredits
            .filter { credit ->
                val cls = reconciliationResult?.bankCreditResults?.get(credit.id)?.classification
                    ?: BankCreditClassification.fromCode(credit.classificationStatus)
                val needsClassification = cls == BankCreditClassification.UNCLASSIFIED ||
                    cls == BankCreditClassification.POSSIBLE_PERSONAL_TRANSFER
                credit.matchedIncomeRecordId.isBlank() &&
                    credit.id !in confirmedCreditIds &&
                    credit.id !in candidateCreditIds &&
                    needsClassification
            }
            .forEach { credit ->
                val cls = reconciliationResult?.bankCreditResults?.get(credit.id)?.classification
                    ?: BankCreditClassification.fromCode(credit.classificationStatus)
                val descriptor = credit.narration.ifBlank { credit.utrReference.ifBlank { credit.bankLabel } }
                val creditDateShort = PassportLedgerBuilder.formatShortDayMonth(credit.creditDate)
                    .ifBlank { credit.creditDate }
                val explanation = if (cls == BankCreditClassification.POSSIBLE_PERSONAL_TRANSFER) {
                    "Bank credit '$descriptor' on $creditDateShort looks like a personal or family transfer. It is excluded from work income unless you link it to a work payment below."
                } else {
                    "Unmatched bank credit '$descriptor' (${RupeeFormatter.format(credit.amountRupees)}) on $creditDateShort has no linked work record. Excluded from work income until you classify it below."
                }
                issues.add(
                    PassportIssue(
                        id = "unmatched_bc_${credit.id}",
                        type = IssueType.UNMATCHED_BANK_CREDIT,
                        headline = "$descriptor · $creditDateShort",
                        explanation = explanation,
                        amountRupees = credit.amountRupees,
                        evidenceStatus = EvidenceStatus.SELF_DECLARED,
                        bankCreditId = credit.id,
                        creditClassification = cls
                    )
                )
            }

        // 6. Unmatched primary income records (not disputed, not bank-matched, no pending candidate, not duplicate copy)
        incomeRecords
            .filter { record ->
                val status = record.resolvedEvidence()
                val isPrimary = !record.isSecondaryDuplicate() && record.id !in duplicateTargetIds
                val hasBankMatch = record.id in confirmedRecordIds || status == EvidenceStatus.SOURCE_MATCHED
                isPrimary &&
                    status != EvidenceStatus.DISPUTED &&
                    !hasBankMatch &&
                    record.id !in candidateRecordIds
            }
            .forEach { record ->
                val simNote = if (record.simulatedApi) " (Simulated API)" else ""
                issues.add(
                    PassportIssue(
                        id = "unmatched_inc_${record.id}",
                        type = IssueType.UNMATCHED_INCOME,
                        headline = "${record.partyName} · ${record.periodLabel.ifBlank { record.earnedDate }}$simNote",
                        explanation = "No matching bank credit has arrived yet for ${RupeeFormatter.format(record.amountRupees)}. Import a bank credit, ask the party to confirm, or flag it if something is wrong.",
                        amountRupees = record.amountRupees,
                        evidenceStatus = record.resolvedEvidence(),
                        incomeRecordId = record.id
                    )
                )
            }

        // 7. Successful matches (confirmed Source-matched pairs with plain-language reasons)
        if (includeSuccessfulMatches) {
            incomeRecords
                .filter { !it.isSecondaryDuplicate() && it.resolvedEvidence() == EvidenceStatus.SOURCE_MATCHED }
                .forEach { record ->
                    val credit = findMatchedCredit(record) ?: return@forEach
                    val recon = confirmedReconsByRecordId[record.id]
                    val linkedCount = reconciliationResult
                        ?.incomeResults
                        ?.get(record.id)
                        ?.linkedItemCount
                        ?.takeIf { it > 1 }
                        ?: (1 + incomeRecords.count { it.groupedIntoRecordId == record.id })

                    val creditDateShort = PassportLedgerBuilder.formatShortDayMonth(credit.creditDate)
                        .ifBlank { credit.creditDate }
                    val recordNoun = when {
                        record.grossAmountRupees > 0L -> "Platform report"
                        record.invoiceAmountRupees > 0L || record.invoiceNumber.isNotBlank() -> "Invoice"
                        else -> "Income record"
                    }
                    val creditDesc = credit.narration.ifBlank { credit.bankLabel }
                    val ruleSummary = recon?.matchRule?.takeIf { it.isNotBlank() }
                        ?: "Amount and date window matched"
                    val gapDays = recon?.dateGapDays
                        ?: (RupeeFormatter.daysBetween(record.earnedDate, credit.creditDate) ?: 0L)

                    val explanation = buildString {
                        append("Matched to a ${RupeeFormatter.format(credit.amountRupees)} bank credit on $creditDateShort ($creditDesc). ")
                        append("$recordNoun and bank credit are one payment, counted once.")
                        if (linkedCount > 1) {
                            append(" $linkedCount duplicate files for this payout are grouped together.")
                        }
                    }

                    issues.add(
                        PassportIssue(
                            id = "matched_${record.id}_${credit.id}",
                            type = IssueType.SUCCESSFUL_MATCH,
                            headline = "${record.partyName} ↔ $creditDesc",
                            explanation = explanation,
                            amountRupees = record.amountRupees,
                            evidenceStatus = EvidenceStatus.SOURCE_MATCHED,
                            incomeRecordId = record.id,
                            bankCreditId = credit.id,
                            successfulMatch = SuccessfulMatchDetail(
                                incomeRecord = record,
                                bankCredit = credit,
                                matchRule = ruleSummary,
                                dateGapDays = gapDays,
                                linkedFileCount = linkedCount
                            )
                        )
                    )
                }
        }

        return issues.sortedBy { issuePriority(it.type) }
    }

    private fun buildDisputeComparison(
        record: IncomeRecord,
        isMathMismatch: Boolean,
        confirmation: ConfirmationRequest?,
        matchedCredit: BankCredit?,
        reason: String
    ): DisputeComparison {
        val dateShort = PassportLedgerBuilder.formatShortDayMonth(record.earnedDate)
            .ifBlank { record.earnedDate }

        // Case A: "Numbers do not add up" (gross - deductions != net)
        if (isMathMismatch) {
            val expectedNet = record.grossAmountRupees - record.deductionsRupees
            val diff = abs(expectedNet - record.netAmountRupees)
            return DisputeComparison(
                leftVersion = DisputeVersion(
                    title = "Printed / Claimed Net",
                    amountDisplay = RupeeFormatter.format(record.netAmountRupees),
                    details = listOfNotNull(
                        "Gross: ${RupeeFormatter.format(record.grossAmountRupees)}",
                        "Deductions: ${RupeeFormatter.format(record.deductionsRupees)}",
                        record.referenceCode.takeIf { it.isNotBlank() }?.let { "Ref: $it" },
                        "Date: $dateShort"
                    )
                ),
                rightVersion = DisputeVersion(
                    title = "Calculated Math",
                    amountDisplay = RupeeFormatter.format(expectedNet),
                    details = listOf(
                        "Gross − Deductions = ${RupeeFormatter.format(expectedNet)}",
                        "Difference: ${RupeeFormatter.format(diff)}",
                        "Tolerance limit: Rs 1"
                    )
                ),
                summaryNote = "Both figures are preserved side by side; disputed items are never deleted."
            )
        }

        // Case B: Client confirmation conflict (public counterparty response OR legacy simulated conflict)
        val evaluatedConf = confirmation?.let { CounterpartyConfirmationRules.evaluateRequest(it) }
        val parsedConf = confirmation?.let { CounterpartyConfirmationRules.parseResponses(it.responses) }
        val clientAmt = evaluatedConf?.correctedAmountRupees ?: confirmation?.confirmedAmountRupees
        val invoiceAmt = if (record.invoiceAmountRupees > 0L) record.invoiceAmountRupees else record.amountRupees
        if (confirmation != null && (
                clientAmt != null ||
                    confirmation.status == "DISPUTED" ||
                    evaluatedConf?.triggersDispute == true
            )
        ) {
            val deliveredText = when {
                parsedConf?.workDelivered != null -> "Work delivered: ${parsedConf.workDelivered.label}"
                confirmation.workDelivered == true -> "Work delivered: Yes"
                confirmation.workDelivered == false -> "Work delivered: No"
                else -> null
            }
            val paymentText = when {
                parsedConf?.paidStatus != null -> {
                    val datePart = if (parsedConf.paidStatus == CounterpartyAnswerChoice.YES && parsedConf.paidDate.isNotBlank()) {
                        " (${PassportLedgerBuilder.formatShortDayMonth(parsedConf.paidDate).ifBlank { parsedConf.paidDate }})"
                    } else ""
                    "Paid: ${parsedConf.paidStatus.label}$datePart"
                }
                confirmation.paymentSent == true -> if (confirmation.paymentDate.isNotBlank()) {
                    "Payment sent (${PassportLedgerBuilder.formatShortDayMonth(confirmation.paymentDate).ifBlank { confirmation.paymentDate }})"
                } else "Payment sent"
                confirmation.paymentSent == false -> "Payment not yet"
                else -> "Payment status unknown"
            }
            val versionTag = if (parsedConf != null && confirmation.responses.isNotEmpty()) {
                "Claim v${parsedConf.claimVersionShown} shown"
            } else null
            return DisputeComparison(
                leftVersion = DisputeVersion(
                    title = "Worker's Version",
                    amountDisplay = RupeeFormatter.format(invoiceAmt),
                    details = listOfNotNull(
                        record.invoiceNumber.takeIf { it.isNotBlank() }?.let { "Invoice: $it" },
                        "Date: $dateShort",
                        record.periodLabel.takeIf { it.isNotBlank() },
                        versionTag
                    )
                ),
                rightVersion = DisputeVersion(
                    title = "${confirmation.partyName}'s Version",
                    amountDisplay = if (clientAmt != null && clientAmt > 0L) {
                        RupeeFormatter.format(clientAmt)
                    } else {
                        "Disputed"
                    },
                    details = listOfNotNull(
                        deliveredText,
                        paymentText,
                        if (clientAmt != null && clientAmt > 0L && clientAmt != invoiceAmt) {
                            "Gap: ${RupeeFormatter.formatSignedDelta(clientAmt - invoiceAmt)}"
                        } else null
                    )
                ),
                summaryNote = "Both versions stay visible side by side and are never deleted."
            )
        }

        // Case C: Linked bank credit mismatch or worker "This is wrong" dispute
        val rightVersion = if (matchedCredit != null && abs(matchedCredit.amountRupees - record.amountRupees) > 1L) {
            val creditDateShort = PassportLedgerBuilder.formatShortDayMonth(matchedCredit.creditDate)
                .ifBlank { matchedCredit.creditDate }
            DisputeVersion(
                title = "Bank Credit Version",
                amountDisplay = RupeeFormatter.format(matchedCredit.amountRupees),
                details = listOfNotNull(
                    matchedCredit.narration.ifBlank { matchedCredit.bankLabel },
                    "Date: $creditDateShort",
                    "Gap: ${RupeeFormatter.formatSignedDelta(matchedCredit.amountRupees - record.amountRupees)}"
                )
            )
        } else {
            DisputeVersion(
                title = "Worker's Dispute Note",
                amountDisplay = "Flagged wrong",
                details = listOf(
                    reason,
                    "Status: Disputed (overrides label)",
                    "Preserved in audit log"
                )
            )
        }

        return DisputeComparison(
            leftVersion = DisputeVersion(
                title = "Original Record",
                amountDisplay = RupeeFormatter.format(record.amountRupees),
                details = listOfNotNull(
                    "Source: ${record.partyName}",
                    "Date: $dateShort",
                    record.referenceCode.takeIf { it.isNotBlank() }?.let { "Ref: $it" },
                    record.notes.takeIf { it.isNotBlank() && it != reason }?.take(80)
                )
            ),
            rightVersion = rightVersion,
            summaryNote = "Both versions stay visible side by side and are never deleted."
        )
    }

    private fun issuePriority(type: IssueType): Int = when (type) {
        IssueType.PENDING_AI_REVIEW -> 1
        IssueType.NUMBERS_DO_NOT_ADD_UP -> 2
        IssueType.DISPUTED_RECORD -> 3
        IssueType.AMOUNT_MISMATCH -> 4
        IssueType.SUGGESTED_BANK_MATCH -> 5
        IssueType.POTENTIAL_DUPLICATE -> 6
        IssueType.UNMATCHED_BANK_CREDIT -> 7
        IssueType.UNMATCHED_INCOME -> 8
        IssueType.SUCCESSFUL_MATCH -> 9
    }
}
