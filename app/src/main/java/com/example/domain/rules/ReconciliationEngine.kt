package com.example.domain.rules

import com.example.model.BankCredit
import com.example.model.ConfirmationRequest
import com.example.model.ConfirmationStatus
import com.example.model.DocumentType
import com.example.model.EvidenceStatus
import com.example.model.IncomeDocument
import com.example.model.IncomeRecord
import com.example.model.Party
import com.example.model.PartyType
import com.example.model.Reconciliation
import com.example.model.ReconciliationDecision
import com.example.model.WorkRelationship
import com.example.reconciliation.BankCreditClassification
import com.example.reconciliation.ConfirmationResponseStatus
import com.example.reconciliation.CreditCandidate
import com.example.reconciliation.ExistingDecisionStatus
import com.example.reconciliation.MatchOutcome
import com.example.reconciliation.PaymentStatus
import com.example.reconciliation.ReconciliationBankCreditInput
import com.example.reconciliation.ReconciliationConfirmationInput
import com.example.reconciliation.ReconciliationDecisionInput
import com.example.reconciliation.ReconciliationEvidenceLabel
import com.example.reconciliation.ReconciliationIncomeInput
import com.example.reconciliation.ReconciliationInput
import com.example.reconciliation.ReconciliationModule
import com.example.reconciliation.ReconciliationRecordKind
import com.example.reconciliation.ReconciliationRelationshipInput
import com.example.reconciliation.ReconciliationResult
import com.example.reconciliation.RelationshipOutcomeStatus

data class MatchCandidate(
    val incomeRecord: IncomeRecord,
    val bankCredit: BankCredit,
    val matchRule: String,
    val amountDeltaRupees: Long,
    val dateGapDays: Long,
    val score: Int,
    val outcome: MatchOutcome = MatchOutcome.POSSIBLE
)

/**
 * Thin domain adapter bridging Firestore models to the standalone plain-code
 * [com.example.reconciliation.ReconciliationModule].
 *
 * All matching, duplicate grouping, conflict detection, credit classification, and
 * evidence-label rules live exclusively in [ReconciliationModule] (no UI, no AI).
 */
object ReconciliationEngine {

