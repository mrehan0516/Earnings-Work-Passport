package com.example.domain.rules

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Exact 16-field JSON payload returned by Gemini document extraction.
 * Every numeric field is preserved verbatim as a raw string/value before validation
 * so rules can report parse failures without silently fixing numbers.
 */
data class RawDocumentExtraction(
    val documentType: String? = null,
    val platform: String? = null,
    val payer: String? = null,
    val clientName: String? = null,
    val invoiceNumber: String? = null,
    val recordDate: String? = null,
    val periodStart: String? = null,
    val periodEnd: String? = null,
    val grossAmountRaw: String? = null,
    val deductionsRaw: String? = null,
    val netAmountRaw: String? = null,
    val invoiceAmountRaw: String? = null,
    val currency: String? = null,
    val reference: String? = null,
    val paymentStatusStated: String? = null,
    val notes: String? = null
)

enum class ValidationFlagCode {
    UNPARSEABLE_NUMBER,
    NON_POSITIVE_AMOUNT,
    INVALID_DATE,
    INVALID_DATE_RANGE,
    NUMBERS_DO_NOT_ADD_UP
}

data class ExtractionValidationFlag(
    val code: ValidationFlagCode,
    val title: String,
    val detail: String
)

data class ValidatedDocumentExtraction(
    val raw: RawDocumentExtraction,
    val grossAmount: Double?,
    val deductions: Double?,
    val netAmount: Double?,
    val invoiceAmount: Double?,
    val recordDateValid: Boolean,
    val periodStartValid: Boolean,
    val periodEndValid: Boolean,
    val flags: List<ExtractionValidationFlag>
) {
    val hasFlags: Boolean get() = flags.isNotEmpty()
    val numbersDoNotAddUp: Boolean
        get() = flags.any { it.code == ValidationFlagCode.NUMBERS_DO_NOT_ADD_UP }

    /**
     * Picks the printed primary amount for display in the Review & Confirm input box
     * WITHOUT calculating or silently altering mismatched numbers.
     */
    fun suggestedDisplayRupees(): Long {
        val candidate = netAmount ?: invoiceAmount ?: grossAmount ?: return 0L
        return if (candidate > 0.0) candidate.roundToLong() else 0L
    }

    fun suggestedPartyName(): String {
        return listOfNotNull(raw.platform, raw.payer, raw.clientName)
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    fun suggestedReference(): String {
        return listOfNotNull(raw.reference, raw.invoiceNumber)
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    fun suggestedPeriodLabel(): String {
        val start = raw.periodStart?.trim().orEmpty()
        val end = raw.periodEnd?.trim().orEmpty()
        return when {
            start.isNotBlank() && end.isNotBlank() -> "$start to $end"
            start.isNotBlank() -> start
            end.isNotBlank() -> end
            else -> ""
        }
    }
}

/**
 * Pure-Kotlin code validation for Gemini document extractions.
 *
 * Rules:
 * - Numbers must parse cleanly as finite numbers.
 * - Dates must be strict, real calendar dates in YYYY-MM-DD format.
 * - Amounts must be positive (deductions >= 0, earnings/invoice amounts > 0).
 * - When gross, deductions, and net are present: abs(gross - deductions - net) <= 1,
 *   else flag "Numbers do not add up".
 * - NEVER fix numbers silently.
 */
object DocumentExtractionValidator {

    const val NUMBERS_MISMATCH_FLAG_TITLE = "Numbers do not add up"
    private val STRICT_ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun validate(raw: RawDocumentExtraction): ValidatedDocumentExtraction {
        val flags = mutableListOf<ExtractionValidationFlag>()

        val gross = parseAndValidateAmount(
            label = "Gross amount",
            rawText = raw.grossAmountRaw,
            allowZero = false,
            flags = flags
        )
        val deductions = parseAndValidateAmount(
            label = "Deductions",
            rawText = raw.deductionsRaw,
            allowZero = true,
            flags = flags
        )
        val net = parseAndValidateAmount(
            label = "Net amount",
            rawText = raw.netAmountRaw,
            allowZero = false,
            flags = flags
        )
        val invoice = parseAndValidateAmount(
            label = "Invoice amount",
            rawText = raw.invoiceAmountRaw,
            allowZero = false,
            flags = flags
        )

        val recordDateOk = validateOptionalDate("Record date", raw.recordDate, flags)
        val periodStartOk = validateOptionalDate("Period start", raw.periodStart, flags)
        val periodEndOk = validateOptionalDate("Period end", raw.periodEnd, flags)

        if (periodStartOk && periodEndOk &&
            !raw.periodStart.isNullOrBlank() &&
            !raw.periodEnd.isNullOrBlank()
        ) {
            val startDay = RupeeFormatter.parseIsoDayNumber(raw.periodStart)
            val endDay = RupeeFormatter.parseIsoDayNumber(raw.periodEnd)
            if (startDay != null && endDay != null && startDay > endDay) {
                flags.add(
                    ExtractionValidationFlag(
                        code = ValidationFlagCode.INVALID_DATE_RANGE,
                        title = "Invalid period range",
                        detail = "Period start (${raw.periodStart}) is after period end (${raw.periodEnd})."
                    )
                )
            }
        }

        // Exact math consistency rule: abs(gross - deductions - net) <= 1
        // Never recalculate or overwrite any field silently!
        if (gross != null && deductions != null && net != null) {
            val diff = abs(gross - deductions - net)
            if (diff > 1.0) {
                flags.add(
                    ExtractionValidationFlag(
                        code = ValidationFlagCode.NUMBERS_DO_NOT_ADD_UP,
                        title = NUMBERS_MISMATCH_FLAG_TITLE,
                        detail = "Printed gross (${formatNumber(gross)}) minus deductions (${formatNumber(deductions)}) does not equal printed net (${formatNumber(net)}). Values were kept as printed — please check and edit manually."
                    )
                )
            }
        } else if (gross != null && deductions == null && net != null && net > gross + 1.0) {
            flags.add(
                ExtractionValidationFlag(
                    code = ValidationFlagCode.NUMBERS_DO_NOT_ADD_UP,
                    title = NUMBERS_MISMATCH_FLAG_TITLE,
                    detail = "Printed net (${formatNumber(net)}) is greater than printed gross (${formatNumber(gross)}). Values were kept as printed — please check carefully."
                )
            )
        }

        return ValidatedDocumentExtraction(
            raw = raw,
            grossAmount = gross,
            deductions = deductions,
            netAmount = net,
            invoiceAmount = invoice,
            recordDateValid = recordDateOk,
            periodStartValid = periodStartOk,
            periodEndValid = periodEndOk,
            flags = flags
        )
    }

    fun isValidStrictIsoDate(value: String): Boolean {
        val trimmed = value.trim()
        if (!STRICT_ISO_DATE_REGEX.matches(trimmed)) return false
        val parts = trimmed.split("-")
        val year = parts[0].toIntOrNull() ?: return false
        val month = parts[1].toIntOrNull() ?: return false
        val day = parts[2].toIntOrNull() ?: return false
        if (year !in 1900..2100) return false
        if (month !in 1..12) return false
        val maxDay = daysInMonth(year, month)
        return day in 1..maxDay
    }

    private fun validateOptionalDate(
        label: String,
        rawDate: String?,
        flags: MutableList<ExtractionValidationFlag>
    ): Boolean {
        if (rawDate.isNullOrBlank()) return true
        val ok = isValidStrictIsoDate(rawDate)
        if (!ok) {
            flags.add(
                ExtractionValidationFlag(
                    code = ValidationFlagCode.INVALID_DATE,
                    title = "Invalid date format",
                    detail = "$label '$rawDate' is not a valid YYYY-MM-DD date. Please review and correct it."
                )
            )
        }
        return ok
    }

    private fun parseAndValidateAmount(
        label: String,
        rawText: String?,
        allowZero: Boolean,
        flags: MutableList<ExtractionValidationFlag>
    ): Double? {
        if (rawText.isNullOrBlank()) return null
        val trimmed = rawText.trim()
        val parsed = trimmed.toDoubleOrNull()
        if (parsed == null || !parsed.isFinite()) {
            flags.add(
                ExtractionValidationFlag(
                    code = ValidationFlagCode.UNPARSEABLE_NUMBER,
                    title = "Number could not be read",
                    detail = "$label '$trimmed' is not a plain number. Please enter the printed value manually."
                )
            )
            return null
        }
        val invalidSign = if (allowZero) parsed < 0.0 else parsed <= 0.0
        if (invalidSign) {
            flags.add(
                ExtractionValidationFlag(
                    code = ValidationFlagCode.NON_POSITIVE_AMOUNT,
                    title = "Amount is not positive",
                    detail = "$label was read as $trimmed, which is not a valid positive amount. Value was not changed silently."
                )
            )
        }
        return parsed
    }

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
        else -> 0
    }

    private fun formatNumber(value: Double): String {
        val rounded = value.roundToLong()
        return if (abs(value - rounded.toDouble()) < 0.005) {
            RupeeFormatter.format(rounded)
        } else {
            "Rs ${"%.2f".format(java.util.Locale.US, value)}"
        }
    }
}
