package com.example.domain.rules

import com.example.model.ConfirmationRequest
import com.example.model.ConfirmationStatus
import java.security.SecureRandom
import kotlin.math.abs

/**
 * Pure Kotlin deterministic rules for Counterparty Confirmation:
 * - Cryptographically random >= 32-char confirmation token generation
 * - 7-day request expiry
 * - On-screen DEMO OTP verification (5-minute expiry, 3 failed attempts -> 5-minute lockout)
 * - Per-question answer parsing & strict status propagation without ever altering evidence_label
 */
enum class CounterpartyAnswerChoice(val code: String, val label: String) {
    YES("yes", "Yes"),
    NO("no", "No"),
    UNSURE("unsure", "Unsure"),
    CORRECTED_AMOUNT("corrected_amount", "Corrected amount"),
    DECLINED("declined", "Declined");

    companion object {
        fun fromCode(raw: String?): CounterpartyAnswerChoice? =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) }
    }
}

enum class WorkerConfirmationResolution(val code: String, val label: String) {
    NONE("", ""),
    PENDING_REVIEW("PENDING_REVIEW", "Waiting for your review"),
    CORRECTION_ACCEPTED("CORRECTION_ACCEPTED", "Correction accepted"),
    DISPUTED_BY_WORKER("DISPUTED_BY_WORKER", "Disputed by worker");

    companion object {
        fun fromCode(raw: String?): WorkerConfirmationResolution =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: NONE
    }
}

data class DemoOtpState(
    val activeCode: String,
    val issuedAtEpochMs: Long,
    val failedAttemptsInWindow: Int = 0,
    val totalFailedAttempts: Int = 0,
    val lockedUntilEpochMs: Long = 0L,
    val verifiedAtEpochMs: Long? = null
) {
    fun isVerified(): Boolean = verifiedAtEpochMs != null

    fun isLocked(nowEpochMs: Long): Boolean = lockedUntilEpochMs > nowEpochMs

    fun remainingLockoutSeconds(nowEpochMs: Long): Long =
        ((lockedUntilEpochMs - nowEpochMs).coerceAtLeast(0L) + 999L) / 1000L

    fun isExpired(nowEpochMs: Long): Boolean =
        !isVerified() && (nowEpochMs - issuedAtEpochMs) >= CounterpartyConfirmationRules.OTP_EXPIRY_MS

    fun remainingExpirySeconds(nowEpochMs: Long): Long {
        val remaining = (issuedAtEpochMs + CounterpartyConfirmationRules.OTP_EXPIRY_MS) - nowEpochMs
        return (remaining.coerceAtLeast(0L) + 999L) / 1000L
    }
}

sealed interface OtpVerifyOutcome {
    data object Verified : OtpVerifyOutcome
    data class WrongCode(val attemptsRemaining: Int) : OtpVerifyOutcome
    data class Locked(val remainingSeconds: Long) : OtpVerifyOutcome
    data object Expired : OtpVerifyOutcome
}

data class ParsedCounterpartyResponses(
    val workDelivered: CounterpartyAnswerChoice? = null,
    val amountCorrect: CounterpartyAnswerChoice? = null,
    val correctedAmountRupees: Long? = null,
    val paidStatus: CounterpartyAnswerChoice? = null,
    val paidDate: String = "",
    val claimVersionShown: Int = 1,
    val otpVerifiedAtEpochMs: Long? = null,
    val otpLockedUntilEpochMs: Long = 0L
) {
    fun hasAnyAnswer(): Boolean =
        workDelivered != null || amountCorrect != null || paidStatus != null

    fun isAllDeclined(): Boolean {
        val provided = listOfNotNull(workDelivered, amountCorrect, paidStatus)
        return provided.isNotEmpty() && provided.all { it == CounterpartyAnswerChoice.DECLINED }
    }

    fun toFirestoreMap(): Map<String, Any> {
        val map = linkedMapOf<String, Any>()
        workDelivered?.let { map["workDelivered"] = it.code }
        amountCorrect?.let { map["amountCorrect"] = it.code }
        correctedAmountRupees?.takeIf { it > 0L }?.let { map["correctedAmountRupees"] = it }
        paidStatus?.let { map["paidStatus"] = it.code }
        if (paidDate.isNotBlank()) map["paidDate"] = paidDate.trim().take(30)
        map["claimVersionShown"] = claimVersionShown.coerceAtLeast(1)
        otpVerifiedAtEpochMs?.let { map["otpVerifiedAtEpochMs"] = it }
        if (otpLockedUntilEpochMs > 0L) map["otpLockedUntilEpochMs"] = otpLockedUntilEpochMs
        return map
    }
}

