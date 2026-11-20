package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.domain.rules.CounterpartyConfirmationRules
import com.example.domain.rules.ParsedCounterpartyResponses
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Strictly scoped public claim projection for an unauthenticated counterparty.
 * Contains ONLY who is asking, work description, amount, and dates for THIS single record.
 */
data class PublicConfirmationClaim(
    val token: String,
    val workerName: String,
    val partyName: String,
    val workDescription: String,
    val earnedDate: String,
    val periodLabel: String,
    val requestedAmountRupees: Long,
    val claimVersion: Int,
    val demoOtpCode: String,
    val expiresAtEpochMs: Long,
    val existingOtpAttempts: Int,
    val existingOtpLockedUntilEpochMs: Long
)

sealed interface PublicClaimLoadResult {
    data class Loaded(val claim: PublicConfirmationClaim) : PublicClaimLoadResult
    data class InvalidOrUnavailable(val message: String) : PublicClaimLoadResult
    data class Error(val message: String) : PublicClaimLoadResult
}

/**
 * Unauthenticated Firestore data access for the public counterparty Confirmation page.
 *
 * Zero-trust guarantees:
 * - Reads ONLY `confirmation_requests/{token}` (no listing, no access to other collections).
 * - Writes ONLY the 4 answer fields permitted by firestore.rules:
 *   `responses`, `status`, `responded_at`, `otp_attempts`.
 * - Always binds to the named Firestore database from `R.string.firestore_database_id`.
 */
class PublicConfirmationRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    suspend fun fetchPendingClaim(rawTokenOrLink: String): PublicClaimLoadResult {
        val token = CounterpartyConfirmationRules.extractTokenFromInput(rawTokenOrLink)
        if (token.length < CounterpartyConfirmationRules.MIN_TOKEN_LENGTH) {
            return PublicClaimLoadResult.InvalidOrUnavailable(
                "That confirmation link is incomplete or invalid. Check that you pasted the full link."
            )
        }

