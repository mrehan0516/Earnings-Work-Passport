package com.example

import com.example.domain.rules.BankCsvParser
import com.example.domain.rules.DemoDataFactory
import com.example.domain.rules.DocumentExtractionValidator
import com.example.domain.rules.EvidenceClassifier
import com.example.domain.rules.FileIngestionValidator
import com.example.domain.rules.FileValidationResult
import com.example.domain.rules.IssueDetector
import com.example.domain.rules.IssueType
import com.example.domain.rules.PartyRules
import com.example.domain.rules.PassportAggregator
import com.example.domain.rules.RawDocumentExtraction
import com.example.domain.rules.ReconciliationEngine
import com.example.domain.rules.ReviewConfirmRules
import com.example.domain.rules.ReviewFormValues
import com.example.domain.rules.RupeeFormatter
import com.example.domain.rules.SourceUploadType
import com.example.domain.rules.UploadFileKind
import com.example.domain.rules.ValidationFlagCode
import com.example.model.BankCredit
import com.example.model.ConfirmationStatus
import com.example.model.EvidenceStatus
import com.example.model.IncomeDocument
import com.example.model.IncomeRecord
import com.example.model.Party
import com.example.model.PartyContactStatus
import com.example.model.PartyRelationshipStatus
import com.example.model.PartyRole
import com.example.model.PartyType
import com.example.model.Reconciliation
import com.example.model.ReconciliationDecision
import com.example.model.ReviewStatus
import com.example.model.ShareLink
import com.example.model.ShareLinkStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassportBusinessRulesTest {

    @Test
    fun rupeeFormatterFormatsIndianGroupingAccurately() {
        assertEquals("Rs 0", RupeeFormatter.format(0))
        assertEquals("Rs 950", RupeeFormatter.format(950))
        assertEquals("Rs 14,250", RupeeFormatter.format(14250))
        assertEquals("Rs 1,24,500", RupeeFormatter.format(124500))
        assertEquals("Rs 12,34,567", RupeeFormatter.format(1234567))
        assertEquals("-Rs 250", RupeeFormatter.format(-250))
        assertEquals("+Rs 200", RupeeFormatter.formatSignedDelta(200))
        assertEquals("••••0441", RupeeFormatter.maskReference("UTR630210290441"))
        assertEquals(1L, RupeeFormatter.daysBetween("2026-10-28", "2026-10-29"))
    }

    @Test
    fun evidenceClassifierEnforcesWorkerConfirmationAndDisputePrecedence() {
        assertEquals(EvidenceStatus.SELF_DECLARED, EvidenceClassifier.forSelfDeclaredEntry())
        assertEquals(
            EvidenceStatus.DOCUMENT_EXTRACTED,
            EvidenceClassifier.forWorkerConfirmedDocument(workerConfirmed = true)
        )
        assertEquals(
            EvidenceStatus.SOURCE_MATCHED,
            EvidenceClassifier.onBankMatchConfirmed(EvidenceStatus.DOCUMENT_EXTRACTED)
        )
        assertEquals(
            EvidenceStatus.DISPUTED,
            EvidenceClassifier.onBankMatchConfirmed(EvidenceStatus.DISPUTED)
        )
        assertEquals(
            EvidenceStatus.SOURCE_VERIFIED_SIMULATED,
            EvidenceClassifier.forSimulatedApiImport()
        )
        assertEquals(
            EvidenceStatus.DOCUMENT_EXTRACTED,
            EvidenceClassifier.onSimulatedPartyResponse(
                ConfirmationStatus.CONFIRMED_SIMULATED,
                EvidenceStatus.DOCUMENT_EXTRACTED
            )
        )
        assertEquals(
            EvidenceStatus.DISPUTED,
            EvidenceClassifier.onSimulatedPartyResponse(
                ConfirmationStatus.DISPUTED,
                EvidenceStatus.SOURCE_MATCHED
            )
        )
    }

    @Test
    fun reconciliationEngineMatchesExactAndOneRupeeToleranceCredits() {
        val exactRec = IncomeRecord(
            id = "inc_1",
            partyName = "RideCo",
            amountRupees = 11000,
            netAmountRupees = 11000,
            earnedDate = "2026-11-17",
            referenceCode = "RC-46-001",
            evidenceStatus = EvidenceStatus.DOCUMENT_EXTRACTED.code
        )
        val oneRupeeRec = IncomeRecord(
            id = "inc_2",
            partyName = "MetroDrop",
            amountRupees = 9500,
            netAmountRupees = 9500,
            earnedDate = "2026-11-05",
            referenceCode = "MD-45-334",
            evidenceStatus = EvidenceStatus.DOCUMENT_EXTRACTED.code
        )
        val exactCredit = BankCredit(
            id = "bc_1",
            bankLabel = "Demo Bank ••4821",
            creditDate = "2026-11-18",
            amountRupees = 11000,
            narration = "IMPS/RIDECO PAYOUT/RC-46-001",
            utrReference = "UTR001"
        )
        val oneRupeeCredit = BankCredit(
            id = "bc_2",
            bankLabel = "Demo Bank ••4821",
            creditDate = "2026-11-06",
            amountRupees = 9499,
            narration = "NEFT/METRODROP/MD-45-334",
            utrReference = "UTR002"
        )
        val outOfToleranceCredit = BankCredit(
            id = "bc_3",
            bankLabel = "Demo Bank ••4821",
            creditDate = "2026-11-06",
            amountRupees = 9300,
            narration = "NEFT/METRODROP/OTHER",
            utrReference = "UTR003"
        )

        val candidates = ReconciliationEngine.findMatchCandidates(
            incomeRecords = listOf(exactRec, oneRupeeRec),
            bankCredits = listOf(exactCredit, oneRupeeCredit, outOfToleranceCredit),
            existingReconciliations = emptyList()
        )

        assertEquals(2, candidates.size)
        val exactMatch = candidates.first { it.incomeRecord.id == "inc_1" }
        assertEquals(0L, exactMatch.amountDeltaRupees)
        val toleranceMatch = candidates.first { it.incomeRecord.id == "inc_2" }
        assertEquals(-1L, toleranceMatch.amountDeltaRupees)
    }

    @Test
    fun issueDetectorSurfacesPendingDocsAmountMismatchesAndDisputes() {
        val pendingDoc = IncomeDocument(
            id = "doc_p",
            title = "Invoice",
            reviewStatus = ReviewStatus.PENDING_REVIEW.code,
            extractedPartyName = "Priya Studio",
            extractedAmountRupees = 6500
        )
        val disputedRec = IncomeRecord(
            id = "inc_d",
            partyName = "Blinkit Express",
            amountRupees = 3600,
            earnedDate = "2026-10-19",
            evidenceStatus = EvidenceStatus.DISPUTED.code
        )
        val issues = IssueDetector.detectIssues(
            incomeRecords = listOf(disputedRec),
            documents = listOf(pendingDoc),
            bankCredits = emptyList(),
            reconciliations = emptyList(),
            candidates = emptyList()
        )
        assertTrue(issues.any { it.type == IssueType.PENDING_AI_REVIEW })
        assertTrue(issues.any { it.type == IssueType.DISPUTED_RECORD })
    }

    @Test
    fun passportAggregatorRespectsConsentScopeAndRevocation() {
        val party1 = Party(id = "p1", name = "Swiggy Delivery")
        val rec1 = IncomeRecord(
            id = "r1",
            partyId = "p1",
            partyName = "Swiggy Delivery",
            amountRupees = 14250,
            earnedDate = "2026-10-28",
            referenceCode = "SWG261028901",
            evidenceStatus = EvidenceStatus.SOURCE_MATCHED.code
        )
        val rec2 = IncomeRecord(
            id = "r2",
            partyId = "p2",
            partyName = "Cash Job",
            amountRupees = 2000,
            earnedDate = "2026-10-29",
            evidenceStatus = EvidenceStatus.SELF_DECLARED.code
        )

        val summary = PassportAggregator.buildSummary(listOf(rec1, rec2), listOf(party1), emptyList())
        assertEquals(16250L, summary.nonDisputedTotalRupees)

        val shareLink = ShareLink(
            id = "s1",
            workerName = "Asha Rao",
            shareCode = "ASHA-2026",
            recipientLabel = "Landlord",
            dateRangeLabel = "Oct 2026",
            includedPartyIds = listOf("p1"),
            includedEvidenceLevels = listOf(EvidenceStatus.SOURCE_MATCHED.code),
            maskBankRefs = true,
            expiresOn = "2026-12-31",
            status = ShareLinkStatus.ACTIVE.code,
            consentText = "Consented"
        )
        val report = PassportAggregator.buildReviewerReport(shareLink, listOf(rec1, rec2), emptyList())
        assertFalse(report.isRevoked)
        assertEquals(14250L, report.totalRupees)
        assertEquals(1, report.lines.size)
        assertEquals("••••8901", report.lines.first().displayReference)
        assertTrue(report.plainTextExport.contains("AI reads. Rules and you decide."))
    }

    @Test
    fun fileIngestionValidatorEnforcesSizeMimeAndPerWorkerHashDeduplication() {
        val validPdf = FileIngestionValidator.validateFile(
            fileName = "swiggy_nov.pdf",
            mimeType = "application/pdf",
            sizeBytes = 250_000L,
            selectedSourceType = SourceUploadType.PLATFORM_STATEMENT
        )
        assertTrue(validPdf is FileValidationResult.Valid)
        assertEquals(UploadFileKind.PDF, (validPdf as FileValidationResult.Valid).fileKind)

        val tooLarge = FileIngestionValidator.validateFile(
            fileName = "huge.pdf",
            mimeType = "application/pdf",
            sizeBytes = FileIngestionValidator.MAX_UPLOAD_BYTES + 1L,
            selectedSourceType = SourceUploadType.PLATFORM_STATEMENT
        )
        assertTrue(tooLarge is FileValidationResult.Invalid)
        assertTrue((tooLarge as FileValidationResult.Invalid).message.contains("10 MB"))

        val unsupported = FileIngestionValidator.validateFile(
            fileName = "notes.docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            sizeBytes = 12_000L,
            selectedSourceType = SourceUploadType.INVOICE
        )
        assertTrue(unsupported is FileValidationResult.Invalid)

        val pdfInCsvMode = FileIngestionValidator.validateFile(
            fileName = "statement.pdf",
            mimeType = "application/pdf",
            sizeBytes = 50_000L,
            selectedSourceType = SourceUploadType.BANK_STATEMENT_CSV
        )
        assertTrue(pdfInCsvMode is FileValidationResult.Invalid)

        val hashA = FileIngestionValidator.computeSha256Hex("sample-bytes".toByteArray())
        assertEquals(64, hashA.length)
        val existing = listOf(IncomeDocument(id = "d1", workerId = "worker_1", fileHash = hashA))
        assertTrue(FileIngestionValidator.isDuplicateHashForWorker(hashA, existing, "worker_1"))
        assertFalse(FileIngestionValidator.isDuplicateHashForWorker(hashA, existing, "worker_2"))
    }

    @Test
    fun documentExtractionValidatorFlagsMathMismatchWithoutSilentlyFixingNumbers() {
        val balanced = DocumentExtractionValidator.validate(
            RawDocumentExtraction(
                platform = "Swiggy Delivery",
                recordDate = "2026-11-16",
                periodStart = "2026-11-09",
                periodEnd = "2026-11-15",
                grossAmountRaw = "14500",
                deductionsRaw = "250.5",
                netAmountRaw = "14250"
            )
        )
        assertFalse(balanced.hasFlags)
        assertFalse(balanced.numbersDoNotAddUp)

        val mismatch = DocumentExtractionValidator.validate(
            RawDocumentExtraction(
                platform = "Blinkit Express",
                recordDate = "2026-02-30", // invalid calendar date
                grossAmountRaw = "12000",
                deductionsRaw = "250",
                netAmountRaw = "11400", // off by 350 -> must flag "Numbers do not add up"
                invoiceAmountRaw = "-50" // non-positive
            )
        )
        assertTrue(mismatch.numbersDoNotAddUp)
        assertTrue(mismatch.flags.any { it.title == "Numbers do not add up" })
        assertTrue(mismatch.flags.any { it.code == ValidationFlagCode.INVALID_DATE })
        assertTrue(mismatch.flags.any { it.code == ValidationFlagCode.NON_POSITIVE_AMOUNT })
        // Crucial: verify values were NEVER silently fixed
        assertEquals(12000.0, mismatch.grossAmount!!, 0.001)
        assertEquals(250.0, mismatch.deductions!!, 0.001)
        assertEquals(11400.0, mismatch.netAmount!!, 0.001)
    }

    @Test
    fun bankCsvParserExtractsCreditsSkipsDebitsAndHandlesQuotedDescriptors() {
        val csv = """
            Date,Descriptor,Debit,Credit,Reference
            16/11/2026,"IMPS/SWIGGY, BLR/SWG2611164821",,"14,250.00",UTR632011164821
            11-Nov-2026,"UPI/FUEL PUMP",650.00,,UTR631511110099
            2026-11-02,"UPI/KULKARNI FAMILY",,"Rs 8,400",
            bad-date,"BAD ROW",,500,
        """.trimIndent()

        val result = BankCsvParser.parse(csv, "SBI_Nov.csv")
        assertEquals(2, result.credits.size)
        assertEquals(1, result.skippedDebitOrEmptyRows)
        assertEquals(1, result.rowErrors.size)

        assertEquals("2026-11-16", result.credits[0].date)
        assertEquals(14250L, result.credits[0].amountRupees)
        assertEquals("IMPS/SWIGGY, BLR/SWG2611164821", result.credits[0].descriptor)
        assertEquals("UTR632011164821", result.credits[0].utrReference)

        assertEquals("2026-11-02", result.credits[1].date)
        assertEquals(8400L, result.credits[1].amountRupees)
    }

    @Test
    fun reviewConfirmRulesBuildAiOriginalTrackEditsAndValidateLiveWithoutSilentFixes() {
        val aiRaw = RawDocumentExtraction(
            documentType = "platform_statement",
            platform = "Blinkit Express",
            payer = null,
            clientName = null,
            invoiceNumber = null,
            recordDate = "2026-11-16",
            periodStart = "2026-11-09",
            periodEnd = "2026-11-15",
            grossAmountRaw = "12000",
            deductionsRaw = "250",
            netAmountRaw = "11400",
            invoiceAmountRaw = null,
            currency = "INR",
            reference = null,
            paymentStatusStated = "Paid",
            notes = "Reference ID was smudged; printed net differs from gross minus deductions."
        )

        val uncertainty = ReviewConfirmRules.summarizeAiUncertainty(aiRaw)
        assertTrue(uncertainty.hasUncertainty)
        assertTrue(uncertainty.missingFieldLabels.contains("Reference / Invoice #"))
        assertTrue(uncertainty.aiNotes.contains("smudged"))

        val aiOriginalMap = ReviewConfirmRules.buildAiOriginalMap(aiRaw)
        assertEquals("Blinkit Express", aiOriginalMap["platform"])
        assertEquals("11400", aiOriginalMap["net_amount"])
        assertFalse(aiOriginalMap.containsKey("reference"))

        // Before worker fixes net amount, live validation flags "Numbers do not add up"
        val uneditedForm = ReviewFormValues(
            title = "Blinkit Statement",
            platform = "Blinkit Express",
            payer = "",
            clientName = "",
            invoiceNumber = "",
            recordDate = "2026-11-16",
            periodStart = "2026-11-09",
            periodEnd = "2026-11-15",
            grossAmountText = "12000",
            deductionsText = "250",
            netAmountText = "11400",
            invoiceAmountText = "",
            reference = "",
            notes = aiRaw.notes.orEmpty()
        )
        assertTrue(ReviewConfirmRules.validateEditedForm(aiRaw, uneditedForm).numbersDoNotAddUp)
        assertTrue(ReviewConfirmRules.computeWorkerEdits(aiRaw, uneditedForm).isEmpty())
        assertTrue(ReviewConfirmRules.formatEditAuditSummary(emptyList()).contains("without edits"))

        // Worker explicitly edits net amount to 11750 and adds reference -> edits captured, mismatch clears
        val editedForm = uneditedForm.copy(
            netAmountText = "11750",
            reference = "BLK261116889"
        )
        val edits = ReviewConfirmRules.computeWorkerEdits(aiRaw, editedForm)
        assertEquals(2, edits.size)
        assertTrue(edits.any { it.fieldKey == "net_amount" && it.aiValue == "11400" && it.confirmedValue == "11750" })
        assertTrue(edits.any { it.fieldKey == "reference" && it.aiValue == "null" && it.confirmedValue == "BLK261116889" })
        assertTrue(ReviewConfirmRules.formatEditAuditSummary(edits).contains("edited 2 field(s)"))
        assertFalse(ReviewConfirmRules.validateEditedForm(aiRaw, editedForm).numbersDoNotAddUp)
        assertEquals(11750L, editedForm.resolvedConfirmedAmountRupees())
        assertEquals("Blinkit Express", editedForm.resolvedPartyName())
    }

    @Test
    fun partyRulesDetectSimilarNamesStripIndividualRegAndTrackSplitRoles() {
        val existing = listOf(
            Party(id = "p_swiggy", name = "Swiggy Delivery", partyType = PartyType.PLATFORM.code),
            Party(id = "p_kulkarni", name = "Kulkarni Household Tutoring", partyType = PartyType.INDIVIDUAL.code),
            Party(id = "p_razorpay", name = "Razorpay Payouts", partyType = PartyType.PAYMENT_PROVIDER.code)
        )

        // Exact case/punctuation match
        assertTrue(PartyRules.findSimilarParties("swiggy delivery!", existing).any { it.party.id == "p_swiggy" })
        // Shared core token ("kulkarni")
        assertTrue(PartyRules.findSimilarParties("Kulkarni Family", existing).any { it.party.id == "p_kulkarni" })
        // Small spelling typo (Levenshtein <= 2)
        assertTrue(PartyRules.findSimilarParties("Swigy Delivery", existing).any { it.party.id == "p_swiggy" })
        // Unrelated name returns empty
        assertTrue(PartyRules.findSimilarParties("Urban Company", existing).isEmpty())

        // Individuals and Other never carry a registration number
        assertEquals("", PartyRules.sanitizeRegistrationNumber(PartyType.INDIVIDUAL, "29ABCDE1234F1Z5"))
        assertEquals("", PartyRules.sanitizeRegistrationNumber(PartyType.OTHER, "REG-999"))
        assertEquals("29ABCDE1234F1Z5", PartyRules.sanitizeRegistrationNumber(PartyType.BUSINESS, " 29abcde1234f1z5 "))
        assertEquals("29PLAT9999Z1", PartyRules.sanitizeRegistrationNumber(PartyType.PLATFORM, "29plat9999z1"))
        assertEquals("29PAY8888Z2", PartyRules.sanitizeRegistrationNumber(PartyType.PAYMENT_PROVIDER, "29pay8888z2"))

        // Split work giver vs payer linking
        val splitRecord = IncomeRecord(
            id = "rec_split",
            partyId = "p_kulkarni",
            partyName = "Kulkarni Household Tutoring",
            workGiverPartyId = "p_kulkarni",
            workGiverPartyName = "Kulkarni Household Tutoring",
            payerPartyId = "p_razorpay",
            payerPartyName = "Razorpay Payouts",
            amountRupees = 8400,
            earnedDate = "2026-11-05"
        )
        val samePartyRecord = IncomeRecord(
            id = "rec_same",
            partyId = "p_swiggy",
            partyName = "Swiggy Delivery",
            workGiverPartyId = "p_swiggy",
            workGiverPartyName = "Swiggy Delivery",
            payerPartyId = "p_swiggy",
            payerPartyName = "Swiggy Delivery",
            amountRupees = 14250,
            earnedDate = "2026-11-10"
        )
        val records = listOf(splitRecord, samePartyRecord)

        assertEquals(listOf("rec_split"), PartyRules.recordsForParty(existing[1], records).map { it.id })
        assertEquals(listOf("rec_split"), PartyRules.recordsForParty(existing[2], records).map { it.id })
        assertEquals("Gave work", PartyRules.roleLabelForRecord(existing[1], splitRecord))
        assertEquals("Paid", PartyRules.roleLabelForRecord(existing[2], splitRecord))
        assertEquals("Gave work & Paid", PartyRules.roleLabelForRecord(existing[0], samePartyRecord))

        // Never label any party status "verified"
        PartyContactStatus.entries.forEach {
            assertFalse(it.label.contains("verified", ignoreCase = true))
            assertFalse(it.code.contains("verified", ignoreCase = true))
        }
        PartyRelationshipStatus.entries.forEach {
            assertFalse(it.label.contains("verified", ignoreCase = true))
            assertFalse(it.code.contains("verified", ignoreCase = true))
        }
        assertEquals(listOf(PartyRole.WORK_GIVER, PartyRole.PAYER), PartyRules.defaultRolesIfEmpty(emptyList()))
    }

    @Test
    fun ashaDemoSeedContainsExactSixFictionalScenariosAndTargetsDuplicatePhotoCorrectly() {
        val seed = DemoDataFactory.buildAshaDemoSeed("test_uid_01")
        assertEquals("Asha Rao", seed.workerDisplayName)
        assertEquals("Gig Worker & Freelancer", seed.workerPrimaryWork)
        assertEquals(3, seed.parties.size)
        assertEquals(6, seed.incomeRecords.size)
        assertEquals(3, seed.bankCredits.size)
        assertEquals(2, seed.reconciliations.size)
        assertEquals(2, seed.confirmationRequests.size)

        // Verify scenario 1a & 1b: RideCo 9-15 Nov + duplicate second photo representation
        val rideCoPrimary = seed.incomeRecords.first { it["_docId"].toString().endsWith("_inc_rideco_nov9") }
        assertEquals("RideCo", rideCoPrimary["partyName"])
        assertEquals(12000L, rideCoPrimary["grossAmountRupees"])
        assertEquals(1000L, rideCoPrimary["deductionsRupees"])
        assertEquals(11000L, rideCoPrimary["netAmountRupees"])
        assertEquals("2026-11-17", rideCoPrimary["earnedDate"])
        assertEquals("RC-46-001", rideCoPrimary["referenceCode"])
        assertEquals(EvidenceStatus.SOURCE_MATCHED.code, rideCoPrimary["evidenceStatus"])
        assertEquals(false, rideCoPrimary["simulatedApi"])

        val rideCoDup = seed.incomeRecords.first { it["_docId"].toString().endsWith("_inc_rideco_nov9_dup") }
        assertEquals("RideCo", rideCoDup["partyName"])
        assertEquals(11000L, rideCoDup["amountRupees"])
        assertEquals("RC-46-001", rideCoDup["referenceCode"])
        assertEquals(EvidenceStatus.DOCUMENT_EXTRACTED.code, rideCoDup["evidenceStatus"])

        // Verify scenario 2: Ravi Design Studio INV-102 Rs 12,000, confirmed work delivered + payment sent 2026-11-19 -> SOURCE_MATCHED via bank match
        val raviRec = seed.incomeRecords.first { it["_docId"].toString().endsWith("_inc_ravi_inv102") }
        assertEquals("Ravi Design Studio", raviRec["partyName"])
        assertEquals("INV-102", raviRec["invoiceNumber"])
        assertEquals(12000L, raviRec["amountRupees"])
        assertEquals("2026-11-15", raviRec["earnedDate"])
        assertEquals(EvidenceStatus.SOURCE_MATCHED.code, raviRec["evidenceStatus"])
        assertEquals("PAYMENT_SENT", raviRec["paymentStatus"])
        assertEquals("2026-11-19", raviRec["paymentDate"])

        val raviConf = seed.confirmationRequests.first { it["partyName"] == "Ravi Design Studio" }
        assertEquals(true, raviConf["workDelivered"])
        assertEquals(12000L, raviConf["confirmedAmountRupees"])
        assertEquals(true, raviConf["paymentSent"])
        assertEquals("2026-11-19", raviConf["paymentDate"])

        // Verify scenario 3: Kumar Stores INV-103 Rs 5,000, client answered work delivered yes, corrected 4500, payment not yet
        val kumarRec = seed.incomeRecords.first { it["_docId"].toString().endsWith("_inc_kumar_inv103") }
        assertEquals("Kumar Stores", kumarRec["partyName"])
        assertEquals("INV-103", kumarRec["invoiceNumber"])
        assertEquals(5000L, kumarRec["amountRupees"])
        assertEquals("2026-11-14", kumarRec["earnedDate"])
        assertEquals(EvidenceStatus.DISPUTED.code, kumarRec["evidenceStatus"])

        val kumarConf = seed.confirmationRequests.first { it["partyName"] == "Kumar Stores" }
        assertEquals(true, kumarConf["workDelivered"])
        assertEquals(4500L, kumarConf["confirmedAmountRupees"])
        assertEquals(false, kumarConf["paymentSent"])

        // Verify scenario 4: Cash tips Rs 800 (self_declared)
        val cashTips = seed.incomeRecords.first { it["_docId"].toString().endsWith("_inc_cash_tips") }
        assertEquals(800L, cashTips["amountRupees"])
        assertEquals(EvidenceStatus.SELF_DECLARED.code, cashTips["evidenceStatus"])

        // Verify scenario 5: Exact 3 bank credits (RideCo 11000 & Ravi 12000 pre-matched; Meena Rao 3000 possible personal transfer)
        val creditsByNarration = seed.bankCredits.associateBy { it["narration"].toString() }
        assertEquals(11000L, creditsByNarration["RIDECO PAYOUT"]?.get("amountRupees"))
        assertEquals("2026-11-18", creditsByNarration["RIDECO PAYOUT"]?.get("creditDate"))
        assertEquals(EvidenceStatus.SOURCE_MATCHED.code, creditsByNarration["RIDECO PAYOUT"]?.get("evidenceStatus"))
        assertEquals(12000L, creditsByNarration["NEFT RAVI DESIGN STUDIO INV102"]?.get("amountRupees"))
        assertEquals("2026-11-19", creditsByNarration["NEFT RAVI DESIGN STUDIO INV102"]?.get("creditDate"))
        assertEquals(EvidenceStatus.SOURCE_MATCHED.code, creditsByNarration["NEFT RAVI DESIGN STUDIO INV102"]?.get("evidenceStatus"))
        assertEquals(3000L, creditsByNarration["UPI MEENA RAO"]?.get("amountRupees"))
        assertEquals("2026-11-12", creditsByNarration["UPI MEENA RAO"]?.get("creditDate"))
        assertEquals("", creditsByNarration["UPI MEENA RAO"]?.get("matchedIncomeRecordId"))
        assertEquals("POSSIBLE_PERSONAL_TRANSFER", creditsByNarration["UPI MEENA RAO"]?.get("classificationStatus"))

        // Verify scenario 6: RideCo simulated_api record 2-8 Nov 2026, gross 9500, deductions 700, net 8800, payout 2026-11-10 -> SOURCE_VERIFIED_SIMULATED
        val simApiRec = seed.incomeRecords.first { it["_docId"].toString().endsWith("_inc_rideco_sim_api") }
        assertEquals("RideCo", simApiRec["partyName"])
        assertEquals(true, simApiRec["simulatedApi"])
        assertEquals(9500L, simApiRec["grossAmountRupees"])
        assertEquals(700L, simApiRec["deductionsRupees"])
        assertEquals(8800L, simApiRec["netAmountRupees"])
        assertEquals("2026-11-10", simApiRec["earnedDate"])
        assertEquals(EvidenceStatus.SOURCE_VERIFIED_SIMULATED.code, simApiRec["evidenceStatus"])

        // Verify grouped duplicate is counted once in PassportAggregator and targeted in IssueDetector
        val incomeModels = seed.incomeRecords.map { m ->
            IncomeRecord(
                id = m["_docId"].toString(),
                partyId = m["partyId"].toString(),
                partyName = m["partyName"].toString(),
                amountRupees = m["amountRupees"] as Long,
                earnedDate = m["earnedDate"].toString(),
                referenceCode = m["referenceCode"].toString(),
                evidenceStatus = m["evidenceStatus"].toString(),
                simulatedApi = m["simulatedApi"] as Boolean,
                groupedIntoRecordId = m["groupedIntoRecordId"].toString(),
                conflictReason = m["conflictReason"].toString()
            )
        }
        val summary = PassportAggregator.buildSummary(incomeModels, emptyList(), emptyList())
        // 11,000 (RideCo grouped once) + 12,000 (Ravi) + 800 (Cash tips) + 8,800 (RideCo simulated API) = 32,600
        assertEquals(32600L, summary.nonDisputedTotalRupees)
        assertEquals(5000L, summary.disputedTotalRupees)
        assertEquals(5, summary.recordCount)

        val creditModels = seed.bankCredits.map { m ->
            BankCredit(
                id = m["_docId"].toString(),
                creditDate = m["creditDate"].toString(),
                amountRupees = m["amountRupees"] as Long,
                narration = m["narration"].toString(),
                matchedIncomeRecordId = m["matchedIncomeRecordId"].toString(),
                classificationStatus = m["classificationStatus"].toString(),
                evidenceStatus = m["evidenceStatus"].toString()
            )
        }
        val reconModels = seed.reconciliations.map { m ->
            Reconciliation(
                id = m["_docId"].toString(),
                incomeRecordId = m["incomeRecordId"].toString(),
                bankCreditId = m["bankCreditId"].toString(),
                decisionStatus = ReconciliationDecision.CONFIRMED.code
            )
        }
        val detectedIssues = IssueDetector.detectIssues(
            incomeRecords = incomeModels,
            documents = emptyList(),
            bankCredits = creditModels,
            reconciliations = reconModels,
            candidates = emptyList()
        )
        val dupIssue = detectedIssues.firstOrNull { it.type == IssueType.POTENTIAL_DUPLICATE }
        assertTrue("Duplicate issue should be detected", dupIssue != null)
        assertEquals(rideCoDup["_docId"].toString(), dupIssue!!.incomeRecordId)
        assertTrue(detectedIssues.any { it.type == IssueType.DISPUTED_RECORD && it.incomeRecordId == kumarRec["_docId"].toString() })
        assertTrue(detectedIssues.any { it.type == IssueType.UNMATCHED_BANK_CREDIT && it.amountRupees == 3000L })
        assertTrue(detectedIssues.any { it.type == IssueType.UNMATCHED_INCOME && it.incomeRecordId == simApiRec["_docId"].toString() })

        // Verify all demo & sample strings are fictional (no real-world brand names)
        val allText = buildString {
            append(seed.toString())
            DemoDataFactory.sampleDocuments.forEach { append(it.toString()) }
            append(DemoDataFactory.sampleBankCsv.toString())
        }.lowercase()
        listOf("swiggy", "blinkit", "zomato", "uber", "ola", "razorpay", "sbi").forEach { realBrand ->
            assertFalse("Demo data should not contain real brand '$realBrand'", allText.contains(realBrand))
        }
    }
}