data class CounterpartyStatusOutcome(
    val hasCounterpartySubmission: Boolean,
    val contactVerified: Boolean,
    val workConfirmed: Boolean,
    val paymentConfirmed: Boolean,
    val paymentNotYet: Boolean,
    val paymentDate: String,
    val correctedAmountRupees: Long?,
    val triggersDispute: Boolean,
    val disputeReason: String,
    val summaryLine: String,
    val workerResolution: WorkerConfirmationResolution
)

object CounterpartyConfirmationRules {

    const val MIN_TOKEN_LENGTH = 32
    const val DEFAULT_TOKEN_LENGTH = 40
    const val REQUEST_EXPIRY_DAYS = 7L
    const val REQUEST_EXPIRY_MS = REQUEST_EXPIRY_DAYS * 24L * 60L * 60L * 1000L
    const val OTP_EXPIRY_MS = 5L * 60L * 1000L
    const val OTP_LOCKOUT_MS = 5L * 60L * 1000L
    const val OTP_MAX_ATTEMPTS = 3

    const val LINK_SCHEME = "workpassport"
    const val LINK_HOST = "confirm"
    const val HTTPS_LINK_HOST = "workpassport.demo"
    const val CONFIRMATION_LINK_PREFIX = "$LINK_SCHEME://$LINK_HOST/"

    const val DEMO_OTP_LABEL = "DEMO OTP, not a real SMS"
    const val OTP_DISCLAIMER_TEXT =
        "An OTP only shows control of a phone or email — not identity, work, or payment."

    private const val TOKEN_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    private val secureRandom = SecureRandom()

    fun generateRandomToken(length: Int = DEFAULT_TOKEN_LENGTH): String {
        val targetLength = length.coerceAtLeast(MIN_TOKEN_LENGTH)
        val chars = CharArray(targetLength)
        for (i in 0 until targetLength) {
            chars[i] = TOKEN_ALPHABET[secureRandom.nextInt(TOKEN_ALPHABET.length)]
        }
        return String(chars)
    }

    fun generateDemoOtpCode(): String {
        val codeInt = secureRandom.nextInt(1_000_000)
        return "%06d".format(java.util.Locale.ENGLISH, codeInt)
    }

    fun computeExpiryEpochMs(createdEpochMs: Long): Long = createdEpochMs + REQUEST_EXPIRY_MS

    fun isRequestExpired(expiresAtEpochMs: Long?, nowEpochMs: Long): Boolean {
        if (expiresAtEpochMs == null || expiresAtEpochMs <= 0L) return false
        return nowEpochMs >= expiresAtEpochMs
    }

    fun buildConfirmationLink(token: String): String = "$CONFIRMATION_LINK_PREFIX${token.trim()}"

