package com.example.domain.rules

import com.example.model.ActorRole
import com.example.model.AuditLogEntry
import com.example.model.ConfirmationRequest
import com.example.model.ConfirmationStatus
import com.example.model.IncomeRecord
import com.example.model.Party
import com.example.model.PartyRole
import com.example.model.PartyType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class SimilarPartyMatch(
    val party: Party,
    val reason: String
)

data class PartyHistoryEvent(
    val id: String,
    val title: String,
    val detail: String,
    val actorLabel: String
)

/**
 * Pure-Kotlin business rules for Parties:
 * - Deterministic fuzzy/normalized similar-name warnings to prevent duplicate parties
 * - Enforcing that Individual parties do not store a registration number
 * - Linking income records where a party acted as work giver, payer, or both
 * - Building a party's chronological history from audit logs and confirmation requests
 */
object PartyRules {

    private val GENERIC_SUFFIX_TOKENS = setOf(
        "pvt", "ltd", "limited", "inc", "llp", "co", "company",
        "delivery", "express", "india", "technologies", "services",
        "family", "household", "studio", "solutions", "payments", "pay"
    )

    fun normalizeName(raw: String): String {
        return raw.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun coreTokens(raw: String): Set<String> {
        val all = normalizeName(raw)
            .split(" ")
            .filter { it.length >= 2 }
        val filtered = all.filterNot { it in GENERIC_SUFFIX_TOKENS }
        return (if (filtered.isNotEmpty()) filtered else all).toSet()
    }

    fun findSimilarParties(
        candidateName: String,
        existingParties: List<Party>,
        excludePartyId: String? = null
    ): List<SimilarPartyMatch> {
        val normCandidate = normalizeName(candidateName)
        if (normCandidate.length < 3) return emptyList()
        val candidateTokens = coreTokens(candidateName)

        return existingParties.mapNotNull { party ->
            if (excludePartyId != null && party.id == excludePartyId) return@mapNotNull null
            val normExisting = normalizeName(party.name)
            if (normExisting.isEmpty()) return@mapNotNull null

            val existingTokens = coreTokens(party.name)
            val sharedTokens = candidateTokens.intersect(existingTokens)

            when {
                normCandidate == normExisting -> {
                    SimilarPartyMatch(party, "Exact same name already exists")
                }
                normCandidate.length >= 4 && normExisting.length >= 4 &&
                    (normExisting.contains(normCandidate) || normCandidate.contains(normExisting)) -> {
                    SimilarPartyMatch(party, "Closely matches existing name")
                }
                sharedTokens.any { it.length >= 4 } -> {
                    SimilarPartyMatch(party, "Shares key name '${sharedTokens.first { it.length >= 4 }}'")
                }
                normCandidate.length >= 4 && normExisting.length >= 4 &&
                    abs(normCandidate.length - normExisting.length) <= 2 &&
                    levenshteinDistance(normCandidate, normExisting) <= 2 -> {
                    SimilarPartyMatch(party, "Very similar spelling to existing party")
                }
                else -> {
                    val tokenEditMatch = candidateTokens.any { ct ->
                        ct.length >= 4 && existingTokens.any { et ->
                            et.length >= 4 &&
                                abs(ct.length - et.length) <= 1 &&
                                levenshteinDistance(ct, et) == 1
                        }
                    }
                    if (tokenEditMatch) {
                        SimilarPartyMatch(party, "Possible typo of existing party name")
                    } else {
                        null
                    }
                }
            }
        }.take(3)
    }

    /**
     * Individuals (and Other) never require or carry a registration / GSTIN number.
     */
    fun sanitizeRegistrationNumber(partyType: PartyType, rawRegistration: String): String {
        if (!partyType.supportsRegistration) return ""
        return rawRegistration.trim().uppercase().take(40)
    }

    fun defaultRolesIfEmpty(roles: Collection<PartyRole>): List<PartyRole> {
        return if (roles.isEmpty()) listOf(PartyRole.WORK_GIVER, PartyRole.PAYER) else roles.distinct()
    }

    fun recordsForParty(party: Party, allRecords: List<IncomeRecord>): List<IncomeRecord> {
        return allRecords.filter { rec ->
            isRecordLinkedToParty(party, rec)
        }.sortedByDescending { it.earnedDate }
    }

    fun isRecordLinkedToParty(party: Party, rec: IncomeRecord): Boolean {
        if (rec.workGiverPartyId == party.id || rec.payerPartyId == party.id || rec.partyId == party.id) {
            return true
        }
        val hasAnyPartyId = rec.workGiverPartyId.isNotBlank() || rec.payerPartyId.isNotBlank() || rec.partyId.isNotBlank()
        if (!hasAnyPartyId) {
            return rec.partyName.equals(party.name, ignoreCase = true) ||
                rec.workGiverPartyName.equals(party.name, ignoreCase = true) ||
                rec.payerPartyName.equals(party.name, ignoreCase = true)
        }
        return false
    }

    fun roleLabelForRecord(party: Party, rec: IncomeRecord): String {
        val isWorkGiver = rec.workGiverPartyId == party.id ||
            (rec.workGiverPartyId.isBlank() && rec.workGiverPartyName.equals(party.name, ignoreCase = true))
        val isPayer = rec.payerPartyId == party.id ||
            (rec.payerPartyId.isBlank() && rec.payerPartyName.equals(party.name, ignoreCase = true))

        return when {
            isWorkGiver && isPayer -> "Gave work & Paid"
            isWorkGiver -> "Gave work"
            isPayer -> "Paid"
            else -> "Linked party"
        }
    }

    fun historyForParty(
        party: Party,
        linkedRecords: List<IncomeRecord>,
        confirmations: List<ConfirmationRequest>,
        auditLogs: List<AuditLogEntry>
    ): List<PartyHistoryEvent> {
        val recordIds = linkedRecords.map { it.id }.toSet()
        val partyConfirmations = confirmations.filter { it.partyId == party.id || it.incomeRecordId in recordIds }
        val confirmationIds = partyConfirmations.map { it.id }.toSet()

        val matchingLogs = auditLogs.filter { log ->
            log.targetId == party.id ||
                log.targetId in recordIds ||
                log.targetId in confirmationIds ||
                log.summary.contains(party.name, ignoreCase = true)
        }

        val events = mutableListOf<PartyHistoryEvent>()
        matchingLogs.forEach { log ->
            events.add(
                PartyHistoryEvent(
                    id = "log_${log.id}",
                    title = formatActionTitle(log.actionType),
                    detail = log.summary,
                    actorLabel = ActorRole.fromCode(log.actorRole).label
                )
            )
        }

        if (events.isEmpty()) {
            partyConfirmations.forEach { req ->
                val status = ConfirmationStatus.fromCode(req.status)
                events.add(
                    PartyHistoryEvent(
                        id = "conf_${req.id}",
                        title = "Confirmation: ${status.label}",
                        detail = req.responseNote.ifBlank {
                            "Requested confirmation for ${RupeeFormatter.format(req.requestedAmountRupees)}."
                        },
                        actorLabel = "Worker / Simulated Party"
                    )
                )
            }
        }

        return events
    }

    private fun formatActionTitle(actionType: String): String {
        return actionType
            .lowercase()
            .split('_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            .ifBlank { "Activity" }
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val maxLen = max(a.length, b.length)
        if (maxLen > 60) return abs(a.length - b.length)

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }
}
