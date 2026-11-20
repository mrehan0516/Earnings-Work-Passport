package com.example.model

/**
 * The five strict evidence tiers for every Rupee figure in Earnings & Work Passport.
 * Color is reserved exclusively for these five labels, and the label text is always shown.
 */
enum class EvidenceStatus(
    val code: String,
    val label: String,
    val shortExplanation: String,
    val sortRank: Int
) {
    SOURCE_VERIFIED_SIMULATED(
        code = "SOURCE_VERIFIED_SIMULATED",
        label = "Source-verified (Simulated)",
        shortExplanation = "Imported from a simulated platform API feed (always labeled Simulated).",
        sortRank = 1
    ),
    SOURCE_MATCHED(
        code = "SOURCE_MATCHED",
        label = "Source-matched",
        shortExplanation = "Matched to a bank or UPI credit by transparent reconciliation rules.",
        sortRank = 2
    ),
    DOCUMENT_EXTRACTED(
        code = "DOCUMENT_EXTRACTED",
        label = "Document-extracted",
        shortExplanation = "Read from an uploaded document by AI and confirmed by the worker.",
        sortRank = 3
    ),
    SELF_DECLARED(
        code = "SELF_DECLARED",
        label = "Self-declared",
        shortExplanation = "Entered directly by the worker without a linked document or bank match.",
        sortRank = 4
    ),
    DISPUTED(
        code = "DISPUTED",
        label = "Disputed",
        shortExplanation = "Flagged as disputed by the worker or paying source and needs resolution.",
        sortRank = 5
    );

    companion object {
        fun fromCode(raw: String?): EvidenceStatus {
            return entries.firstOrNull { it.code == raw } ?: SELF_DECLARED
        }
    }
}