    fun extractTokenFromInput(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return ""
        val withoutQuery = trimmed.substringBefore('?').substringBefore('#').trimEnd('/')
        val extracted = when {
            withoutQuery.startsWith(CONFIRMATION_LINK_PREFIX, ignoreCase = true) ->
                withoutQuery.substring(CONFIRMATION_LINK_PREFIX.length)
            withoutQuery.contains("/confirm/", ignoreCase = true) ->
                withoutQuery.substringAfterLast("/confirm/")
            else -> withoutQuery
        }
        return extracted.trim().filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    fun verifyDemoOtp(
        state: DemoOtpState,
        enteredCode: String,
        nowEpochMs: Long
    ): Pair<DemoOtpState, OtpVerifyOutcome> {
        if (state.isLocked(nowEpochMs)) {
            return state to OtpVerifyOutcome.Locked(state.remainingLockoutSeconds(nowEpochMs))
        }
        if (state.isExpired(nowEpochMs)) {
            return state to OtpVerifyOutcome.Expired
        }
        val cleanInput = enteredCode.filter { it.isDigit() }
        if (cleanInput == state.activeCode && cleanInput.length == 6) {
            val verifiedState = state.copy(
                failedAttemptsInWindow = 0,
                lockedUntilEpochMs = 0L,
                verifiedAtEpochMs = nowEpochMs
            )
            return verifiedState to OtpVerifyOutcome.Verified
        }

        val nextWindowFailures = state.failedAttemptsInWindow + 1
        val nextTotalFailures = state.totalFailedAttempts + 1
        return if (nextWindowFailures >= OTP_MAX_ATTEMPTS) {
            val lockedUntil = nowEpochMs + OTP_LOCKOUT_MS
            val lockedState = state.copy(
                failedAttemptsInWindow = 0,
                totalFailedAttempts = nextTotalFailures,
                lockedUntilEpochMs = lockedUntil
            )
            lockedState to OtpVerifyOutcome.Locked(lockedState.remainingLockoutSeconds(nowEpochMs))
        } else {
            val updated = state.copy(
                failedAttemptsInWindow = nextWindowFailures,
                totalFailedAttempts = nextTotalFailures
            )
            updated to OtpVerifyOutcome.WrongCode(OTP_MAX_ATTEMPTS - nextWindowFailures)
        }
    }

    fun regenerateDemoOtp(
        state: DemoOtpState,
        newCode: String,
        nowEpochMs: Long
    ): DemoOtpState {
        if (state.isLocked(nowEpochMs)) return state
        return state.copy(
            activeCode = newCode,
            issuedAtEpochMs = nowEpochMs,
            failedAttemptsInWindow = 0,
            lockedUntilEpochMs = 0L
        )
    }

    fun parseResponses(rawMap: Map<String, Any>?): ParsedCounterpartyResponses {
        if (rawMap.isNullOrEmpty()) return ParsedCounterpartyResponses()
        val work = CounterpartyAnswerChoice.fromCode(rawMap["workDelivered"] as? String)
        val amtChoice = CounterpartyAnswerChoice.fromCode(rawMap["amountCorrect"] as? String)
        val correctedAmt = (rawMap["correctedAmountRupees"] as? Number)?.toLong()?.takeIf { it > 0L }
        val paid = CounterpartyAnswerChoice.fromCode(rawMap["paidStatus"] as? String)
        val paidDate = (rawMap["paidDate"] as? String).orEmpty().trim()
        val version = (rawMap["claimVersionShown"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1
        val otpVerifiedAt = (rawMap["otpVerifiedAtEpochMs"] as? Number)?.toLong()?.takeIf { it > 0L }
        val otpLockedUntil = (rawMap["otpLockedUntilEpochMs"] as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
        return ParsedCounterpartyResponses(
            workDelivered = work,
            amountCorrect = amtChoice,
            correctedAmountRupees = correctedAmt,
            paidStatus = paid,
            paidDate = paidDate,
            claimVersionShown = version,
            otpVerifiedAtEpochMs = otpVerifiedAt,
            otpLockedUntilEpochMs = otpLockedUntil
        )
    }

    fun evaluateRequest(request: ConfirmationRequest): CounterpartyStatusOutcome {
        val status = ConfirmationStatus.fromCode(request.status)
        val workerRes = WorkerConfirmationResolution.fromCode(request.workerResolution)
        val parsed = parseResponses(request.responses)

        // Legacy simulated request path (no `responses` map)
        if (request.responses.isEmpty() && (status == ConfirmationStatus.CONFIRMED_SIMULATED || status == ConfirmationStatus.DISPUTED)) {
            val isDispute = status == ConfirmationStatus.DISPUTED ||
                (request.confirmedAmountRupees != null &&
                    request.confirmedAmountRupees > 0L &&
                    abs(request.confirmedAmountRupees - request.requestedAmountRupees) > 1L)
            return CounterpartyStatusOutcome(
                hasCounterpartySubmission = true,
                contactVerified = false,
                workConfirmed = request.workDelivered == true,
                paymentConfirmed = request.paymentSent == true,
                paymentNotYet = request.paymentSent == false,
                paymentDate = request.paymentDate.trim(),
                correctedAmountRupees = request.confirmedAmountRupees?.takeIf {
                    it > 0L && abs(it - request.requestedAmountRupees) > 1L
                },
                triggersDispute = isDispute && workerRes != WorkerConfirmationResolution.CORRECTION_ACCEPTED,
                disputeReason = if (isDispute) {
                    request.responseNote.ifBlank { "Client disputed this payment." }
                } else "",
                summaryLine = request.responseNote,
                workerResolution = workerRes
            )
        }

        val hasSubmission = status == ConfirmationStatus.RESPONDED || status == ConfirmationStatus.DECLINED
        if (!hasSubmission) {
            return CounterpartyStatusOutcome(
                hasCounterpartySubmission = false,
                contactVerified = parsed.otpVerifiedAtEpochMs != null,
                workConfirmed = false,
                paymentConfirmed = false,
                paymentNotYet = false,
                paymentDate = "",
                correctedAmountRupees = null,
                triggersDispute = false,
                disputeReason = "",
                summaryLine = "",
                workerResolution = workerRes
            )
        }

        val workConfirmed = parsed.workDelivered == CounterpartyAnswerChoice.YES
        val paymentConfirmed = parsed.paidStatus == CounterpartyAnswerChoice.YES
        val paymentNotYet = parsed.paidStatus == CounterpartyAnswerChoice.NO
        val correctedAmount = parsed.correctedAmountRupees?.takeIf {
            parsed.amountCorrect == CounterpartyAnswerChoice.CORRECTED_AMOUNT && it > 0L
        }

        val disputeReasons = mutableListOf<String>()
        if (parsed.workDelivered == CounterpartyAnswerChoice.NO) {
            disputeReasons.add("${request.partyName} answered No to work delivery")
        }
        if (parsed.amountCorrect == CounterpartyAnswerChoice.NO) {
            disputeReasons.add("${request.partyName} answered No to amount ${RupeeFormatter.format(request.requestedAmountRupees)}")
        }
        if (correctedAmount != null && abs(correctedAmount - request.requestedAmountRupees) > 1L) {
            disputeReasons.add(
                "${request.partyName} suggested ${RupeeFormatter.format(correctedAmount)} instead of ${RupeeFormatter.format(request.requestedAmountRupees)}"
            )
        }
        if (parsed.paidStatus == CounterpartyAnswerChoice.NO) {
            disputeReasons.add("${request.partyName} answered No to payment")
        }

        val rawConflict = disputeReasons.isNotEmpty()
        val effectiveDispute = when (workerRes) {
            WorkerConfirmationResolution.CORRECTION_ACCEPTED -> false
            WorkerConfirmationResolution.DISPUTED_BY_WORKER -> true
            else -> rawConflict
        }

        val summaryParts = mutableListOf<String>()
        parsed.workDelivered?.let { summaryParts.add("Work delivered: ${it.label}") }
        parsed.amountCorrect?.let { choice ->
            if (choice == CounterpartyAnswerChoice.CORRECTED_AMOUNT && correctedAmount != null) {
                summaryParts.add("Amount corrected to ${RupeeFormatter.format(correctedAmount)}")
            } else {
                summaryParts.add("Amount: ${choice.label}")
            }
        }
        parsed.paidStatus?.let { choice ->
            val dateSuffix = if (choice == CounterpartyAnswerChoice.YES && parsed.paidDate.isNotBlank()) {
                " (${parsed.paidDate})"
            } else ""
            summaryParts.add("Paid: ${choice.label}$dateSuffix")
        }
        val summaryLine = if (summaryParts.isEmpty()) {
            "${request.partyName} declined to answer."
        } else {
            summaryParts.joinToString(" · ")
        }

        val disputeReason = when {
            workerRes == WorkerConfirmationResolution.DISPUTED_BY_WORKER && disputeReasons.isEmpty() ->
                "Worker disputed ${request.partyName}'s confirmation response."
            disputeReasons.isNotEmpty() -> disputeReasons.joinToString("; ") + "."
            else -> ""
        }

        return CounterpartyStatusOutcome(
            hasCounterpartySubmission = true,
            contactVerified = parsed.otpVerifiedAtEpochMs != null || hasSubmission,
            workConfirmed = workConfirmed,
            paymentConfirmed = paymentConfirmed,
            paymentNotYet = paymentNotYet,
            paymentDate = parsed.paidDate,
            correctedAmountRupees = correctedAmount,
            triggersDispute = effectiveDispute,
            disputeReason = disputeReason,
            summaryLine = summaryLine,
            workerResolution = workerRes
        )
    }

    fun formatCountdown(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val mins = safe / 60L
        val secs = safe % 60L
        return "%d:%02d".format(java.util.Locale.ENGLISH, mins, secs)
    }
}
