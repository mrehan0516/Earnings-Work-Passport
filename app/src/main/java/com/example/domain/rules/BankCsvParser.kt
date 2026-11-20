package com.example.domain.rules

import java.util.Locale
import kotlin.math.roundToLong

data class ParsedBankCsvCredit(
    val date: String,
    val amountRupees: Long,
    val descriptor: String,
    val utrReference: String
)

data class BankCsvParseResult(
    val credits: List<ParsedBankCsvCredit>,
    val skippedDebitOrEmptyRows: Int,
    val rowErrors: List<String>,
    val bankLabelHint: String
)

/**
 * Pure-Kotlin bank statement CSV parser (NO AI).
 * Parses credit rows into structured (date, amount, descriptor) entries for `bank_credits`.
 */
object BankCsvParser {

    private val MONTH_MAP = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
        "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    private val REF_TOKEN_REGEX = Regex("\\b((?:UTR|UPI|IMPS|NEFT|SWG|BLK|ZMT|REF)[A-Z0-9\\-]{5,24})\\b", RegexOption.IGNORE_CASE)

    fun parse(csvContent: String, fileName: String = ""): BankCsvParseResult {
        val cleaned = csvContent.removePrefix("\uFEFF")
        val rawLines = cleaned
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")
            .filter { it.isNotBlank() }

        if (rawLines.isEmpty()) {
            return BankCsvParseResult(
                credits = emptyList(),
                skippedDebitOrEmptyRows = 0,
                rowErrors = listOf("The CSV file is empty."),
                bankLabelHint = deriveBankLabel(fileName)
            )
        }

        // Locate the header row (some Indian bank CSVs have 1-5 preamble metadata lines before the header)
        var headerIndex = -1
        var headerColumns: List<String> = emptyList()
        var dateCol = -1
        var creditCol = -1
        var amountCol = -1
        var debitCol = -1
        var typeCol = -1
        var descriptorCol = -1
        var refCol = -1

        for (i in 0 until minOf(rawLines.size, 15)) {
            val cols = parseCsvLine(rawLines[i]).map { normalizeHeader(it) }
            val dIdx = findColumn(cols, listOf("date", "txndate", "transactiondate", "valuedate", "creditdate", "postingdate"))
            val descIdx = findColumn(cols, listOf("descriptor", "description", "narration", "particulars", "remarks", "details", "memo"))
            val crIdx = findColumn(cols, listOf("credit", "creditamount", "cr", "deposit", "depositamt", "depositamount", "creditinr"))
            val amtIdx = findColumn(cols, listOf("amount", "amountinr", "amountrs", "txnamount", "transactionamount", "value"))

            if (dIdx >= 0 && (crIdx >= 0 || amtIdx >= 0) && descIdx >= 0) {
                headerIndex = i
                headerColumns = cols
                dateCol = dIdx
                creditCol = crIdx
                amountCol = amtIdx
                debitCol = findColumn(cols, listOf("debit", "debitamount", "dr", "withdrawal", "withdrawalamt"))
                typeCol = findColumn(cols, listOf("type", "drcr", "crdr", "transactiontype", "txntype"))
                descriptorCol = descIdx
                refCol = findColumn(cols, listOf("utr", "reference", "ref", "refno", "referenceno", "chequeref", "chqrefno", "utrreference"))
                break
            }
        }

        if (headerIndex < 0) {
            return BankCsvParseResult(
                credits = emptyList(),
                skippedDebitOrEmptyRows = 0,
                rowErrors = listOf(
                    "Could not find required CSV columns. Expected columns for Date, Amount (or Credit), and Descriptor (or Narration/Description)."
                ),
                bankLabelHint = deriveBankLabel(fileName)
            )
        }

        val credits = mutableListOf<ParsedBankCsvCredit>()
        val rowErrors = mutableListOf<String>()
        var skipped = 0

        for (lineIdx in (headerIndex + 1) until rawLines.size) {
            val rowNumber = lineIdx + 1
            val cells = parseCsvLine(rawLines[lineIdx])
            if (cells.all { it.isBlank() }) {
                skipped++
                continue
            }

            val rawDate = cells.getOrNull(dateCol)?.trim().orEmpty()
            val rawDescriptor = cells.getOrNull(descriptorCol)?.trim().orEmpty()
            val rawRef = if (refCol >= 0) cells.getOrNull(refCol)?.trim().orEmpty() else ""

            // Determine whether this row is a debit (skip cleanly) or a credit
            if (typeCol >= 0) {
                val rowType = cells.getOrNull(typeCol)?.trim()?.uppercase(Locale.US).orEmpty()
                if (rowType == "DR" || rowType == "DEBIT" || rowType == "WD" || rowType == "WITHDRAWAL") {
                    skipped++
                    continue
                }
            }

            val rawAmountStr: String
            if (creditCol >= 0) {
                val crVal = cells.getOrNull(creditCol)?.trim().orEmpty()
                val drVal = if (debitCol >= 0) cells.getOrNull(debitCol)?.trim().orEmpty() else ""
                val parsedCr = parseAmountNumber(crVal)
                if (crVal.isBlank() || crVal == "-" || (parsedCr != null && parsedCr == 0.0)) {
                    // Debit-only row or zero-credit row
                    if (drVal.isNotBlank()) {
                        skipped++
                        continue
                    }
                    skipped++
                    continue
                }
                rawAmountStr = crVal
            } else {
                rawAmountStr = cells.getOrNull(amountCol)?.trim().orEmpty()
                val signedAmount = parseAmountNumber(rawAmountStr)
                if (signedAmount != null && signedAmount < 0.0) {
                    // Negative amount in single-column bank CSV is a debit -> skip
                    skipped++
                    continue
                }
            }

            val normalizedDate = normalizeToIsoDate(rawDate)
            if (normalizedDate == null) {
                rowErrors.add("Row $rowNumber: invalid date '$rawDate'")
                continue
            }

            val parsedAmount = parseAmountNumber(rawAmountStr)
            if (parsedAmount == null || parsedAmount <= 0.0) {
                rowErrors.add("Row $rowNumber: invalid credit amount '$rawAmountStr'")
                continue
            }

            val roundedRupees = parsedAmount.roundToLong()
            if (roundedRupees <= 0L || roundedRupees > 100_000_000L) {
                rowErrors.add("Row $rowNumber: amount out of valid range '$rawAmountStr'")
                continue
            }

            val descriptor = rawDescriptor.ifBlank { "Bank credit ($normalizedDate)" }.take(200)
            val utr = rawRef.ifBlank { extractRefFromDescriptor(descriptor) }.take(80)

            credits.add(
                ParsedBankCsvCredit(
                    date = normalizedDate,
                    amountRupees = roundedRupees,
                    descriptor = descriptor,
                    utrReference = utr
                )
            )
        }

        if (credits.isEmpty() && rowErrors.isEmpty()) {
            rowErrors.add("No positive credit rows were found in this CSV file.")
        }

        return BankCsvParseResult(
            credits = credits,
            skippedDebitOrEmptyRows = skipped,
            rowErrors = rowErrors,
            bankLabelHint = deriveBankLabel(fileName)
        )
    }

