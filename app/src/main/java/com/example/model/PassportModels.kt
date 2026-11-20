package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PartyType(
    val code: String,
    val label: String,
    val supportsRegistration: Boolean
) {
    INDIVIDUAL("INDIVIDUAL", "Individual", supportsRegistration = false),
    BUSINESS("BUSINESS", "Business", supportsRegistration = true),
    PLATFORM("PLATFORM", "Platform", supportsRegistration = true),
    PAYMENT_PROVIDER("PAYMENT_PROVIDER", "Payment provider", supportsRegistration = true),
    OTHER("OTHER", "Other", supportsRegistration = false);

    companion object {
        fun fromCode(raw: String?): PartyType = when (raw?.trim()?.uppercase()) {
            "INDIVIDUAL", "HOUSEHOLD" -> INDIVIDUAL
            "BUSINESS", "CLIENT", "CONTRACTOR" -> BUSINESS
            "PLATFORM" -> PLATFORM
            "PAYMENT_PROVIDER" -> PAYMENT_PROVIDER
            "OTHER" -> OTHER
            else -> PLATFORM
        }
    }
}

enum class PartyRole(val code: String, val label: String, val shortLabel: String) {
    WORK_GIVER("WORK_GIVER", "Who gives me work", "Gives work"),
    PAYER("PAYER", "Who pays me", "Pays me");

