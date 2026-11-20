package com.example.domain.rules

import com.example.model.ActorRole
import com.example.model.ConfirmationStatus
import com.example.model.DocumentSourceKind
import com.example.model.DocumentType
import com.example.model.EvidenceStatus
import com.example.model.PartyContactStatus
import com.example.model.PartyRelationshipStatus
import com.example.model.PartyRole
import com.example.model.PartyType
import com.example.model.PayoutCycle
import com.example.model.ReconciliationDecision
import com.example.model.RelationshipStatus
import com.example.model.ReviewStatus
import com.example.model.ShareLinkStatus

data class SyntheticSampleDocument(
    val id: String,
    val title: String,
    val subtitle: String,
    val sourceUploadType: SourceUploadType,
    val documentType: DocumentType,
    val rawDocumentText: String
)

data class SyntheticSampleBankCsv(
    val id: String,
    val fileName: String,
    val title: String,
    val subtitle: String,
    val csvText: String
)

data class DemoSeedBundle(
    val workerId: String,
    val workerDisplayName: String,
    val workerCity: String,
    val workerPrimaryWork: String,
    val parties: List<Map<String, Any>>,
    val relationships: List<Map<String, Any>>,
    val documents: List<Map<String, Any>>,
    val incomeRecords: List<Map<String, Any>>,
    val bankCredits: List<Map<String, Any>>,
    val reconciliations: List<Map<String, Any>>,
    val confirmationRequests: List<Map<String, Any>>,
    val shareLinks: List<Map<String, Any>>,
    val auditLogs: List<Map<String, Any>>
)

/**
 * Pure fictional demo data generator for "Asha Rao" (gig worker and freelancer) and sample
 * fictional income documents. All names, organizations, references, and accounts are 100% fictional.
 */
object DemoDataFactory {

