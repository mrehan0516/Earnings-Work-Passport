package com.example.domain.rules

import kotlin.math.roundToLong

data class ReviewFormValues(
    val title: String,
    val platform: String,
    val payer: String,
    val clientName: String,
    val invoiceNumber: String,
    val recordDate: String,
    val periodStart: String,
    val periodEnd: String,
    val grossAmountText: String,
    val deductionsText: String,
    val netAmountText: String,
    val invoiceAmountText: String,
    val reference: String,
    val notes: String
) {
    /**
     * Resolves the display party name from whichever of platform / payer / clientName was entered.
     */
    fun resolvedPartyName(): String {
        return listOf(platform, payer, clientName)
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    /**
     * Resolves the primary confirmed Rupee amount for the ledger from net -> invoice -> gross,
     * without silently recalculating or modifying mismatched values.
     */
    fun resolvedConfirmedAmountRupees(): Long {
        val candidate = parsePlainDouble(netAmountText)
            ?: parsePlainDouble(invoiceAmountText)
            ?: parsePlainDouble(grossAmountText)
            ?: return 0L
        return if (candidate > 0.0) candidate.roundToLong() else 0L
    }

    fun resolvedPeriodLabel(): String {
        val start = periodStart.trim()
        val end = periodEnd.trim()
        return when {
            start.isNotBlank() && end.isNotBlank() -> "$start to $end"
            start.isNotBlank() -> start
            end.isNotBlank() -> end
            else -> ""
        }
    }

    fun resolvedReference(): String {
        return listOf(reference, invoiceNumber)
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    fun toRawExtraction(original: RawDocumentExtraction?): RawDocumentExtraction {
        return RawDocumentExtraction(
            documentType = original?.documentType,
            platform = platform.trim().ifBlank { null },
            payer = payer.trim().ifBlank { null },
            clientName = clientName.trim().ifBlank { null },
            invoiceNumber = invoiceNumber.trim().ifBlank { null },
            recordDate = recordDate.trim().ifBlank { null },
            periodStart = periodStart.trim().ifBlank { null },
            periodEnd = periodEnd.trim().ifBlank { null },
            grossAmountRaw = grossAmountText.trim().ifBlank { null },
            deductionsRaw = deductionsText.trim().ifBlank { null },
            netAmountRaw = netAmountText.trim().ifBlank { null },
            invoiceAmountRaw = invoiceAmountText.trim().ifBlank { null },
            currency = original?.currency,
            reference = reference.trim().ifBlank { null },
            paymentStatusStated = original?.paymentStatusStated,
            notes = notes.trim().ifBlank { null }
        )
    }

    companion object {
        fun parsePlainDouble(text: String): Double? {
            val clean = text.trim()
            if (clean.isEmpty()) return null
            val v = clean.toDoubleOrNull() ?: return null
            return if (v.isFinite()) v else null
        }

        fun parseRupeesOrZero(text: String): Long {
            val d = parsePlainDouble(text) ?: return 0L
            return d.roundToLong().coerceIn(0L, 100_000_000L)
        }
    }
}

data class EditedFieldChange(
    val fieldKey: String,
    val fieldLabel: String,
    val aiValue: String,
    val confirmedValue: String
)

data class AiUncertaintySummary(
    val missingFieldLabels: List<String>,
    val aiNotes: String
) {
    val hasUncertainty: Boolean
        get() = missingFieldLabels.isNotEmpty() || aiNotes.isNotBlank()
}

/**
 * Pure-Kotlin business rules for Review & Confirm:
 * - Builds "AI was unsure" summaries (null fields + AI uncertainty notes)
 * - Serializes the exact AI extraction into a bounded, immutable Firestore-ready `aiOriginal` map
 * - Computes deterministic field-by-field worker edits for the `audit_log`
 * - Runs live code-level validation without silently changing numbers
 */
object ReviewConfirmRules {

    fun summarizeAiUncertainty(raw: RawDocumentExtraction?): AiUncertaintySummary {
        if (raw == null) {
            return AiUncertaintySummary(emptyList(), "")
        }
        val missing = mutableListOf<String>()
        if (raw.platform.isNullOrBlank() && raw.payer.isNullOrBlank() && raw.clientName.isNullOrBlank()) {
            missing.add("Platform / Payer / Client")
        }
        if (raw.recordDate.isNullOrBlank()) missing.add("Record date")
        if (raw.periodStart.isNullOrBlank() && raw.periodEnd.isNullOrBlank()) missing.add("Period")
        if (raw.grossAmountRaw.isNullOrBlank() &&
            raw.netAmountRaw.isNullOrBlank() &&
            raw.invoiceAmountRaw.isNullOrBlank()
        ) {
            missing.add("Amount")
        }
        if (raw.reference.isNullOrBlank() && raw.invoiceNumber.isNullOrBlank()) {
            missing.add("Reference / Invoice #")
        }
        return AiUncertaintySummary(
            missingFieldLabels = missing,
            aiNotes = raw.notes?.trim().orEmpty()
        )
    }

    /**
     * Converts the AI's original 16-field reading into a Firestore-safe, bounded map
     * using exact snake_case keys matching the Gemini schema. Null fields are omitted
     * cleanly (or represented only when present) so Firestore rules stay strict and clean.
     */
    fun buildAiOriginalMap(raw: RawDocumentExtraction?): Map<String, Any> {
        if (raw == null) return emptyMap()
        val map = linkedMapOf<String, Any>()
        fun putStr(key: String, value: String?, maxLen: Int = 120) {
            val clean = value?.trim().orEmpty()
            if (clean.isNotEmpty()) map[key] = clean.take(maxLen)
        }
        putStr("document_type", raw.documentType, 60)
        putStr("platform", raw.platform, 100)
        putStr("payer", raw.payer, 100)
        putStr("client_name", raw.clientName, 100)
        putStr("invoice_number", raw.invoiceNumber, 80)
        putStr("record_date", raw.recordDate, 30)
        putStr("period_start", raw.periodStart, 30)
        putStr("period_end", raw.periodEnd, 30)
        putStr("gross_amount", raw.grossAmountRaw, 30)
        putStr("deductions", raw.deductionsRaw, 30)
        putStr("net_amount", raw.netAmountRaw, 30)
        putStr("invoice_amount", raw.invoiceAmountRaw, 30)
        putStr("currency", raw.currency, 20)
        putStr("reference", raw.reference, 80)
        putStr("payment_status_stated", raw.paymentStatusStated, 60)
        putStr("notes", raw.notes, 300)
        return map
    }

    /**
     * Compares the worker's confirmed edits against the AI's original reading to produce
     * an exact list of edited fields for the `audit_log` edit entry.
     */
    fun computeWorkerEdits(
        aiOriginal: RawDocumentExtraction?,
        confirmed: ReviewFormValues
    ): List<EditedFieldChange> {
        if (aiOriginal == null) return emptyList()
        val changes = mutableListOf<EditedFieldChange>()

        fun compare(key: String, label: String, original: String?, current: String) {
            val origNorm = original?.trim().orEmpty()
            val currNorm = current.trim()
            if (!origNorm.equals(currNorm, ignoreCase = false)) {
                changes.add(
                    EditedFieldChange(
                        fieldKey = key,
                        fieldLabel = label,
                        aiValue = origNorm.ifBlank { "null" },
                        confirmedValue = currNorm.ifBlank { "empty" }
                    )
                )
            }
        }

        compare("platform", "Platform", aiOriginal.platform, confirmed.platform)
        compare("payer", "Payer", aiOriginal.payer, confirmed.payer)
        compare("client_name", "Client", aiOriginal.clientName, confirmed.clientName)
        compare("invoice_number", "Invoice #", aiOriginal.invoiceNumber, confirmed.invoiceNumber)
        compare("record_date", "Record date", aiOriginal.recordDate, confirmed.recordDate)
        compare("period_start", "Period start", aiOriginal.periodStart, confirmed.periodStart)
        compare("period_end", "Period end", aiOriginal.periodEnd, confirmed.periodEnd)
        compare("gross_amount", "Gross", aiOriginal.grossAmountRaw, confirmed.grossAmountText)
        compare("deductions", "Deductions", aiOriginal.deductionsRaw, confirmed.deductionsText)
        compare("net_amount", "Net", aiOriginal.netAmountRaw, confirmed.netAmountText)
        compare("invoice_amount", "Invoice amount", aiOriginal.invoiceAmountRaw, confirmed.invoiceAmountText)
        compare("reference", "Reference", aiOriginal.reference, confirmed.reference)
        return changes
    }

    fun formatEditAuditSummary(edits: List<EditedFieldChange>): String {
        if (edits.isEmpty()) {
            return "Worker reviewed AI values and confirmed without edits."
        }
        val details = edits.joinToString("; ") { "${it.fieldLabel}: ${it.aiValue} -> ${it.confirmedValue}" }
        return "Worker edited ${edits.size} field(s) before confirming: $details".take(300)
    }

    fun validateEditedForm(
        original: RawDocumentExtraction?,
        current: ReviewFormValues
    ): ValidatedDocumentExtraction {
        return DocumentExtractionValidator.validate(current.toRawExtraction(original))
    }
}
