package com.example.domain.rules

import com.example.model.DocumentType
import com.example.model.IncomeDocument
import java.security.MessageDigest
import java.util.Locale

enum class SourceUploadType(
    val code: String,
    val label: String,
    val helperText: String,
    val documentType: DocumentType
) {
    PLATFORM_STATEMENT(
        code = "PLATFORM_STATEMENT",
        label = "Platform statement",
        helperText = "Upload a PDF or image of your weekly or monthly app payout summary. AI reads candidate values for you to confirm.",
        documentType = DocumentType.PLATFORM_STATEMENT
    ),
    BANK_STATEMENT_CSV(
        code = "BANK_STATEMENT_CSV",
        label = "Bank statement (CSV)",
        helperText = "Upload a bank statement .csv file. Parsed directly in code (no AI) into bank credits for matching rules.",
        documentType = DocumentType.BANK_STATEMENT_CSV
    ),
    INVOICE(
        code = "INVOICE",
        label = "Invoice",
        helperText = "Upload a PDF or photo of a client invoice or tuition bill. AI reads printed values without guessing.",
        documentType = DocumentType.INVOICE
    ),
    CASH_NOTE(
        code = "CASH_NOTE",
        label = "Cash note",
        helperText = "Upload a PDF or photo of a handwritten or printed cash receipt note. You review every field before saving.",
        documentType = DocumentType.CASH_NOTE
    );

    companion object {
        fun fromCode(raw: String?): SourceUploadType =
            entries.firstOrNull { it.code == raw } ?: PLATFORM_STATEMENT
    }
}

enum class UploadFileKind {
    PDF,
    CSV,
    IMAGE
}

sealed interface FileValidationResult {
    data class Valid(
        val fileKind: UploadFileKind,
        val normalizedMimeType: String,
        val effectiveSourceType: SourceUploadType
    ) : FileValidationResult

    data class Invalid(val message: String) : FileValidationResult
}

/**
 * Pure-Kotlin business rules for validating uploaded files (type, max 10 MB size) and
 * computing deterministic SHA-256 content hashes for per-worker duplicate file detection.
 */
object FileIngestionValidator {

    const val MAX_UPLOAD_BYTES: Long = 10L * 1024L * 1024L // 10 MB
    const val DUPLICATE_FILE_ERROR: String = "This file is already added"

    private val IMAGE_MIME_TYPES = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp",
        "image/heic",
        "image/heif",
        "image/gif"
    )

    private val CSV_MIME_TYPES = setOf(
        "text/csv",
        "text/comma-separated-values",
        "application/csv",
        "application/x-csv",
        "text/x-csv",
        "text/x-comma-separated-values"
    )

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")

    fun validateFile(
        fileName: String,
        mimeType: String?,
        sizeBytes: Long,
        selectedSourceType: SourceUploadType
    ): FileValidationResult {
        if (sizeBytes <= 0L) {
            return FileValidationResult.Invalid(
                "This file appears to be empty. Please choose a valid PDF, CSV, or image file."
            )
        }
        if (sizeBytes > MAX_UPLOAD_BYTES) {
            val sizeMb = String.format(Locale.US, "%.1f", sizeBytes / (1024.0 * 1024.0))
            return FileValidationResult.Invalid(
                "This file is $sizeMb MB, which is over the 10 MB limit. Please upload a smaller PDF, CSV, or image."
            )
        }

        val ext = fileName.substringAfterLast('.', "").trim().lowercase(Locale.US)
        val cleanMime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.US).orEmpty()

        val detectedKind: UploadFileKind? = when {
            cleanMime == "application/pdf" || ext == "pdf" -> UploadFileKind.PDF
            cleanMime in CSV_MIME_TYPES || ext == "csv" -> UploadFileKind.CSV
            cleanMime in IMAGE_MIME_TYPES || cleanMime.startsWith("image/") || ext in IMAGE_EXTENSIONS -> UploadFileKind.IMAGE
            else -> null
        }

        if (detectedKind == null) {
            return FileValidationResult.Invalid(
                "Unsupported file type. Please upload a PDF, CSV, or image (JPG, PNG, or WEBP) up to 10 MB."
            )
        }

        if (selectedSourceType == SourceUploadType.BANK_STATEMENT_CSV && detectedKind != UploadFileKind.CSV) {
            return FileValidationResult.Invalid(
                "Bank statement import expects a .csv file. Upload a CSV file, or switch source type to Platform statement, Invoice, or Cash note for PDFs and images."
            )
        }

        val normalizedMime = when (detectedKind) {
            UploadFileKind.PDF -> "application/pdf"
            UploadFileKind.CSV -> "text/csv"
            UploadFileKind.IMAGE -> when {
                cleanMime.startsWith("image/") -> cleanMime
                ext == "png" -> "image/png"
                ext == "webp" -> "image/webp"
                else -> "image/jpeg"
            }
        }

        val effectiveSourceType = if (detectedKind == UploadFileKind.CSV) {
            SourceUploadType.BANK_STATEMENT_CSV
        } else {
            selectedSourceType
        }

        return FileValidationResult.Valid(
            fileKind = detectedKind,
            normalizedMimeType = normalizedMime,
            effectiveSourceType = effectiveSourceType
        )
    }

    /**
     * Computes a deterministic lowercase SHA-256 hex digest of the exact file bytes.
     */
    fun computeSha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = StringBuilder(digest.size * 2)
        for (b in digest) {
            val value = b.toInt() and 0xFF
            hex.append(HEX_CHARS[value ushr 4])
            hex.append(HEX_CHARS[value and 0x0F])
        }
        return hex.toString()
    }

    fun isDuplicateHashForWorker(
        fileHash: String,
        existingDocuments: List<IncomeDocument>,
        workerId: String
    ): Boolean {
        val cleanHash = fileHash.trim()
        if (cleanHash.isEmpty()) return false
        return existingDocuments.any { doc ->
            doc.workerId == workerId &&
                doc.fileHash.isNotBlank() &&
                doc.fileHash.equals(cleanHash, ignoreCase = true)
        }
    }

    private const val HEX_CHARS = "0123456789abcdef"
}
