package com.example.domain.rules

import com.example.model.BankCredit
import com.example.model.EvidenceStatus
import com.example.model.IncomeRecord
import com.example.model.Party
import com.example.model.ShareLink
import com.example.model.ShareLinkStatus
import com.example.model.WorkRelationship

data class EvidenceTierTotal(
    val status: EvidenceStatus,
    val totalRupees: Long,
    val recordCount: Int
)

data class PartyEarningsSummary(
    val party: Party,
    val relationship: WorkRelationship?,
    val totalRupees: Long,
    val recordCount: Int,
    val highestEvidence: EvidenceStatus
)

data class PassportSummary(
    val nonDisputedTotalRupees: Long,
    val disputedTotalRupees: Long,
    val recordCount: Int,
    val tierTotals: List<EvidenceTierTotal>,
    val partySummaries: List<PartyEarningsSummary>
)

data class ReviewerReportLine(
    val partyName: String,
    val earnedDate: String,
    val periodLabel: String,
    val amountRupees: Long,
    val evidenceStatus: EvidenceStatus,
    val displayReference: String
)

data class ReviewerReport(
    val shareLink: ShareLink,
    val isRevoked: Boolean,
    val totalRupees: Long,
    val tierTotals: List<EvidenceTierTotal>,
    val lines: List<ReviewerReportLine>,
    val plainTextExport: String
)

/**
 * Pure aggregation & consent-scoped projection rules for Passport and Reviewer View.
 */
object PassportAggregator {

    fun buildSummary(
        incomeRecords: List<IncomeRecord>,
        parties: List<Party>,
        relationships: List<WorkRelationship>
    ): PassportSummary {
        // Rule 1: Grouped duplicate representations are counted strictly once (primary items only)
        val primaryRecords = incomeRecords.filter { !it.isSecondaryDuplicate() }

        val tierTotals = EvidenceStatus.entries.map { status ->
            val matching = primaryRecords.filter { it.resolvedEvidence() == status }
            EvidenceTierTotal(
                status = status,
                totalRupees = matching.sumOf { it.amountRupees },
                recordCount = matching.size
            )
        }

        val nonDisputedTotal = primaryRecords
            .filter { it.resolvedEvidence() != EvidenceStatus.DISPUTED }
            .sumOf { it.amountRupees }

        val disputedTotal = primaryRecords
            .filter { it.resolvedEvidence() == EvidenceStatus.DISPUTED }
            .sumOf { it.amountRupees }

        val relByParty = relationships.associateBy { it.partyId }
        val partySummaries = parties.map { party ->
            val partyRecords = PartyRules.recordsForParty(party, primaryRecords)
            val bestStatus = partyRecords
                .map { it.resolvedEvidence() }
                .minByOrNull { it.sortRank }
                ?: EvidenceStatus.SELF_DECLARED
            PartyEarningsSummary(
                party = party,
                relationship = relByParty[party.id],
                totalRupees = partyRecords
                    .filter { it.resolvedEvidence() != EvidenceStatus.DISPUTED }
                    .sumOf { it.amountRupees },
                recordCount = partyRecords.size,
                highestEvidence = bestStatus
            )
        }.sortedByDescending { it.totalRupees }

        return PassportSummary(
            nonDisputedTotalRupees = nonDisputedTotal,
            disputedTotalRupees = disputedTotal,
            recordCount = primaryRecords.size,
            tierTotals = tierTotals,
            partySummaries = partySummaries
        )
    }

    /**
     * Filters income records strictly by the worker's consent scope and produces
     * snapshot lines suitable for storing natively in ShareLink.summaryLines.
     */
    fun buildShareSnapshot(
        incomeRecords: List<IncomeRecord>,
        includedPartyIds: Set<String>,
        includedEvidenceCodes: Set<String>,
        maskBankRefs: Boolean
    ): Pair<Long, List<String>> {
        val filtered = filterConsentedRecords(incomeRecords, includedPartyIds, includedEvidenceCodes)
        val total = filtered
            .filter { it.resolvedEvidence() != EvidenceStatus.DISPUTED }
            .sumOf { it.amountRupees }

        val lines = filtered.take(80).map { rec ->
            val ref = if (maskBankRefs) RupeeFormatter.maskReference(rec.referenceCode) else rec.referenceCode.ifBlank { "No ref" }
            "${rec.earnedDate} | ${rec.partyName} | ${RupeeFormatter.format(rec.amountRupees)} | ${rec.resolvedEvidence().label} | Ref: $ref"
        }
        return total to lines
    }