    companion object {
        fun fromCode(raw: String?): PartyRole? =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * Contact provenance status for a party.
 * contact_verified proves OTP/channel control only — never legal identity, work, or payment.
 */
enum class PartyContactStatus(val code: String, val label: String) {
    ADDED_BY_WORKER("added_by_worker", "Added by worker"),
    CONTACT_PROVIDED("contact_provided", "Contact provided"),
    OUTREACH_SENT("outreach_sent", "Outreach sent"),
    CONTACT_VERIFIED("contact_verified", "Contact verified");

    companion object {
        fun fromCode(raw: String?): PartyContactStatus =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: ADDED_BY_WORKER
    }
}

/**
 * Relationship status between worker and party. Never uses the label "verified".
 */
enum class PartyRelationshipStatus(val code: String, val label: String) {
    WORKER_DECLARED("worker_declared", "Worker declared"),
    COUNTERPARTY_ACKNOWLEDGED("counterparty_acknowledged", "Counterparty acknowledged"),
    CONFIRMED_SIMULATED("confirmed_simulated", "Confirmed (Simulated)"),
    DISPUTED("disputed", "Disputed");

    companion object {
        fun fromCode(raw: String?): PartyRelationshipStatus =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: WORKER_DECLARED
    }
}

enum class PayoutCycle(val code: String, val label: String) {
    WEEKLY("WEEKLY", "Weekly"),
    BIWEEKLY("BIWEEKLY", "Every 2 weeks"),
    MONTHLY("MONTHLY", "Monthly"),
    PER_TASK("PER_TASK", "Per task / job");

    companion object {
        fun fromCode(raw: String?): PayoutCycle =
            entries.firstOrNull { it.code == raw } ?: WEEKLY
    }
}

enum class RelationshipStatus(val code: String, val label: String) {
    ACTIVE("ACTIVE", "Active"),
    PAUSED("PAUSED", "Paused"),
    ENDED("ENDED", "Ended");

    companion object {
        fun fromCode(raw: String?): RelationshipStatus =
            entries.firstOrNull { it.code == raw } ?: ACTIVE
    }
}

enum class DocumentType(val code: String, val label: String) {
    PLATFORM_STATEMENT("PLATFORM_STATEMENT", "Platform statement"),
    BANK_STATEMENT_CSV("BANK_STATEMENT_CSV", "Bank statement (CSV)"),
    INVOICE("INVOICE", "Invoice"),
    CASH_NOTE("CASH_NOTE", "Cash note"),
    UPI_RECEIPT("UPI_RECEIPT", "UPI / payment screenshot"),
    BANK_SLIP("BANK_SLIP", "Bank credit slip"),
    OTHER("OTHER", "Other income document");

    companion object {
        fun fromCode(raw: String?): DocumentType =
            entries.firstOrNull { it.code == raw } ?: OTHER
    }
}

enum class DocumentSourceKind(val code: String, val label: String) {
    PHOTO_PICKER("PHOTO_PICKER", "Uploaded photo"),
    FILE_UPLOAD("FILE_UPLOAD", "Uploaded file"),
    CSV_IMPORT("CSV_IMPORT", "Bank CSV import"),
    SAMPLE_DOC("SAMPLE_DOC", "Synthetic sample");

    companion object {
        fun fromCode(raw: String?): DocumentSourceKind =
            entries.firstOrNull { it.code == raw } ?: SAMPLE_DOC
    }
}

enum class ReviewStatus(val code: String, val label: String) {
    PENDING_REVIEW("PENDING_REVIEW", "Waiting for your confirmation"),
    CONFIRMED("CONFIRMED", "Confirmed by worker"),
    DISCARDED("DISCARDED", "Discarded");

    companion object {
        fun fromCode(raw: String?): ReviewStatus =
            entries.firstOrNull { it.code == raw } ?: PENDING_REVIEW
    }
}

enum class ReconciliationDecision(val code: String, val label: String) {
    SUGGESTED("SUGGESTED", "Suggested by rules"),
    CONFIRMED("CONFIRMED", "Confirmed match"),
    REJECTED("REJECTED", "Rejected");

    companion object {
        fun fromCode(raw: String?): ReconciliationDecision =
            entries.firstOrNull { it.code == raw } ?: SUGGESTED
    }
}

enum class ConfirmationStatus(val code: String, val label: String) {
    PENDING("PENDING", "Pending response"),
    RESPONDED("RESPONDED", "Client responded"),
    DECLINED("DECLINED", "Client declined"),
    CONFIRMED_SIMULATED("CONFIRMED_SIMULATED", "Confirmed (Simulated)"),
    DISPUTED("DISPUTED", "Disputed");

    companion object {
        fun fromCode(raw: String?): ConfirmationStatus =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: PENDING
    }
}

enum class ShareLinkStatus(val code: String, val label: String) {
    ACTIVE("ACTIVE", "Active"),
    REVOKED("REVOKED", "Revoked");

    companion object {
        fun fromCode(raw: String?): ShareLinkStatus =
            entries.firstOrNull { it.code == raw } ?: ACTIVE
    }
}

enum class ActorRole(val code: String, val label: String) {
    WORKER("WORKER", "Worker"),
    RULES("RULES", "Rules Engine"),
    AI("AI", "AI Reader"),
    PARTY_SIMULATED("PARTY_SIMULATED", "Party (Simulated)");

    companion object {
        fun fromCode(raw: String?): ActorRole =
            entries.firstOrNull { it.code == raw } ?: WORKER
    }
}

// --- 10 Firestore Collection Data Classes (Exact 1:1 parity with firestore.rules) ---

@Keep
@IgnoreExtraProperties
data class WorkerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val city: String = "",
    val primaryWorkType: String = "",
    val demoProfile: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Party(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workerId: String = "",
    val name: String = "",
    val partyType: String = PartyType.PLATFORM.code,
    val contactHint: String = "",
    val roles: List<String> = listOf(PartyRole.WORK_GIVER.code, PartyRole.PAYER.code),
    val phone: String = "",
    val email: String = "",
    val note: String = "",
    val registrationNumber: String = "",
    val partyContactStatus: String = PartyContactStatus.ADDED_BY_WORKER.code,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedType(): PartyType = PartyType.fromCode(partyType)

    fun resolvedRoles(): List<PartyRole> {
        val parsed = roles.mapNotNull { PartyRole.fromCode(it) }.distinct()
        return if (parsed.isEmpty()) listOf(PartyRole.WORK_GIVER, PartyRole.PAYER) else parsed
    }

    fun resolvedContactStatus(): PartyContactStatus = PartyContactStatus.fromCode(partyContactStatus)

    fun givesWork(): Boolean = PartyRole.WORK_GIVER in resolvedRoles()

    fun paysMe(): Boolean = PartyRole.PAYER in resolvedRoles()
}

@Keep
@IgnoreExtraProperties
data class WorkRelationship(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workerId: String = "",
    val partyId: String = "",
    val roleTitle: String = "",
    val payoutCycle: String = PayoutCycle.WEEKLY.code,
    val status: String = RelationshipStatus.ACTIVE.code,
    val relationshipStatus: String = PartyRelationshipStatus.WORKER_DECLARED.code,
    val roles: List<String> = emptyList(),
    val startedOn: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedRelationshipStatus(): PartyRelationshipStatus =
        PartyRelationshipStatus.fromCode(relationshipStatus)
}

@Keep
@IgnoreExtraProperties
data class IncomeRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workerId: String = "",
    val partyId: String = "",
    val partyName: String = "",
    val workGiverPartyId: String = "",
    val workGiverPartyName: String = "",
    val payerPartyId: String = "",
    val payerPartyName: String = "",
    val documentId: String = "",
    val amountRupees: Long = 0L,
    val earnedDate: String = "",
    val periodLabel: String = "",
    val referenceCode: String = "",
    val invoiceNumber: String = "",
    val periodStart: String = "",
    val periodEnd: String = "",
    val grossAmountRupees: Long = 0L,
    val deductionsRupees: Long = 0L,
    val netAmountRupees: Long = 0L,
    val invoiceAmountRupees: Long = 0L,
    val aiOriginal: Map<String, Any> = emptyMap(),
    val evidenceStatus: String = EvidenceStatus.SELF_DECLARED.code,
    val workerConfirmed: Boolean = true,
    val simulatedApi: Boolean = false,
    val duplicateGroupKey: String = "",
    val groupedIntoRecordId: String = "",
    val duplicateIgnored: Boolean = false,
    val matchOutcome: String = "NONE",
    val paymentStatus: String = "UNKNOWN",
    val paymentDate: String = "",
    val conflictReason: String = "",
    val manuallyDisputed: Boolean = false,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedEvidence(): EvidenceStatus = EvidenceStatus.fromCode(evidenceStatus)
    fun isSecondaryDuplicate(): Boolean = groupedIntoRecordId.isNotBlank()
}

@Keep
@IgnoreExtraProperties
data class IncomeDocument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workerId: String = "",
    val partyId: String = "",
    val title: String = "",
    val documentType: String = DocumentType.PLATFORM_STATEMENT.code,
    val sourceKind: String = DocumentSourceKind.SAMPLE_DOC.code,
    val reviewStatus: String = ReviewStatus.PENDING_REVIEW.code,
    val fileHash: String = "",
    val extractedPartyName: String = "",
    val extractedAmountRupees: Long = 0L,
    val extractedDate: String = "",
    val extractedReference: String = "",
    val extractionNotes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BankCredit(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workerId: String = "",
    val bankLabel: String = "",
    val creditDate: String = "",
    val amountRupees: Long = 0L,
    val narration: String = "",
    val utrReference: String = "",
    val matchedIncomeRecordId: String = "",
    val classificationStatus: String = "UNCLASSIFIED",
    val evidenceStatus: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Reconciliation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workerId: String = "",
    val incomeRecordId: String = "",
    val bankCreditId: String = "",
    val matchRule: String = "",
    val amountDeltaRupees: Long = 0L,
    val dateGapDays: Long = 0L,
    val decisionStatus: String = ReconciliationDecision.SUGGESTED.code,
    val matchOutcome: String = "STRONG",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ConfirmationRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workerId: String = "",
    val incomeRecordId: String = "",
    val partyId: String = "",
    val partyName: String = "",
    val workerName: String = "",
    val workDescription: String = "",
    val earnedDate: String = "",
    val periodLabel: String = "",
    val requestedAmountRupees: Long = 0L,
    val claimVersion: Int = 1,
    val claimSnapshot: Map<String, Any> = emptyMap(),
    val demoOtpCode: String = "",
    val expiresAt: Timestamp? = null,
    val status: String = ConfirmationStatus.PENDING.code,
    val responseNote: String = "",
    val workDelivered: Boolean? = null,
    val confirmedAmountRupees: Long? = null,
    val paymentSent: Boolean? = null,
    val paymentDate: String = "",
    val responses: Map<String, Any> = emptyMap(),
    val responded_at: Timestamp? = null,
    val otp_attempts: Int = 0,
    val workerResolution: String = "",
    val appliedToLedgerAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isPublicTokenRequest(): Boolean = id.length >= 32 && expiresAt != null
}

@Keep
@IgnoreExtraProperties
data class ShareLink(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workerId: String = "",
    val workerName: String = "",
    val shareCode: String = "",
    val recipientLabel: String = "",
    val dateRangeLabel: String = "",
    val includedPartyIds: List<String> = emptyList(),
    val includedEvidenceLevels: List<String> = emptyList(),
    val maskBankRefs: Boolean = true,
    val expiresOn: String = "",
    val status: String = ShareLinkStatus.ACTIVE.code,
    val totalIncludedRupees: Long = 0L,
    val summaryLines: List<String> = emptyList(),
    val consentText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AuditLogEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workerId: String = "",
    val actionType: String = "",
    val actorRole: String = ActorRole.WORKER.code,
    val targetCollection: String = "",
    val targetId: String = "",
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