    fun demoWorkerIdForUser(userId: String): String {
        val safeSuffix = userId.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "user" }
        return "asha_demo_$safeSuffix"
    }

    /**
     * Built-in fictional sample documents that can be read live by Gemini AI and routed to Review & Confirm.
     */
    val sampleDocuments: List<SyntheticSampleDocument> = listOf(
        SyntheticSampleDocument(
            id = "sample_rideco_payout",
            title = "RideCo Weekly Statement (Sample)",
            subtitle = "Platform statement · Gross Rs 12,000 − Rs 1,000 = Net Rs 11,000",
            sourceUploadType = SourceUploadType.PLATFORM_STATEMENT,
            documentType = DocumentType.PLATFORM_STATEMENT,
            rawDocumentText = """
                RIDECO PARTNER WEEKLY PAYOUT SUMMARY (FICTIONAL DEMO)
                Platform: RideCo
                Partner Name: Asha Rao (Partner ID: RC-BLR-4821)
                Payout Period: 2026-11-16 to 2026-11-22
                Trips Completed: 94
                Gross Earnings: 12000
                Total Deductions (Platform Fee): 1000
                Net Payout Amount: 11000
                Currency: INR
                Payout Date: 2026-11-24
                Payout Reference: RC-47-001
                Payment Status: Paid via IMPS to Demo Bank XX4821
            """.trimIndent()
        ),
        SyntheticSampleDocument(
            id = "sample_ravi_invoice",
            title = "Ravi Design Studio Invoice (Sample)",
            subtitle = "Invoice · Rs 12,000 · Social media design package",
            sourceUploadType = SourceUploadType.INVOICE,
            documentType = DocumentType.INVOICE,
            rawDocumentText = """
                FREELANCE DESIGN INVOICE (FICTIONAL DEMO)
                Invoice Number: INV-104
                Client Name: Ravi Design Studio
                Payer: Ravi Design Studio
                Service: Social media design package (Nov batch 2)
                Period: 2026-11-01 to 2026-11-15
                Invoice Amount: 12000
                Currency: INR
                Invoice Date: 2026-11-15
                Reference: INV-104
                Payment Status: Pending NEFT
            """.trimIndent()
        ),
        SyntheticSampleDocument(
            id = "sample_cash_note",
            title = "Weekend Event Helper Cash Note",
            subtitle = "Cash note · Rs 2,400 · Vani Event Services",
            sourceUploadType = SourceUploadType.CASH_NOTE,
            documentType = DocumentType.CASH_NOTE,
            rawDocumentText = """
                HANDWRITTEN CASH RECEIPT NOTE (FICTIONAL DEMO)
                Payer: Vani Event Services
                Received By: Asha Rao
                Date: 2026-11-11
                Work: Weekend booth coordination (2 shifts)
                Cash Paid: 2400 INR
                Receipt Ref: CASH-VES-111
                Status: Paid in cash
            """.trimIndent()
        ),
        SyntheticSampleDocument(
            id = "sample_math_mismatch_statement",
            title = "MetroDrop Statement (Math-Check Demo)",
            subtitle = "Platform statement · Gross 10,000 − Ded 500 != Net 9,100",
            sourceUploadType = SourceUploadType.PLATFORM_STATEMENT,
            documentType = DocumentType.PLATFORM_STATEMENT,
            rawDocumentText = """
                METRODROP RIDER WEEKLY PAYOUT (FICTIONAL DEMO)
                Platform: MetroDrop
                Rider: Asha Rao
                Period: 2026-11-02 to 2026-11-08
                Record Date: 2026-11-09
                Gross Payout: 10000
                Deductions: 500
                Net Payout Printed: 9100
                Currency: INR
                Reference: MD-45-771
                Payment Status: Paid
                (Note: Printed net intentionally differs to test code validation flag)
            """.trimIndent()
        )
    )

    val sampleBankCsv: SyntheticSampleBankCsv = SyntheticSampleBankCsv(
        id = "sample_fictional_bank_csv",
        fileName = "DemoBank_Nov2026_Fictional.csv",
        title = "Demo Bank Statement (Fictional CSV)",
        subtitle = "3 credit rows + 1 debit skipped · Parsed in code (no AI)",
        csvText = """
            Date,Descriptor,Debit,Credit,Reference
            2026-11-18,"RIDECO PAYOUT",,11000.00,RC-46-001
            2026-11-19,"NEFT RAVI DESIGN STUDIO INV102",,12000.00,NEFT-RDS-102
            2026-11-14,"UPI/FUEL STATION DEMO",650.00,,UTR-FUEL-1114
            2026-11-12,"UPI MEENA RAO",,3000.00,UPI-MR-1112
        """.trimIndent()
    )

    /**
     * Generates the exact canonical fictional demo state for "Asha Rao" (gig worker and freelancer):
     * 1) Platform RideCo (platform): weekly statement 9-15 Nov 2026, gross 12000, deductions 1000,
     *    net 11000, payout date 2026-11-17, ref RC-46-001 + same statement as a second photo
     *    representation (flagged duplicate event in Issues).
     * 2) Client Ravi Design Studio (individual business; work source & payer): invoice INV-102
     *    12000 dated 2026-11-15, "Social media design package". Client confirmed work delivered,
     *    amount 12000, payment sent on 2026-11-19.
     * 3) Client Kumar Stores: invoice INV-103 5000 dated 2026-11-14; client answered: work
     *    delivered yes, amount 4500 (corrected), payment not yet.
     * 4) Cash tips 800 (self_declared).
     * 5) Bank credits: 2026-11-18 "RIDECO PAYOUT" 11000 (pre-matched); 2026-11-19 "NEFT RAVI
     *    DESIGN STUDIO INV102" 12000 (pre-matched); 2026-11-12 "UPI MEENA RAO" 3000 (unlinked).
     * 6) One simulated record flagged simulated_api: RideCo statement 2-8 Nov 2026, gross 9500,
     *    deductions 700, net 8800, payout 2026-11-10 (no bank credit yet).
     */
    fun buildAshaDemoSeed(userId: String): DemoSeedBundle {
        val wid = demoWorkerIdForUser(userId)

        val pRideCo = "${wid}_p_rideco"
        val pRavi = "${wid}_p_ravi"
        val pKumar = "${wid}_p_kumar"

        val docRideCoPrimary = "${wid}_doc_rideco_nov9"
        val docRideCoDupPhoto = "${wid}_doc_rideco_nov9_photo"
        val docRaviInv102 = "${wid}_doc_ravi_inv102"
        val docKumarInv103 = "${wid}_doc_kumar_inv103"

        val incRideCoPrimary = "${wid}_inc_rideco_nov9"
        val incRideCoDupPhoto = "${wid}_inc_rideco_nov9_dup"
        val incRaviInv102 = "${wid}_inc_ravi_inv102"
        val incKumarInv103 = "${wid}_inc_kumar_inv103"
        val incCashTips = "${wid}_inc_cash_tips"
        val incRideCoSimApi = "${wid}_inc_rideco_sim_api"

        val bcRideCo = "${wid}_bc_rideco"
        val bcRavi = "${wid}_bc_ravi"
        val bcMeena = "${wid}_bc_meena"

        val recRideCo = "${wid}_rec_rideco"
        val recRavi = "${wid}_rec_ravi"

        val confRavi = "${wid}_conf_ravi"
        val confKumar = "${wid}_conf_kumar"

        val parties = listOf(
            mapOf(
                "_docId" to pRideCo,
                "userId" to userId,
                "workerId" to wid,
                "name" to "RideCo",
                "partyType" to PartyType.PLATFORM.code,
                "contactHint" to "Partner ID: RC-BLR-4821",
                "roles" to listOf(PartyRole.WORK_GIVER.code, PartyRole.PAYER.code),
                "phone" to "",
                "email" to "partners@rideco-fictional.invalid",
                "note" to "Fictional ride and delivery platform with weekly payouts.",
                "registrationNumber" to "",
                "partyContactStatus" to PartyContactStatus.ADDED_BY_WORKER.code
            ),
            mapOf(
                "_docId" to pRavi,
                "userId" to userId,
                "workerId" to wid,
                "name" to "Ravi Design Studio",
                "partyType" to PartyType.BUSINESS.code,
                "contactHint" to "Individual business · Work source & payer",
                "roles" to listOf(PartyRole.WORK_GIVER.code, PartyRole.PAYER.code),
                "phone" to "+91 98000 00102",
                "email" to "ravi@ravidesign-fictional.invalid",
                "note" to "Individual business client for freelance social media design packages.",
                "registrationNumber" to "",
                "partyContactStatus" to PartyContactStatus.ADDED_BY_WORKER.code
            ),
            mapOf(
                "_docId" to pKumar,
                "userId" to userId,
                "workerId" to wid,
                "name" to "Kumar Stores",
                "partyType" to PartyType.BUSINESS.code,
                "contactHint" to "Local retail client (fictional)",
                "roles" to listOf(PartyRole.WORK_GIVER.code, PartyRole.PAYER.code),
                "phone" to "+91 98000 00103",
                "email" to "billing@kumarstores-fictional.invalid",
                "note" to "Local store freelance design client.",
                "registrationNumber" to "",
                "partyContactStatus" to PartyContactStatus.ADDED_BY_WORKER.code
            )
        )

        val relationships = listOf(
            mapOf(
                "_docId" to "${wid}_rel_rideco",
                "userId" to userId,
                "workerId" to wid,
                "partyId" to pRideCo,
                "roleTitle" to "Gig Driver & Delivery Partner",
                "payoutCycle" to PayoutCycle.WEEKLY.code,
                "status" to RelationshipStatus.ACTIVE.code,
                "relationshipStatus" to PartyRelationshipStatus.WORKER_DECLARED.code,
                "roles" to listOf(PartyRole.WORK_GIVER.code, PartyRole.PAYER.code),
                "startedOn" to "2025-05-01"
            ),
            mapOf(
                "_docId" to "${wid}_rel_ravi",
                "userId" to userId,
                "workerId" to wid,
                "partyId" to pRavi,
                "roleTitle" to "Freelance Social Media Designer",
                "payoutCycle" to PayoutCycle.PER_TASK.code,
                "status" to RelationshipStatus.ACTIVE.code,
                "relationshipStatus" to PartyRelationshipStatus.CONFIRMED_SIMULATED.code,
                "roles" to listOf(PartyRole.WORK_GIVER.code, PartyRole.PAYER.code),
                "startedOn" to "2026-02-10"
            ),
            mapOf(
                "_docId" to "${wid}_rel_kumar",
                "userId" to userId,
                "workerId" to wid,
                "partyId" to pKumar,
                "roleTitle" to "Freelance Catalog Designer",
                "payoutCycle" to PayoutCycle.PER_TASK.code,
                "status" to RelationshipStatus.ACTIVE.code,
                "relationshipStatus" to PartyRelationshipStatus.DISPUTED.code,
                "roles" to listOf(PartyRole.WORK_GIVER.code, PartyRole.PAYER.code),
                "startedOn" to "2026-06-15"
            )
        )

        val documents = listOf(
            mapOf(
                "_docId" to docRideCoPrimary,
                "userId" to userId,
                "workerId" to wid,
                "partyId" to pRideCo,
                "title" to "RideCo Weekly Statement (9–15 Nov 2026)",
                "documentType" to DocumentType.PLATFORM_STATEMENT.code,
                "sourceKind" to DocumentSourceKind.FILE_UPLOAD.code,
                "reviewStatus" to ReviewStatus.CONFIRMED.code,
                "fileHash" to FileIngestionValidator.computeSha256Hex("fictional_rideco_pdf_2026_11_09_15_rc46001".toByteArray()),
                "extractedPartyName" to "RideCo",
                "extractedAmountRupees" to 11000L,
                "extractedDate" to "2026-11-17",
                "extractedReference" to "RC-46-001",
                "extractionNotes" to "Weekly statement 9–15 Nov 2026: Gross Rs 12,000 − Deductions Rs 1,000 = Net Rs 11,000."
            ),
            mapOf(
                "_docId" to docRideCoDupPhoto,
                "userId" to userId,
                "workerId" to wid,
                "partyId" to pRideCo,
                "title" to "RideCo Statement Photo Copy (9–15 Nov 2026)",
                "documentType" to DocumentType.PLATFORM_STATEMENT.code,
                "sourceKind" to DocumentSourceKind.PHOTO_PICKER.code,
                "reviewStatus" to ReviewStatus.CONFIRMED.code,
                "fileHash" to FileIngestionValidator.computeSha256Hex("fictional_rideco_photo_copy_2026_11_09_15_rc46001".toByteArray()),
                "extractedPartyName" to "RideCo",
                "extractedAmountRupees" to 11000L,
                "extractedDate" to "2026-11-17",
                "extractedReference" to "RC-46-001",
                "extractionNotes" to "Second photo representation of the same RideCo 9–15 Nov 2026 statement (RC-46-001)."
            ),
            mapOf(
                "_docId" to docRaviInv102,
                "userId" to userId,
                "workerId" to wid,
                "partyId" to pRavi,
                "title" to "Ravi Design Studio — Invoice INV-102",
                "documentType" to DocumentType.INVOICE.code,
                "sourceKind" to DocumentSourceKind.FILE_UPLOAD.code,
                "reviewStatus" to ReviewStatus.CONFIRMED.code,
                "fileHash" to FileIngestionValidator.computeSha256Hex("fictional_ravi_design_studio_inv102".toByteArray()),
                "extractedPartyName" to "Ravi Design Studio",
                "extractedAmountRupees" to 12000L,
                "extractedDate" to "2026-11-15",
                "extractedReference" to "INV-102",
                "extractionNotes" to "Invoice INV-102 for Social media design package (Rs 12,000)."
            ),
            mapOf(
                "_docId" to docKumarInv103,
                "userId" to userId,
                "workerId" to wid,
                "partyId" to pKumar,
                "title" to "Kumar Stores — Invoice INV-103",
                "documentType" to DocumentType.INVOICE.code,
                "sourceKind" to DocumentSourceKind.FILE_UPLOAD.code,
                "reviewStatus" to ReviewStatus.CONFIRMED.code,
                "fileHash" to FileIngestionValidator.computeSha256Hex("fictional_kumar_stores_inv103".toByteArray()),
                "extractedPartyName" to "Kumar Stores",
                "extractedAmountRupees" to 5000L,
                "extractedDate" to "2026-11-14",
                "extractedReference" to "INV-103",
                "extractionNotes" to "Invoice INV-103 dated 2026-11-14 for Rs 5,000."
            )
        )

        val rideCoGroupKey = "rideco|2026110920261115|11000|rc46001"

        val incomeRecords = listOf(
            // 1a) RideCo weekly statement 9-15 Nov 2026 (pre-matched to bank credit 2026-11-18 "RIDECO PAYOUT" 11000)
            mapOf(
                "_docId" to incRideCoPrimary,
                "userId" to userId,
                "workerId" to wid,
                "partyId" to pRideCo,
                "partyName" to "RideCo",
                "workGiverPartyId" to pRideCo,
                "workGiverPartyName" to "RideCo",
                "payerPartyId" to pRideCo,
                "payerPartyName" to "RideCo",
                "documentId" to docRideCoPrimary,
                "amountRupees" to 11000L,
                "earnedDate" to "2026-11-17",
                "periodLabel" to "9–15 Nov 2026",
                "referenceCode" to "RC-46-001",
                "invoiceNumber" to "",
                "periodStart" to "2026-11-09",
                "periodEnd" to "2026-11-15",
                "grossAmountRupees" to 12000L,
                "deductionsRupees" to 1000L,
                "netAmountRupees" to 11000L,
                "invoiceAmountRupees" to 0L,
                "evidenceStatus" to EvidenceStatus.SOURCE_MATCHED.code,
                "workerConfirmed" to true,
                "simulatedApi" to false,
                "duplicateGroupKey" to rideCoGroupKey,
                "groupedIntoRecordId" to "",
                "matchOutcome" to "STRONG",
                "paymentStatus" to "UNKNOWN",
                "paymentDate" to "",
                "conflictReason" to "",
                "manuallyDisputed" to false,
                "notes" to "Gross Rs 12,000 · Deductions Rs 1,000 · Net Rs 11,000. Matched to 2026-11-18 RIDECO PAYOUT bank credit."
            ),
            // 1b) RideCo duplicate second photo representation (grouped with primary & counted once; visible in Issues)
            mapOf(
                "_docId" to incRideCoDupPhoto,
                "userId" to userId,
                "workerId" to wid,
                "partyId" to pRideCo,
                "partyName" to "RideCo",
                "workGiverPartyId" to pRideCo,
                "workGiverPartyName" to "RideCo",
                "payerPartyId" to pRideCo,
                "payerPartyName" to "RideCo",
                "documentId" to docRideCoDupPhoto,
                "amountRupees" to 11000L,
                "earnedDate" to "2026-11-17",
                "periodLabel" to "9–15 Nov 2026 (2nd photo)",
                "referenceCode" to "RC-46-001",
                "invoiceNumber" to "",
                "periodStart" to "2026-11-09",
                "periodEnd" to "2026-11-15",
                "grossAmountRupees" to 12000L,
                "deductionsRupees" to 1000L,
                "netAmountRupees" to 11000L,
                "invoiceAmountRupees" to 0L,
                "evidenceStatus" to EvidenceStatus.DOCUMENT_EXTRACTED.code,
                "workerConfirmed" to true,
                "simulatedApi" to false,
                "duplicateGroupKey" to rideCoGroupKey,
                "groupedIntoRecordId" to incRideCoPrimary,
                "matchOutcome" to "STRONG",
                "paymentStatus" to "UNKNOWN",
                "paymentDate" to "",
                "conflictReason" to "",
                "manuallyDisputed" to false,
                "notes" to "Second photo representation of RideCo 9–15 Nov 2026 statement (RC-46-001). Grouped as one evidence item so it is counted once."
            ),
            // 2) Ravi Design Studio INV-102 Rs 12,000 (client confirmed payment sent + pre-matched -> SOURCE_MATCHED per Rule 7)
            mapOf(
                "_docId" to incRaviInv102,
                "userId" to userId,
                "workerId" to wid,
                "partyId" to pRavi,
                "partyName" to "Ravi Design Studio",
                "workGiverPartyId" to pRavi,
                "workGiverPartyName" to "Ravi Design Studio",
                "payerPartyId" to pRavi,
                "payerPartyName" to "Ravi Design Studio",
                "documentId" to docRaviInv102,
                "amountRupees" to 12000L,
                "earnedDate" to "2026-11-15",
                "periodLabel" to "Social media design package",
                "referenceCode" to "INV-102",
                "invoiceNumber" to "INV-102",
                "periodStart" to "",
                "periodEnd" to "",
                "grossAmountRupees" to 0L,
                "deductionsRupees" to 0L,
                "netAmountRupees" to 0L,
                "invoiceAmountRupees" to 12000L,
                "evidenceStatus" to EvidenceStatus.SOURCE_MATCHED.code,
                "workerConfirmed" to true,
                "simulatedApi" to false,
                "duplicateGroupKey" to "ravidesignstudio|20261115|12000|inv102",
                "groupedIntoRecordId" to "",
                "matchOutcome" to "STRONG",
                "paymentStatus" to "PAYMENT_SENT",
                "paymentDate" to "2026-11-19",
                "conflictReason" to "",
                "manuallyDisputed" to false,
                "notes" to "Social media design package. Client confirmed work delivered, Rs 12,000, payment sent on 2026-11-19. Strong match to NEFT bank credit."
            ),
            // 3) Kumar Stores INV-103 Rs 5,000 (client answered: work delivered yes, corrected to Rs 4,500, payment not yet -> DISPUTED)
            mapOf(
                "_docId" to incKumarInv103,
                "userId" to userId,
                "workerId" to wid,
                "partyId" to pKumar,
                "partyName" to "Kumar Stores",
                "workGiverPartyId" to pKumar,
                "workGiverPartyName" to "Kumar Stores",
                "payerPartyId" to pKumar,
                "payerPartyName" to "Kumar Stores",
                "documentId" to docKumarInv103,
                "amountRupees" to 5000L,
                "earnedDate" to "2026-11-14",
                "periodLabel" to "Invoice INV-103",
                "referenceCode" to "INV-103",
                "invoiceNumber" to "INV-103",
                "periodStart" to "",
                "periodEnd" to "",
                "grossAmountRupees" to 0L,
                "deductionsRupees" to 0L,
                "netAmountRupees" to 0L,
                "invoiceAmountRupees" to 5000L,
                "evidenceStatus" to EvidenceStatus.DISPUTED.code,
                "workerConfirmed" to true,
                "simulatedApi" to false,
                "duplicateGroupKey" to "kumarstores|20261114|5000|inv103",
                "groupedIntoRecordId" to "",
                "matchOutcome" to "NONE",
                "paymentStatus" to "PAYMENT_PENDING",
                "paymentDate" to "",
                "conflictReason" to "Client confirmed Rs 4500 vs invoiced Rs 5000.",
                "manuallyDisputed" to false,
                "notes" to "Client answered: work delivered yes, amount Rs 4,500 (corrected from Rs 5,000), payment not yet."
            ),
            // 4) Cash tips Rs 800 (self_declared)
            mapOf(
                "_docId" to incCashTips,
                "userId" to userId,
                "workerId" to wid,
                "partyId" to "",
                "partyName" to "Cash Tips",
                "workGiverPartyId" to "",
                "workGiverPartyName" to "Cash Tips",
                "payerPartyId" to "",
                "payerPartyName" to "Cash Tips",
                "documentId" to "",
                "amountRupees" to 800L,
                "earnedDate" to "2026-11-16",
                "periodLabel" to "Cash tips",
                "referenceCode" to "",
                "invoiceNumber" to "",
                "periodStart" to "",
                "periodEnd" to "",
                "grossAmountRupees" to 0L,
                "deductionsRupees" to 0L,
                "netAmountRupees" to 0L,
                "invoiceAmountRupees" to 0L,
                "evidenceStatus" to EvidenceStatus.SELF_DECLARED.code,
                "workerConfirmed" to true,
                "simulatedApi" to false,
                "duplicateGroupKey" to "cashtips|cashtips|800|",
                "groupedIntoRecordId" to "",
                "matchOutcome" to "NONE",
                "paymentStatus" to "UNKNOWN",
                "paymentDate" to "",
                "conflictReason" to "",
                "manuallyDisputed" to false,
                "notes" to "Cash tips entered directly by worker (self_declared)."
            ),
            // 6) RideCo simulated_api statement for 2-8 Nov 2026 (Rule 7: simulated import -> source_verified (Simulated))
            mapOf(
                "_docId" to incRideCoSimApi,
                "userId" to userId,
                "workerId" to wid,
                "partyId" to pRideCo,
                "partyName" to "RideCo",
                "workGiverPartyId" to pRideCo,
                "workGiverPartyName" to "RideCo",
                "payerPartyId" to pRideCo,
                "payerPartyName" to "RideCo",
                "documentId" to "",
                "amountRupees" to 8800L,
                "earnedDate" to "2026-11-10",
                "periodLabel" to "2–8 Nov 2026",
                "referenceCode" to "RC-45-SIM",
                "invoiceNumber" to "",
                "periodStart" to "2026-11-02",
                "periodEnd" to "2026-11-08",
                "grossAmountRupees" to 9500L,
                "deductionsRupees" to 700L,
                "netAmountRupees" to 8800L,
                "invoiceAmountRupees" to 0L,
                "evidenceStatus" to EvidenceStatus.SOURCE_VERIFIED_SIMULATED.code,
                "workerConfirmed" to true,
                "simulatedApi" to true,
                "duplicateGroupKey" to "rideco|2026110220261108|8800|rc45sim",
                "groupedIntoRecordId" to "",
                "matchOutcome" to "NONE",
                "paymentStatus" to "UNKNOWN",
                "paymentDate" to "",
                "conflictReason" to "",
                "manuallyDisputed" to false,
                "notes" to "Simulated API record (simulated_api): Gross Rs 9,500 · Deductions Rs 700 · Net Rs 8,800 · Payout 2026-11-10 (no bank credit yet)."
            )
        )

        // 5) Exact 3 bank credits (Rule 5: Meena Rao flagged possible_personal_transfer; Rule 7: matched credits labeled SOURCE_MATCHED)
        val bankCredits = listOf(
            mapOf(
                "_docId" to bcRideCo,
                "userId" to userId,
                "workerId" to wid,
                "bankLabel" to "Demo Bank ••4821",
                "creditDate" to "2026-11-18",
                "amountRupees" to 11000L,
                "narration" to "RIDECO PAYOUT",
                "utrReference" to "RC-46-001",
                "matchedIncomeRecordId" to incRideCoPrimary,
                "classificationStatus" to "MATCHED",
                "evidenceStatus" to EvidenceStatus.SOURCE_MATCHED.code
            ),
            mapOf(
                "_docId" to bcRavi,
                "userId" to userId,
                "workerId" to wid,
                "bankLabel" to "Demo Bank ••4821",
                "creditDate" to "2026-11-19",
                "amountRupees" to 12000L,
                "narration" to "NEFT RAVI DESIGN STUDIO INV102",
                "utrReference" to "INV102",
                "matchedIncomeRecordId" to incRaviInv102,
                "classificationStatus" to "MATCHED",
                "evidenceStatus" to EvidenceStatus.SOURCE_MATCHED.code
            ),
            mapOf(
                "_docId" to bcMeena,
                "userId" to userId,
                "workerId" to wid,
                "bankLabel" to "Demo Bank ••4821",
                "creditDate" to "2026-11-12",
                "amountRupees" to 3000L,
                "narration" to "UPI MEENA RAO",
                "utrReference" to "UPI-MEENA-1112",
                "matchedIncomeRecordId" to "",
                "classificationStatus" to "POSSIBLE_PERSONAL_TRANSFER",
                "evidenceStatus" to ""
            )
        )

        val reconciliations = listOf(
            mapOf(
                "_docId" to recRideCo,
                "userId" to userId,
                "workerId" to wid,
                "incomeRecordId" to incRideCoPrimary,
                "bankCreditId" to bcRideCo,
                "matchRule" to "Platform payout within ±3d (1d apart) · name + ref match",
                "amountDeltaRupees" to 0L,
                "dateGapDays" to 1L,
                "decisionStatus" to ReconciliationDecision.CONFIRMED.code,
                "matchOutcome" to "STRONG"
            ),
            mapOf(
                "_docId" to recRavi,
                "userId" to userId,
                "workerId" to wid,
                "incomeRecordId" to incRaviInv102,
                "bankCreditId" to bcRavi,
                "matchRule" to "Client payment within ±7d of stated date (0d apart) · name + ref match",
                "amountDeltaRupees" to 0L,
                "dateGapDays" to 0L,
                "decisionStatus" to ReconciliationDecision.CONFIRMED.code,
                "matchOutcome" to "STRONG"
            )
        )

        val confirmationRequests = listOf(
            mapOf(
                "_docId" to confRavi,
                "userId" to userId,
                "workerId" to wid,
                "incomeRecordId" to incRaviInv102,
                "partyId" to pRavi,
                "partyName" to "Ravi Design Studio",
                "requestedAmountRupees" to 12000L,
                "status" to ConfirmationStatus.CONFIRMED_SIMULATED.code,
                "responseNote" to "Work delivered: Yes · Confirmed amount: Rs 12,000 · Payment sent on 2026-11-19.",
                "workDelivered" to true,
                "confirmedAmountRupees" to 12000L,
                "paymentSent" to true,
                "paymentDate" to "2026-11-19"
            ),
            mapOf(
                "_docId" to confKumar,
                "userId" to userId,
                "workerId" to wid,
                "incomeRecordId" to incKumarInv103,
                "partyId" to pKumar,
                "partyName" to "Kumar Stores",
                "requestedAmountRupees" to 5000L,
                "status" to ConfirmationStatus.DISPUTED.code,
                "responseNote" to "Work delivered: Yes · Amount corrected to Rs 4,500 (vs Rs 5,000 invoiced) · Payment not yet.",
                "workDelivered" to true,
                "confirmedAmountRupees" to 4500L,
                "paymentSent" to false,
                "paymentDate" to ""
            )
        )

        val shareLinks = listOf(
            mapOf(
                "_docId" to "${wid}_share_1",
                "userId" to userId,
                "workerId" to wid,
                "workerName" to "Asha Rao",
                "shareCode" to "ASHA-2026",
                "recipientLabel" to "Demo Landlord (Rental Income Proof)",
                "dateRangeLabel" to "Nov 2026",
                "includedPartyIds" to listOf(pRideCo, pRavi),
                "includedEvidenceLevels" to listOf(
                    EvidenceStatus.SOURCE_VERIFIED_SIMULATED.code,
                    EvidenceStatus.SOURCE_MATCHED.code
                ),
                "maskBankRefs" to true,
                "expiresOn" to "2026-12-31",
                "status" to ShareLinkStatus.ACTIVE.code,
                "totalIncludedRupees" to 31800L,
                "summaryLines" to listOf(
                    "2026-11-17 | RideCo | Rs 11,000 | Source-matched | Ref: ••••-001",
                    "2026-11-15 | Ravi Design Studio | Rs 12,000 | Source-matched | Ref: ••••-102",
                    "2026-11-10 | RideCo | Rs 8,800 | Source-verified (Simulated) | Ref: ••••-SIM"
                ),
                "consentText" to "I consent to share this read-only earnings summary. It is not a credit score or loan approval."
            )
        )

        val auditLogs = listOf(
            mapOf(
                "_docId" to "${wid}_log_1",
                "userId" to userId,
                "workerId" to wid,
                "actionType" to "WORKER_CONFIRMED_RECORD",
                "actorRole" to ActorRole.WORKER.code,
                "targetCollection" to "income_records",
                "targetId" to incRideCoPrimary,
                "summary" to "Asha Rao confirmed RideCo weekly statement 9–15 Nov 2026 (Gross Rs 12,000 − Rs 1,000 = Net Rs 11,000, ref RC-46-001)."
            ),
            mapOf(
                "_docId" to "${wid}_log_2",
                "userId" to userId,
                "workerId" to wid,
                "actionType" to "RULE_RECONCILIATION_MATCHED",
                "actorRole" to ActorRole.RULES.code,
                "targetCollection" to "reconciliations",
                "targetId" to recRideCo,
                "summary" to "Strong match: RideCo Rs 11,000 (RC-46-001) <-> 2026-11-18 RIDECO PAYOUT -> both records labeled Source-matched."
            ),
            mapOf(
                "_docId" to "${wid}_log_3",
                "userId" to userId,
                "workerId" to wid,
                "actionType" to "DUPLICATE_GROUPED",
                "actorRole" to ActorRole.RULES.code,
                "targetCollection" to "income_records",
                "targetId" to incRideCoDupPhoto,
                "summary" to "Grouped 2nd photo of RideCo 9–15 Nov 2026 (RC-46-001, Rs 11,000) with primary evidence item so it is counted once."
            ),
            mapOf(
                "_docId" to "${wid}_log_4",
                "userId" to userId,
                "workerId" to wid,
                "actionType" to "PARTY_CONFIRMATION_SIMULATED",
                "actorRole" to ActorRole.PARTY_SIMULATED.code,
                "targetCollection" to "confirmation_requests",
                "targetId" to confRavi,
                "summary" to "Ravi Design Studio confirmed INV-102 (payment sent 2026-11-19 -> relationship confirmed); strong NEFT match labeled both Source-matched."
            ),
            mapOf(
                "_docId" to "${wid}_log_5",
                "userId" to userId,
                "workerId" to wid,
                "actionType" to "PARTY_CONFIRMATION_DISPUTED",
                "actorRole" to ActorRole.PARTY_SIMULATED.code,
                "targetCollection" to "confirmation_requests",
                "targetId" to confKumar,
                "summary" to "Kumar Stores corrected INV-103 to Rs 4,500 (vs Rs 5,000), payment not yet -> Disputed (overrides all other labels)."
            ),
            mapOf(
                "_docId" to "${wid}_log_6",
                "userId" to userId,
                "workerId" to wid,
                "actionType" to "SIMULATED_API_SYNC",
                "actorRole" to ActorRole.RULES.code,
                "targetCollection" to "income_records",
                "targetId" to incRideCoSimApi,
                "summary" to "Simulated API import for RideCo 2–8 Nov 2026 (Net Rs 8,800, payout 2026-11-10) -> Source-verified (Simulated)."
            )
        )

        return DemoSeedBundle(
            workerId = wid,
            workerDisplayName = "Asha Rao",
            workerCity = "Bengaluru",
            workerPrimaryWork = "Gig Worker & Freelancer",
            parties = parties,
            relationships = relationships,
            documents = documents,
            incomeRecords = incomeRecords,
            bankCredits = bankCredits,
            reconciliations = reconciliations,
            confirmationRequests = confirmationRequests,
            shareLinks = shareLinks,
            auditLogs = auditLogs
        )
    }
}