    /**
     * RFC-4180 compliant single-line CSV tokenizer handling quoted commas and escaped quotes.
     */
    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    fun normalizeToIsoDate(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank()) return null

        // 1. YYYY-MM-DD
        if (DocumentExtractionValidator.isValidStrictIsoDate(value)) {
            return value
        }

        // 2. DD/MM/YYYY or DD-MM-YYYY
        val dmyMatch = Regex("^(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{4})$").matchEntire(value)
        if (dmyMatch != null) {
            val day = dmyMatch.groupValues[1].toIntOrNull() ?: return null
            val month = dmyMatch.groupValues[2].toIntOrNull() ?: return null
            val year = dmyMatch.groupValues[3].toIntOrNull() ?: return null
            val iso = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
            return if (DocumentExtractionValidator.isValidStrictIsoDate(iso)) iso else null
        }

        // 3. DD MMM YYYY or DD-MMM-YYYY (common in HDFC/SBI/ICICI CSV exports)
        val dMonYMatch = Regex("^(\\d{1,2})[\\s\\-]([A-Za-z]{3,4})[\\s\\-](\\d{2,4})$").matchEntire(value)
        if (dMonYMatch != null) {
            val day = dMonYMatch.groupValues[1].toIntOrNull() ?: return null
            val monKey = dMonYMatch.groupValues[2].lowercase(Locale.US)
            val month = MONTH_MAP[monKey] ?: return null
            val rawYear = dMonYMatch.groupValues[3].toIntOrNull() ?: return null
            val year = if (rawYear < 100) 2000 + rawYear else rawYear
            val iso = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
            return if (DocumentExtractionValidator.isValidStrictIsoDate(iso)) iso else null
        }

        return null
    }

    fun parseAmountNumber(raw: String): Double? {
        val cleaned = raw
            .replace("₹", "")
            .replace("INR", "", ignoreCase = true)
            .replace("Rs.", "", ignoreCase = true)
            .replace("Rs", "", ignoreCase = true)
            .replace(",", "")
            .replace(" ", "")
            .trim()
        if (cleaned.isBlank()) return null
        val num = cleaned.toDoubleOrNull() ?: return null
        return if (num.isFinite()) num else null
    }

    private fun normalizeHeader(raw: String): String =
        raw.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    private fun findColumn(headers: List<String>, candidates: List<String>): Int {
        for (candidate in candidates) {
            val exact = headers.indexOfFirst { it == candidate }
            if (exact >= 0) return exact
        }
        for (candidate in candidates) {
            val partial = headers.indexOfFirst { it.contains(candidate) }
            if (partial >= 0) return partial
        }
        return -1
    }

    private fun extractRefFromDescriptor(descriptor: String): String {
        return REF_TOKEN_REGEX.find(descriptor)?.groupValues?.getOrNull(1)?.uppercase(Locale.US) ?: ""
    }

    private fun deriveBankLabel(fileName: String): String {
        val stem = fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()
        return if (stem.isNotBlank()) "CSV: ${stem.take(60)}" else "Bank Statement CSV"
    }
}