    fun buildReviewerReport(
        shareLink: ShareLink,
        allIncomeRecords: List<IncomeRecord>,
        allBankCredits: List<BankCredit>
    ): ReviewerReport {
        val revoked = ShareLinkStatus.fromCode(shareLink.status) == ShareLinkStatus.REVOKED
        val partySet = shareLink.includedPartyIds.toSet()
        val evidenceSet = shareLink.includedEvidenceLevels.toSet()

        val filtered = filterConsentedRecords(allIncomeRecords, partySet, evidenceSet)
        val creditByRecord = allBankCredits
            .filter { it.matchedIncomeRecordId.isNotBlank() }
            .associateBy { it.matchedIncomeRecordId }

        val lines = filtered.map { rec ->
            val linkedCredit = creditByRecord[rec.id]
            val rawRef = linkedCredit?.utrReference?.ifBlank { rec.referenceCode } ?: rec.referenceCode
            val displayRef = if (shareLink.maskBankRefs) {
                RupeeFormatter.maskReference(rawRef)
            } else {
                rawRef.ifBlank { "Not provided" }
            }
            ReviewerReportLine(
                partyName = rec.partyName,
                earnedDate = rec.earnedDate,
                periodLabel = rec.periodLabel,
                amountRupees = rec.amountRupees,
                evidenceStatus = rec.resolvedEvidence(),
                displayReference = displayRef
            )
        }

        val tierTotals = EvidenceStatus.entries
            .filter { it.code in evidenceSet }
            .map { status ->
                val subset = filtered.filter { it.resolvedEvidence() == status }
                EvidenceTierTotal(
                    status = status,
                    totalRupees = subset.sumOf { it.amountRupees },
                    recordCount = subset.size
                )
            }

        val totalRupees = if (lines.isNotEmpty()) {
            filtered.filter { it.resolvedEvidence() != EvidenceStatus.DISPUTED }.sumOf { it.amountRupees }
        } else {
            shareLink.totalIncludedRupees
        }

        val exportText = buildString {
            appendLine("EARNINGS & WORK PASSPORT — READ-ONLY REPORT")
            appendLine("Share Code: ${shareLink.shareCode} | Status: ${ShareLinkStatus.fromCode(shareLink.status).label}")
            appendLine("Worker: ${shareLink.workerName}")
            appendLine("Shared With: ${shareLink.recipientLabel}")
            appendLine("Period: ${shareLink.dateRangeLabel} | Valid Until: ${shareLink.expiresOn}")
            appendLine("--------------------------------------------------")
            appendLine("NOTICE: Not a lender. Not a credit score. Not AI-verified income.")
            appendLine("Every figure below carries an explicit evidence label.")
            appendLine("--------------------------------------------------")
            appendLine("Included Total (non-disputed): ${RupeeFormatter.format(totalRupees)}")
            tierTotals.filter { it.recordCount > 0 }.forEach { tier ->
                appendLine("  - ${tier.status.label}: ${RupeeFormatter.format(tier.totalRupees)} (${tier.recordCount} entries)")
            }
            appendLine()
            appendLine("Ledger Entries:")
            if (lines.isNotEmpty()) {
                lines.forEach { line ->
                    appendLine("• ${line.earnedDate} | ${line.partyName} | ${RupeeFormatter.format(line.amountRupees)} | [${line.evidenceStatus.label}] | Ref: ${line.displayReference}")
                }
            } else {
                shareLink.summaryLines.forEach { snap -> appendLine("• $snap") }
            }
            appendLine()
            appendLine("Consent: ${shareLink.consentText}")
            appendLine("AI reads. Rules and you decide.")
        }

        return ReviewerReport(
            shareLink = shareLink,
            isRevoked = revoked,
            totalRupees = totalRupees,
            tierTotals = tierTotals,
            lines = lines,
            plainTextExport = exportText
        )
    }

    private fun filterConsentedRecords(
        records: List<IncomeRecord>,
        includedPartyIds: Set<String>,
        includedEvidenceCodes: Set<String>
    ): List<IncomeRecord> {
        return records.filter { rec ->
            if (rec.isSecondaryDuplicate()) return@filter false
            val partyAllowed = includedPartyIds.isEmpty() ||
                rec.partyId in includedPartyIds ||
                rec.workGiverPartyId in includedPartyIds ||
                rec.payerPartyId in includedPartyIds ||
                (rec.partyId.isBlank() && rec.workGiverPartyId.isBlank() && rec.payerPartyId.isBlank())
            val evidenceAllowed = includedEvidenceCodes.isEmpty() || rec.evidenceStatus in includedEvidenceCodes
            partyAllowed && evidenceAllowed
        }.sortedByDescending { it.earnedDate }
    }
}