    fun reconcileWorkspace(
        workerDisplayName: String = "",
        incomeRecords: List<IncomeRecord>,
        documents: List<IncomeDocument> = emptyList(),
        parties: List<Party> = emptyList(),
        bankCredits: List<BankCredit> = emptyList(),
        confirmations: List<ConfirmationRequest> = emptyList(),
        relationships: List<WorkRelationship> = emptyList(),
        reconciliations: List<Reconciliation> = emptyList()
    ): ReconciliationResult {
        val docsById = documents.associateBy { it.id }
        val partiesById = parties.associateBy { it.id }

        val incomeInputs = incomeRecords
            .filter { it.id.isNotBlank() }
            .map { rec ->
                val doc = docsById[rec.documentId]
                val party = partiesById[rec.payerPartyId.ifBlank { rec.partyId }]
                    ?: partiesById[rec.partyId]
                val recordKind = resolveRecordKind(rec, doc, party)
                ReconciliationIncomeInput(
                    id = rec.id,
                    partyId = rec.partyId,
                    partyName = rec.partyName,
                    workGiverName = rec.workGiverPartyName,
                    payerName = rec.payerPartyName.ifBlank { rec.partyName },
                    documentId = rec.documentId,
                    fileHash = doc?.fileHash.orEmpty(),
                    recordKind = recordKind,
                    earnedDate = rec.earnedDate,
                    periodStart = rec.periodStart,
                    periodEnd = rec.periodEnd,
                    periodLabel = rec.periodLabel,
                    referenceCode = rec.referenceCode,
                    invoiceNumber = rec.invoiceNumber,
                    amountRupees = rec.amountRupees,
                    grossAmountRupees = rec.grossAmountRupees,
                    deductionsRupees = rec.deductionsRupees,
                    netAmountRupees = rec.netAmountRupees,
                    invoiceAmountRupees = rec.invoiceAmountRupees,
                    workerConfirmed = rec.workerConfirmed,
                    simulatedApi = rec.simulatedApi,
                    hasDocument = rec.documentId.isNotBlank(),
                    manuallyDisputed = rec.manuallyDisputed ||
                        (rec.resolvedEvidence() == EvidenceStatus.DISPUTED &&
                            confirmations.none {
                                it.incomeRecordId == rec.id &&
                                    CounterpartyConfirmationRules.evaluateRequest(it).triggersDispute
                            } &&
                            !(rec.grossAmountRupees > 0L && rec.netAmountRupees > 0L && kotlin.math.abs((rec.grossAmountRupees - rec.deductionsRupees) - rec.netAmountRupees) > 1L)),
                    currentEvidenceLabel = ReconciliationEvidenceLabel.fromCode(rec.evidenceStatus),
                    currentPaymentStatus = PaymentStatus.fromCode(rec.paymentStatus),
                    currentPaymentDate = rec.paymentDate,
                    currentGroupedIntoRecordId = rec.groupedIntoRecordId,
                    currentDuplicateGroupKey = rec.duplicateGroupKey,
                    currentMatchOutcome = MatchOutcome.fromCode(rec.matchOutcome),
                    currentConflictReason = rec.conflictReason
                )
            }

        val creditInputs = bankCredits
            .filter { it.id.isNotBlank() }
            .map { bc ->
                ReconciliationBankCreditInput(
                    id = bc.id,
                    creditDate = bc.creditDate,
                    amountRupees = bc.amountRupees,
                    descriptor = bc.narration,
                    utrReference = bc.utrReference,
                    matchedIncomeRecordId = bc.matchedIncomeRecordId,
                    currentClassification = BankCreditClassification.fromCode(bc.classificationStatus),
                    currentEvidenceLabel = bc.evidenceStatus.takeIf { it.isNotBlank() }
                        ?.let { ReconciliationEvidenceLabel.fromCode(it) }
                )
            }

        // Pass confirmations oldest -> newest so ReconciliationModule's lastOrNull() picks the latest request
        val confirmationInputs = confirmations.reversed().map { conf ->
            val confStatus = when (ConfirmationStatus.fromCode(conf.status)) {
                ConfirmationStatus.PENDING -> ConfirmationResponseStatus.PENDING
                ConfirmationStatus.RESPONDED -> ConfirmationResponseStatus.RESPONDED
                ConfirmationStatus.DECLINED -> ConfirmationResponseStatus.DECLINED
                ConfirmationStatus.CONFIRMED_SIMULATED -> ConfirmationResponseStatus.CONFIRMED_SIMULATED
                ConfirmationStatus.DISPUTED -> ConfirmationResponseStatus.DISPUTED
            }
            val evaluated = CounterpartyConfirmationRules.evaluateRequest(conf)
            val isPublic = conf.isPublicTokenRequest() || conf.responses.isNotEmpty()
            val workDelivered = if (isPublic) {
                when {
                    evaluated.workConfirmed -> true
                    CounterpartyConfirmationRules.parseResponses(conf.responses).workDelivered == CounterpartyAnswerChoice.NO -> false
                    else -> null
                }
            } else {
                conf.workDelivered
            }
            val paymentSent = if (isPublic) {
                when {
                    evaluated.paymentConfirmed -> true
                    evaluated.paymentNotYet -> false
                    else -> null
                }
            } else {
                conf.paymentSent
            }
            val confirmedAmount = if (isPublic) {
                evaluated.correctedAmountRupees
            } else {
                conf.confirmedAmountRupees
            }
            val paymentDate = if (isPublic) evaluated.paymentDate else conf.paymentDate
            ReconciliationConfirmationInput(
                id = conf.id,
                incomeRecordId = conf.incomeRecordId,
                partyId = conf.partyId,
                status = confStatus,
                workDelivered = workDelivered,
                confirmedAmountRupees = confirmedAmount,
                paymentSent = paymentSent,
                paymentDate = paymentDate,
                isPublicCounterparty = isPublic,
                triggersDispute = evaluated.triggersDispute,
                disputeReason = evaluated.disputeReason,
                correctionAcceptedByWorker = evaluated.workerResolution == WorkerConfirmationResolution.CORRECTION_ACCEPTED
            )
        }

        val relationshipInputs = relationships
            .filter { it.id.isNotBlank() }
            .map { rel ->
                ReconciliationRelationshipInput(
                    id = rel.id,
                    partyId = rel.partyId,
                    currentRelationshipStatus = RelationshipOutcomeStatus.fromCode(rel.relationshipStatus)
                )
            }

        val decisionInputs = reconciliations.map { r ->
            val decStatus = when (ReconciliationDecision.fromCode(r.decisionStatus)) {
                ReconciliationDecision.SUGGESTED -> ExistingDecisionStatus.SUGGESTED
                ReconciliationDecision.CONFIRMED -> ExistingDecisionStatus.CONFIRMED
                ReconciliationDecision.REJECTED -> ExistingDecisionStatus.REJECTED
            }
            ReconciliationDecisionInput(
                incomeRecordId = r.incomeRecordId,
                bankCreditId = r.bankCreditId,
                decisionStatus = decStatus
            )
        }

        return ReconciliationModule.reconcile(
            ReconciliationInput(
                workerDisplayName = workerDisplayName,
                incomes = incomeInputs,
                bankCredits = creditInputs,
                confirmations = confirmationInputs,
                relationships = relationshipInputs,
                existingDecisions = decisionInputs
            )
        )
    }

