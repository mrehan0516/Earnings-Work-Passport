package com.example.domain.rules

import kotlin.math.abs

/**
 * Pure Kotlin Indian Rupee formatter and ISO date helper (no Android or Compose dependencies).
 * Always formats using Indian grouping: Rs 14,250 | Rs 1,24,500 | Rs 12,34,567.
 */
object RupeeFormatter {

    fun format(amountRupees: Long): String {
        val negative = amountRupees < 0
        val digits = abs(amountRupees).toString()
        val grouped = formatIndianGrouping(digits)
        return if (negative) "-Rs $grouped" else "Rs $grouped"
    }

    fun formatSignedDelta(deltaRupees: Long): String {
        return when {
            deltaRupees > 0 -> "+${format(deltaRupees)}"
            deltaRupees < 0 -> format(deltaRupees)
            else -> "Rs 0"
        }
    }

    fun formatIndianGrouping(digits: String): String {
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        val remaining = digits.dropLast(3)
        val pairs = StringBuilder()
        var i = remaining.length
        while (i > 0) {
            val start = maxOf(0, i - 2)
            if (pairs.isNotEmpty()) {
                pairs.insert(0, ',')
            }
            pairs.insert(0, remaining.substring(start, i))
            i = start
        }
        return "$pairs,$lastThree"
    }

    /**
     * Masks a bank/UTR reference so only the last 4 characters remain visible.
     */
    fun maskReference(reference: String): String {
        val trimmed = reference.trim()
        if (trimmed.isBlank()) return "Not provided"
        if (trimmed.length <= 4) return "••••"
        return "••••${trimmed.takeLast(4)}"
    }

    /**
     * Pure calendar conversion of YYYY-MM-DD into an ordinal day count (safe on Android API 24+).
     * Returns null if the string cannot be parsed.
     */
    fun parseIsoDayNumber(isoDate: String): Long? {
        val parts = isoDate.trim().split("-")
        if (parts.size != 3) return null
        val y = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val d = parts[2].toLongOrNull() ?: return null
        if (m !in 1..12 || d !in 1..31) return null
        // Howard Hinnant civil-from-days algorithm
        val adjY = if (m <= 2) y - 1 else y
        val era = (if (adjY >= 0) adjY else adjY - 399) / 400
        val yoe = adjY - era * 400
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    fun daysBetween(isoDateA: String, isoDateB: String): Long? {
        val a = parseIsoDayNumber(isoDateA) ?: return null
        val b = parseIsoDayNumber(isoDateB) ?: return null
        return abs(a - b)
    }
}