        return try {
            val snap = db.collection(PassportRepository.COL_CONFIRMATION_REQUESTS)
                .document(token)
                .get()
                .await()

            if (!snap.exists()) {
                return PublicClaimLoadResult.InvalidOrUnavailable(
                    "This confirmation link could not be found, has expired, or was already answered."
                )
            }

            val status = snap.getString("status").orEmpty()
            val expiresAtTs = snap.getTimestamp("expiresAt")
            val expiresAtMs = expiresAtTs?.toDate()?.time ?: 0L
            val nowMs = System.currentTimeMillis()

            if (status != "PENDING") {
                return PublicClaimLoadResult.InvalidOrUnavailable(
                    "This confirmation request has already been answered and is now closed."
                )
            }
            if (expiresAtMs <= 0L || CounterpartyConfirmationRules.isRequestExpired(expiresAtMs, nowMs)) {
                return PublicClaimLoadResult.InvalidOrUnavailable(
                    "This confirmation link expired after 7 days. Ask the worker to generate a new link."
                )
            }

            @Suppress("UNCHECKED_CAST")
            val rawResponses = snap.get("responses") as? Map<String, Any>
            val parsedResponses = CounterpartyConfirmationRules.parseResponses(rawResponses)

            @Suppress("UNCHECKED_CAST")
            val claimSnapshot = snap.get("claimSnapshot") as? Map<String, Any> ?: emptyMap()

            val workerName = (claimSnapshot["workerName"] as? String)
                ?.takeIf { it.isNotBlank() }
                ?: snap.getString("workerName").orEmpty().ifBlank { "Worker" }
            val workDescription = (claimSnapshot["workDescription"] as? String)
                ?.takeIf { it.isNotBlank() }
                ?: snap.getString("workDescription").orEmpty().ifBlank { "Work record" }
            val earnedDate = (claimSnapshot["earnedDate"] as? String)
                ?.takeIf { it.isNotBlank() }
                ?: snap.getString("earnedDate").orEmpty()
            val periodLabel = (claimSnapshot["periodLabel"] as? String)
                ?.takeIf { it.isNotBlank() }
                ?: snap.getString("periodLabel").orEmpty()
            val requestedAmount = (claimSnapshot["requestedAmountRupees"] as? Number)?.toLong()
                ?: snap.getLong("requestedAmountRupees")
                ?: 0L
            val claimVersion = (claimSnapshot["claimVersion"] as? Number)?.toInt()
                ?: snap.getLong("claimVersion")?.toInt()
                ?: 1

            val demoOtp = snap.getString("demoOtpCode")
                ?.takeIf { it.length == 6 && it.all(Char::isDigit) }
                ?: CounterpartyConfirmationRules.generateDemoOtpCode()

            val otpAttempts = snap.getLong("otp_attempts")?.toInt()?.coerceAtLeast(0) ?: 0

            PublicClaimLoadResult.Loaded(
                PublicConfirmationClaim(
                    token = token,
                    workerName = workerName,
                    partyName = snap.getString("partyName").orEmpty(),
                    workDescription = workDescription,
                    earnedDate = earnedDate,
                    periodLabel = periodLabel,
                    requestedAmountRupees = requestedAmount,
                    claimVersion = claimVersion.coerceAtLeast(1),
                    demoOtpCode = demoOtp,
                    expiresAtEpochMs = expiresAtMs,
                    existingOtpAttempts = otpAttempts,
                    existingOtpLockedUntilEpochMs = parsedResponses.otpLockedUntilEpochMs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "Public confirmation fetch blocked/failed: ${e.code}", e)
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                e.code == FirebaseFirestoreException.Code.NOT_FOUND
            ) {
                PublicClaimLoadResult.InvalidOrUnavailable(
                    "This confirmation link is expired, already answered, or invalid."
                )
            } else {
                PublicClaimLoadResult.Error(
                    "Could not reach the confirmation service (${e.code}). Check your connection and try again."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error loading public confirmation", e)
            PublicClaimLoadResult.Error(e.message ?: "Could not load confirmation request.")
        }
    }

    /**
     * Persists failed DEMO OTP attempts and lockout state while keeping status = PENDING.
     * Touches ONLY the publicly permitted fields (`otp_attempts`, `responses`).
     */
    suspend fun recordOtpAttempt(
        token: String,
        totalAttempts: Int,
        lockedUntilEpochMs: Long
    ): Result<Unit> {
        return try {
            val payload = linkedMapOf<String, Any>(
                "otp_attempts" to totalAttempts.coerceIn(0, 100),
                "responses" to mapOf(
                    "otpLockedUntilEpochMs" to lockedUntilEpochMs.coerceAtLeast(0L)
                )
            )
            db.collection(PassportRepository.COL_CONFIRMATION_REQUESTS)
                .document(token)
                .update(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist otp_attempts", e)
            Result.failure(e)
        }
    }

    /**
     * Submits the counterparty's separate answers with server timestamp and claim version shown.
     * Touches ONLY the 4 whitelisted fields: `responses`, `status`, `responded_at`, `otp_attempts`.
     */
    suspend fun submitCounterpartyResponse(
        token: String,
        responses: ParsedCounterpartyResponses,
        totalOtpAttempts: Int
    ): Result<Unit> {
        return try {
            val nextStatus = if (responses.isAllDeclined()) "DECLINED" else "RESPONDED"
            val payload = linkedMapOf<String, Any>(
                "responses" to responses.toFirestoreMap(),
                "status" to nextStatus,
                "responded_at" to FieldValue.serverTimestamp(),
                "otp_attempts" to totalOtpAttempts.coerceIn(0, 100)
            )
            db.collection(PassportRepository.COL_CONFIRMATION_REQUESTS)
                .document(token)
                .update(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit counterparty confirmation response", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "PublicConfirmationRepo"
    }
}