    /**
     * Convenience wrapper returning actionable MatchCandidates for POSSIBLE, MULTIPLE,
     * and newly discovered STRONG matches (powered by [ReconciliationModule]).
     */
    fun findMatchCandidates(
        incomeRecords: List<IncomeRecord>,
        bankCredits: List<BankCredit>,
        existingReconciliations: List<Reconciliation>,
        documents: List<IncomeDocument> = emptyList(),
        parties: List<Party> = emptyList(),
        confirmations: List<ConfirmationRequest> = emptyList()
    ): List<MatchCandidate> {
        val result = reconcileWorkspace(
            incomeRecords = incomeRecords,
            documents = documents,
            parties = parties,
            bankCredits = bankCredits,
            confirmations = confirmations,
            reconciliations = existingReconciliations
        )
        return extractMatchCandidates(result, incomeRecords, bankCredits, includeNewStrong = true)
    }

    fun extractMatchCandidates(
        result: ReconciliationResult,
        incomeRecords: List<IncomeRecord>,
        bankCredits: List<BankCredit>,
        includeNewStrong: Boolean = false
    ): List<MatchCandidate> {
        val recordsById = incomeRecords.associateBy { it.id }
        val creditsById = bankCredits.associateBy { it.id }
        val list = mutableListOf<MatchCandidate>()

        if (includeNewStrong) {
            result.newStrongMatches.forEach { strong ->
                val rec = recordsById[strong.incomeRecordId] ?: return@forEach
                val bc = creditsById[strong.bankCreditId] ?: return@forEach
                list.add(toDomainCandidate(rec, bc, strong.candidate, MatchOutcome.STRONG))
            }
        }

        result.incomeResults.values.forEach { incRes ->
            if (incRes.groupedIntoRecordId.isNotBlank()) return@forEach
            if (incRes.matchOutcome == MatchOutcome.POSSIBLE || incRes.matchOutcome == MatchOutcome.MULTIPLE) {
                val rec = recordsById[incRes.incomeRecordId] ?: return@forEach
                incRes.candidates.forEach { cand ->
                    val bc = creditsById[cand.bankCreditId] ?: return@forEach
                    list.add(toDomainCandidate(rec, bc, cand, incRes.matchOutcome))
                }
            }
        }
        return list
    }

    private fun toDomainCandidate(
        record: IncomeRecord,
        credit: BankCredit,
        cand: CreditCandidate,
        outcome: MatchOutcome
    ): MatchCandidate {
        val computedScore = 50 +
            (cand.nameOverlapScore * 20) +
            (if (cand.referenceMatched) 25 else 0) -
            kotlin.math.abs(cand.dateGapDays).toInt()
        return MatchCandidate(
            incomeRecord = record,
            bankCredit = credit,
            matchRule = cand.matchRule,
            amountDeltaRupees = cand.amountDeltaRupees,
            dateGapDays = cand.dateGapDays,
            score = computedScore.coerceAtLeast(1),
            outcome = outcome
        )
    }

    private fun resolveRecordKind(
        record: IncomeRecord,
        document: IncomeDocument?,
        party: Party?
    ): ReconciliationRecordKind {
        val docType = document?.let { DocumentType.fromCode(it.documentType) }
        val partyType = party?.resolvedType()
        return when {
            docType == DocumentType.INVOICE ||
                record.invoiceNumber.isNotBlank() ||
                record.invoiceAmountRupees > 0L -> ReconciliationRecordKind.CLIENT_INVOICE
            docType == DocumentType.PLATFORM_STATEMENT ||
                partyType == PartyType.PLATFORM ||
                record.grossAmountRupees > 0L ||
                record.netAmountRupees > 0L -> ReconciliationRecordKind.PLATFORM
            else -> ReconciliationRecordKind.MANUAL_OTHER
        }
    }
}
