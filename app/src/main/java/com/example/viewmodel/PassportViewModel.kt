package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ExtractedDocumentDraft
import com.example.data.GeminiDocumentReader
import com.example.data.PassportRepository
import com.example.domain.rules.BankCsvParser
import com.example.domain.rules.CounterpartyConfirmationRules
import com.example.domain.rules.EvidenceClassifier
import com.example.domain.rules.FileIngestionValidator
import com.example.domain.rules.FileValidationResult
import com.example.domain.rules.IssueDetector
import com.example.domain.rules.MatchCandidate
import com.example.domain.rules.ParsedBankCsvCredit
import com.example.domain.rules.PassportAggregator
import com.example.domain.rules.PassportIssue
import com.example.domain.rules.PassportSummary
import com.example.domain.rules.RawDocumentExtraction
import com.example.domain.rules.ReconciliationEngine
import com.example.domain.rules.ReviewFormValues
import com.example.domain.rules.ReviewerReport
import com.example.domain.rules.SourceUploadType
import com.example.domain.rules.SyntheticSampleBankCsv
import com.example.domain.rules.SyntheticSampleDocument
import com.example.domain.rules.UploadFileKind
import com.example.model.ActorRole
import com.example.model.AuditLogEntry
import com.example.model.BankCredit
import com.example.model.ConfirmationRequest
import com.example.model.ConfirmationStatus
import com.example.model.DocumentSourceKind
import com.example.model.DocumentType
import com.example.model.EvidenceStatus
import com.example.model.IncomeDocument
import com.example.model.IncomeRecord
import com.example.model.Party
import com.example.model.PartyRole
import com.example.model.PartyType
import com.example.model.PayoutCycle
import com.example.model.Reconciliation
import com.example.model.ReviewStatus
import com.example.model.ShareLink
import com.example.model.WorkRelationship
import com.example.model.WorkerProfile
import com.example.reconciliation.BankCreditClassification
import com.example.reconciliation.ReconciliationResult
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface ExtractionUiState {
    data object Idle : ExtractionUiState
    data class Reading(
        val sourceTitle: String,
        val stageLabel: String = "Reading document…"
    ) : ExtractionUiState
    data class ReadyForReview(
        val draft: ExtractedDocumentDraft,
        val savedDocumentId: String? = null
    ) : ExtractionUiState
    data class CsvImportSuccess(
        val fileName: String,
        val importedCount: Int,
        val skippedCount: Int,
        val rowErrorCount: Int,
        val totalRupees: Long,
        val previewCredits: List<ParsedBankCsvCredit>
    ) : ExtractionUiState
    data class Error(
        val message: String,
        val canRetry: Boolean = false,
        val isDuplicateFile: Boolean = false
    ) : ExtractionUiState
}

private sealed interface RetryableIngestion {
    data class UploadedFile(
        val uri: Uri,
        val sourceType: SourceUploadType
    ) : RetryableIngestion

    data class SampleDoc(val sample: SyntheticSampleDocument) : RetryableIngestion
    data class SampleCsv(val sample: SyntheticSampleBankCsv) : RetryableIngestion
}

data class PassportWorkspaceData(
    val allWorkers: List<WorkerProfile> = emptyList(),
    val activeWorker: WorkerProfile? = null,
    val parties: List<Party> = emptyList(),
    val relationships: List<WorkRelationship> = emptyList(),
    val incomeRecords: List<IncomeRecord> = emptyList(),
    val documents: List<IncomeDocument> = emptyList(),
    val bankCredits: List<BankCredit> = emptyList(),
    val reconciliations: List<Reconciliation> = emptyList(),
    val confirmationRequests: List<ConfirmationRequest> = emptyList(),
    val shareLinks: List<ShareLink> = emptyList(),
    val auditLogs: List<AuditLogEntry> = emptyList(),
    val summary: PassportSummary = PassportSummary(0L, 0L, 0, emptyList(), emptyList()),
    val reconciliationResult: ReconciliationResult? = null,
    val matchCandidates: List<MatchCandidate> = emptyList(),
    val issues: List<PassportIssue> = emptyList(),
    val allIssueItems: List<PassportIssue> = emptyList()
) {
    val pendingDocuments: List<IncomeDocument>
        get() = documents.filter { ReviewStatus.fromCode(it.reviewStatus) == ReviewStatus.PENDING_REVIEW }
}

private data class CoreCollectionsBundle(
    val workers: List<WorkerProfile>,
    val parties: List<Party>,
    val relationships: List<WorkRelationship>,
    val incomeRecords: List<IncomeRecord>,
    val documents: List<IncomeDocument>
)

private data class SupportCollectionsBundle(
    val bankCredits: List<BankCredit>,
    val reconciliations: List<Reconciliation>,
    val confirmationRequests: List<ConfirmationRequest>,
    val shareLinks: List<ShareLink>,
    val auditLogs: List<AuditLogEntry>
)

data class CreatedConfirmationLinkInfo(
    val token: String,
    val link: String,
    val partyName: String,
    val amountRupees: Long,
    val expiresInDays: Int = 7
)

class PassportViewModel(
    private val repository: PassportRepository,
    private val documentReader: GeminiDocumentReader,
    private val currentUserId: String
) : ViewModel() {

    private val _activeWorkerId = MutableStateFlow<String?>(null)
    val activeWorkerId: StateFlow<String?> = _activeWorkerId.asStateFlow()

    private val _extractionState = MutableStateFlow<ExtractionUiState>(ExtractionUiState.Idle)
    val extractionState: StateFlow<ExtractionUiState> = _extractionState.asStateFlow()

    private val _selectedShareLinkId = MutableStateFlow<String?>(null)
    val selectedShareLinkId: StateFlow<String?> = _selectedShareLinkId.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _createdConfirmationLink = MutableStateFlow<CreatedConfirmationLinkInfo?>(null)
    val createdConfirmationLink: StateFlow<CreatedConfirmationLinkInfo?> = _createdConfirmationLink.asStateFlow()

    private val syncedConfirmationIds = mutableSetOf<String>()

    private var lastRetryableIngestion: RetryableIngestion? = null

    private val coreFlow: Flow<CoreCollectionsBundle> = combine(
        repository.observeWorkers(currentUserId),
        repository.observeParties(currentUserId),
        repository.observeRelationships(currentUserId),
        repository.observeIncomeRecords(currentUserId),
        repository.observeDocuments(currentUserId)
    ) { workers, parties, relationships, records, documents ->
        CoreCollectionsBundle(workers, parties, relationships, records, documents)
    }

    private val supportFlow: Flow<SupportCollectionsBundle> = combine(
        repository.observeBankCredits(currentUserId),
        repository.observeReconciliations(currentUserId),
        repository.observeConfirmationRequests(currentUserId),
        repository.observeShareLinks(currentUserId),
        repository.observeAuditLogs(currentUserId)
    ) { credits, reconciliations, confirmations, shareLinks, auditLogs ->
        SupportCollectionsBundle(credits, reconciliations, confirmations, shareLinks, auditLogs)
    }

    val workspaceState: StateFlow<UiState<PassportWorkspaceData>> = combine(
        coreFlow,
        supportFlow,
        _activeWorkerId
    ) { core, support, chosenWorkerId ->
        val sortedWorkers = core.workers.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        val activeWorker = chosenWorkerId?.let { id -> sortedWorkers.firstOrNull { it.id == id } }

        if (activeWorker == null) {
            PassportWorkspaceData(allWorkers = sortedWorkers)
        } else {
            val wid = activeWorker.id
            val parties = core.parties.filter { it.workerId == wid }.sortedBy { it.name.lowercase() }
            val relationships = core.relationships.filter { it.workerId == wid }
            val records = core.incomeRecords
                .filter { it.workerId == wid }
                .sortedByDescending { it.earnedDate }
            val docs = core.documents
                .filter { it.workerId == wid }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            val credits = support.bankCredits
                .filter { it.workerId == wid }
                .sortedByDescending { it.creditDate }
            val reconciliations = support.reconciliations.filter { it.workerId == wid }
            val confirmations = support.confirmationRequests
                .filter { it.workerId == wid }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            val shareLinks = support.shareLinks
                .filter { it.workerId == wid }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            val auditLogs = support.auditLogs
                .filter { it.workerId == wid }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }

            val reconciliationResult = ReconciliationEngine.reconcileWorkspace(
                workerDisplayName = activeWorker.displayName,
                incomeRecords = records,
                documents = docs,
                parties = parties,
                bankCredits = credits,
                confirmations = confirmations,
                relationships = relationships,
                reconciliations = reconciliations
            )
            val candidates = ReconciliationEngine.extractMatchCandidates(
                result = reconciliationResult,
                incomeRecords = records,
                bankCredits = credits,
                includeNewStrong = false
            )
            val allIssueItems = IssueDetector.detectIssues(
                incomeRecords = records,
                documents = docs,
                bankCredits = credits,
                reconciliations = reconciliations,
                candidates = candidates,
                reconciliationResult = reconciliationResult,
                confirmationRequests = confirmations,
                includeSuccessfulMatches = true
            )
            val actionableIssues = allIssueItems.filter { it.type.isActionable }
            val summary = PassportAggregator.buildSummary(
                incomeRecords = records,
                parties = parties,
                relationships = relationships
            )

            PassportWorkspaceData(
                allWorkers = sortedWorkers,
                activeWorker = activeWorker,
                parties = parties,
                relationships = relationships,
                incomeRecords = records,
                documents = docs,
                bankCredits = credits,
                reconciliations = reconciliations,
                confirmationRequests = confirmations,
                shareLinks = shareLinks,
                auditLogs = auditLogs,
                summary = summary,
                reconciliationResult = reconciliationResult,
                matchCandidates = candidates,
                issues = actionableIssues,
                allIssueItems = allIssueItems
            )
        }
    }
        .map<PassportWorkspaceData, UiState<PassportWorkspaceData>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing passport collections", rootError)
            emit(UiState.Error(rootError.message ?: "Could not load your passport data."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun dismissCreatedConfirmationLink() {
        _createdConfirmationLink.value = null
    }

    fun clearActiveWorker() {
        _activeWorkerId.value = null
        _extractionState.value = ExtractionUiState.Idle
        _selectedShareLinkId.value = null
        _createdConfirmationLink.value = null
        syncedConfirmationIds.clear()
    }

    fun selectExistingWorker(workerId: String) {
        _activeWorkerId.value = workerId
        _extractionState.value = ExtractionUiState.Idle
        _createdConfirmationLink.value = null
        syncedConfirmationIds.clear()
    }

    fun openAshaDemoProfile(forceReset: Boolean = false, onReady: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.ensureAshaDemoProfile(currentUserId, forceReset = forceReset)
            _isBusy.value = false
            result.onSuccess { wid ->
                _activeWorkerId.value = wid
                _statusMessage.value = if (forceReset) {
                    "Asha Rao demo data reset to fresh synthetic records."
                } else {
                    "Loaded Asha Rao synthetic demo passport."
                }
                onReady()
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not prepare Asha Rao demo profile."
            }
        }
    }

    fun createAndSelectCustomWorker(
        displayName: String,
        city: String,
        primaryWorkType: String,
        onReady: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createCustomWorker(currentUserId, displayName, city, primaryWorkType)
            _isBusy.value = false
            result.onSuccess { wid ->
                _activeWorkerId.value = wid
                _statusMessage.value = "Created passport for ${displayName.trim()}."
                onReady()
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not create worker profile."
            }
        }
    }

    fun addParty(
        name: String,
        partyType: PartyType,
        roles: List<PartyRole>,
        phone: String = "",
        email: String = "",
        note: String = "",
        registrationNumber: String = "",
        roleTitle: String = "",
        payoutCycle: PayoutCycle = PayoutCycle.WEEKLY,
        startedOn: String = "2026-11-01",
        onCreated: (partyId: String, partyName: String) -> Unit = { _, _ -> }
    ) {
        val wid = _activeWorkerId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val cleanName = name.trim()
            val result = repository.addPartyAndRelationship(
                userId = currentUserId,
                workerId = wid,
                name = cleanName,
                partyType = partyType,
                roles = roles,
                phone = phone,
                email = email,
                note = note,
                registrationNumber = registrationNumber,
                roleTitle = roleTitle,
                payoutCycle = payoutCycle,
                startedOn = startedOn
            )
            _isBusy.value = false
            result.onSuccess { partyId ->
                _statusMessage.value = "Added party '$cleanName' (Added by worker · Worker declared)."
                onCreated(partyId, cleanName)
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not add work source."
            }
        }
    }

    fun addSelfDeclaredIncome(
        partyId: String,
        partyName: String,
        amountRupees: Long,
        earnedDate: String,
        periodLabel: String,
        referenceCode: String,
        notes: String,
        onSaved: () -> Unit = {}
    ) {
        val wid = _activeWorkerId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.addSelfDeclaredIncome(
                userId = currentUserId,
                workerId = wid,
                partyId = partyId,
                partyName = partyName,
                amountRupees = amountRupees,
                earnedDate = earnedDate,
                periodLabel = periodLabel,
                referenceCode = referenceCode,
                notes = notes
            )
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.value = "Saved as Self-declared."
                onSaved()
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not save income entry."
            }
        }
    }

    fun addBankCredit(
        bankLabel: String,
        creditDate: String,
        amountRupees: Long,
        narration: String,
        utrReference: String
    ) {
        val wid = _activeWorkerId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.addBankCredit(
                userId = currentUserId,
                workerId = wid,
                bankLabel = bankLabel,
                creditDate = creditDate,
                amountRupees = amountRupees,
                narration = narration,
                utrReference = utrReference
            )
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.value = "Bank credit added. Matching rules updated."
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not add bank credit."
            }
        }
    }

    // --- File Upload (PDF / CSV / Image <= 10 MB), Hash Dedup, CSV Code Parse & Gemini AI ---

    private suspend fun isDuplicateFileHash(workerId: String, fileHash: String): Boolean {
        val currentWorkspace = (workspaceState.value as? UiState.Success)?.data
        if (currentWorkspace != null &&
            FileIngestionValidator.isDuplicateHashForWorker(fileHash, currentWorkspace.documents, workerId)
        ) {
            return true
        }
        return repository.hasDocumentWithFileHash(currentUserId, workerId, fileHash).getOrDefault(false)
    }

    fun ingestUploadedFile(
        context: Context,
        uri: Uri,
        selectedSourceType: SourceUploadType,
        onReadyForReview: () -> Unit
    ) {
        val wid = _activeWorkerId.value ?: return
        lastRetryableIngestion = RetryableIngestion.UploadedFile(uri, selectedSourceType)

        viewModelScope.launch {
            _extractionState.value = ExtractionUiState.Reading(
                sourceTitle = selectedSourceType.label,
                stageLabel = "Checking file type, size, and hash…"
            )

            val readOutcome = withContext(Dispatchers.IO) {
                readFileMetadataAndBytes(context, uri)
            }

            if (readOutcome.isFailure) {
                _extractionState.value = ExtractionUiState.Error(
                    message = readOutcome.exceptionOrNull()?.message
                        ?: "Could not read the selected file. Please choose another PDF, CSV, or image.",
                    canRetry = true
                )
                return@launch
            }

            val loadedFile = readOutcome.getOrThrow()
            val validation = FileIngestionValidator.validateFile(
                fileName = loadedFile.fileName,
                mimeType = loadedFile.mimeType,
                sizeBytes = loadedFile.sizeBytes,
                selectedSourceType = selectedSourceType
            )

            if (validation is FileValidationResult.Invalid) {
                _extractionState.value = ExtractionUiState.Error(
                    message = validation.message,
                    canRetry = false
                )
                return@launch
            }

            val valid = validation as FileValidationResult.Valid
            val fileHash = withContext(Dispatchers.Default) {
                FileIngestionValidator.computeSha256Hex(loadedFile.bytes)
            }

            // Duplicate file hash check for this worker
            if (isDuplicateFileHash(wid, fileHash)) {
                _extractionState.value = ExtractionUiState.Error(
                    message = FileIngestionValidator.DUPLICATE_FILE_ERROR,
                    canRetry = false,
                    isDuplicateFile = true
                )
                return@launch
            }

            when (valid.fileKind) {
                UploadFileKind.CSV -> {
                    _extractionState.value = ExtractionUiState.Reading(
                        sourceTitle = loadedFile.fileName,
                        stageLabel = "Parsing bank statement CSV in code (no AI)…"
                    )
                    val csvText = loadedFile.bytes.toString(Charsets.UTF_8)
                    val parsed = withContext(Dispatchers.Default) {
                        BankCsvParser.parse(csvText, loadedFile.fileName)
                    }
                    if (parsed.credits.isEmpty()) {
                        val detail = parsed.rowErrors.firstOrNull()
                            ?: "No valid positive bank credit rows were found in this CSV."
                        _extractionState.value = ExtractionUiState.Error(
                            message = detail,
                            canRetry = false
                        )
                        return@launch
                    }

                    val importResult = repository.importBankCreditsFromCsv(
                        userId = currentUserId,
                        workerId = wid,
                        fileName = loadedFile.fileName,
                        fileHash = fileHash,
                        bankLabel = parsed.bankLabelHint,
                        credits = parsed.credits
                    )
                    importResult.onSuccess { count ->
                        val total = parsed.credits.sumOf { it.amountRupees }
                        _extractionState.value = ExtractionUiState.CsvImportSuccess(
                            fileName = loadedFile.fileName,
                            importedCount = count,
                            skippedCount = parsed.skippedDebitOrEmptyRows,
                            rowErrorCount = parsed.rowErrors.size,
                            totalRupees = total,
                            previewCredits = parsed.credits.take(5)
                        )
                        _statusMessage.value = "Imported $count bank credit(s) from CSV (no AI). Matching rules updated."
                    }.onFailure { err ->
                        _extractionState.value = ExtractionUiState.Error(
                            message = err.message ?: "Could not save parsed bank CSV credits.",
                            canRetry = true
                        )
                    }
                }

                UploadFileKind.PDF, UploadFileKind.IMAGE -> {
                    _extractionState.value = ExtractionUiState.Reading(
                        sourceTitle = loadedFile.fileName,
                        stageLabel = if (valid.fileKind == UploadFileKind.PDF) {
                            "Reading PDF with Gemini AI (gemini-3.8-flash)…"
                        } else {
                            "Reading image with Gemini AI (gemini-3.8-flash)…"
                        }
                    )
                    val aiResult = documentReader.readUploadedFile(
                        bytes = loadedFile.bytes,
                        fileKind = valid.fileKind,
                        mimeType = valid.normalizedMimeType,
                        fileName = loadedFile.fileName,
                        selectedSourceType = valid.effectiveSourceType,
                        fileHash = fileHash,
                        previewUri = if (valid.fileKind == UploadFileKind.IMAGE) uri else null
                    )
                    aiResult.onSuccess { draft ->
                        // Keep draft strictly in memory — nothing writes to Firestore until worker taps Confirm
                        _extractionState.value = ExtractionUiState.ReadyForReview(
                            draft = draft,
                            savedDocumentId = null
                        )
                        onReadyForReview()
                    }.onFailure { err ->
                        _extractionState.value = ExtractionUiState.Error(
                            message = err.message ?: "AI could not read this file. Please try again.",
                            canRetry = true
                        )
                    }
                }
            }
        }
    }

    fun readSampleDocumentWithAi(sample: SyntheticSampleDocument, onReadyForReview: () -> Unit) {
        val wid = _activeWorkerId.value ?: return
        lastRetryableIngestion = RetryableIngestion.SampleDoc(sample)

        viewModelScope.launch {
            _extractionState.value = ExtractionUiState.Reading(
                sourceTitle = sample.title,
                stageLabel = "Checking file hash & reading with Gemini AI…"
            )
            val fileHash = FileIngestionValidator.computeSha256Hex(sample.rawDocumentText.toByteArray(Charsets.UTF_8))
            if (isDuplicateFileHash(wid, fileHash)) {
                _extractionState.value = ExtractionUiState.Error(
                    message = FileIngestionValidator.DUPLICATE_FILE_ERROR,
                    canRetry = false,
                    isDuplicateFile = true
                )
                return@launch
            }

            val aiResult = documentReader.readSampleDocument(sample, fileHash)
            aiResult.onSuccess { draft ->
                // Keep draft strictly in memory — nothing writes to Firestore until worker taps Confirm
                _extractionState.value = ExtractionUiState.ReadyForReview(
                    draft = draft,
                    savedDocumentId = null
                )
                onReadyForReview()
            }.onFailure { err ->
                _extractionState.value = ExtractionUiState.Error(
                    message = err.message ?: "AI could not read the sample document.",
                    canRetry = true
                )
            }
        }
    }

    fun importSampleBankCsv(sample: SyntheticSampleBankCsv) {
        val wid = _activeWorkerId.value ?: return
        lastRetryableIngestion = RetryableIngestion.SampleCsv(sample)

        viewModelScope.launch {
            _extractionState.value = ExtractionUiState.Reading(
                sourceTitle = sample.fileName,
                stageLabel = "Computing hash & parsing bank CSV in code (no AI)…"
            )
            val bytes = sample.csvText.toByteArray(Charsets.UTF_8)
            val fileHash = FileIngestionValidator.computeSha256Hex(bytes)
            if (isDuplicateFileHash(wid, fileHash)) {
                _extractionState.value = ExtractionUiState.Error(
                    message = FileIngestionValidator.DUPLICATE_FILE_ERROR,
                    canRetry = false,
                    isDuplicateFile = true
                )
                return@launch
            }

            val parsed = BankCsvParser.parse(sample.csvText, sample.fileName)
            if (parsed.credits.isEmpty()) {
                _extractionState.value = ExtractionUiState.Error(
                    message = parsed.rowErrors.firstOrNull() ?: "No credit rows found in sample CSV.",
                    canRetry = false
                )
                return@launch
            }

            val result = repository.importBankCreditsFromCsv(
                userId = currentUserId,
                workerId = wid,
                fileName = sample.fileName,
                fileHash = fileHash,
                bankLabel = "SBI Savings ••4821 (CSV)",
                credits = parsed.credits
            )
            result.onSuccess { count ->
                val total = parsed.credits.sumOf { it.amountRupees }
                _extractionState.value = ExtractionUiState.CsvImportSuccess(
                    fileName = sample.fileName,
                    importedCount = count,
                    skippedCount = parsed.skippedDebitOrEmptyRows,
                    rowErrorCount = parsed.rowErrors.size,
                    totalRupees = total,
                    previewCredits = parsed.credits.take(5)
                )
                _statusMessage.value = "Imported $count bank credit(s) from CSV (no AI)."
            }.onFailure { err ->
                _extractionState.value = ExtractionUiState.Error(
                    message = err.message ?: "Could not save CSV bank credits.",
                    canRetry = true
                )
            }
        }
    }

    fun retryLastIngestion(context: Context, onReadyForReview: () -> Unit) {
        when (val last = lastRetryableIngestion) {
            is RetryableIngestion.UploadedFile -> ingestUploadedFile(context, last.uri, last.sourceType, onReadyForReview)
            is RetryableIngestion.SampleDoc -> readSampleDocumentWithAi(last.sample, onReadyForReview)
            is RetryableIngestion.SampleCsv -> importSampleBankCsv(last.sample)
            null -> _extractionState.value = ExtractionUiState.Idle
        }
    }

    fun openPendingDocumentForReview(doc: IncomeDocument) {
        val docType = DocumentType.fromCode(doc.documentType)
        val rawFromDoc = RawDocumentExtraction(
            documentType = doc.documentType.ifBlank { null },
            platform = if (docType == DocumentType.PLATFORM_STATEMENT) doc.extractedPartyName.ifBlank { null } else null,
            payer = if (docType != DocumentType.PLATFORM_STATEMENT && docType != DocumentType.INVOICE) doc.extractedPartyName.ifBlank { null } else null,
            clientName = if (docType == DocumentType.INVOICE) doc.extractedPartyName.ifBlank { null } else null,
            invoiceNumber = if (docType == DocumentType.INVOICE) doc.extractedReference.ifBlank { null } else null,
            recordDate = doc.extractedDate.ifBlank { null },
            periodStart = null,
            periodEnd = null,
            grossAmountRaw = null,
            deductionsRaw = null,
            netAmountRaw = if (docType != DocumentType.INVOICE && doc.extractedAmountRupees > 0L) doc.extractedAmountRupees.toString() else null,
            invoiceAmountRaw = if (docType == DocumentType.INVOICE && doc.extractedAmountRupees > 0L) doc.extractedAmountRupees.toString() else null,
            currency = "INR",
            reference = doc.extractedReference.ifBlank { null },
            paymentStatusStated = null,
            notes = doc.extractionNotes.ifBlank { null }
        )
        _extractionState.value = ExtractionUiState.ReadyForReview(
            draft = ExtractedDocumentDraft(
                title = doc.title,
                sourceKind = DocumentSourceKind.fromCode(doc.sourceKind),
                documentType = docType,
                fileHash = doc.fileHash,
                fileName = doc.title,
                partyName = doc.extractedPartyName,
                amountRupees = doc.extractedAmountRupees,
                earnedDate = doc.extractedDate,
                periodLabel = "",
                referenceCode = doc.extractedReference,
                extractionNotes = doc.extractionNotes,
                confidenceHint = "Review every value",
                previewUri = null,
                aiOriginalRaw = rawFromDoc
            ),
            savedDocumentId = doc.id
        )
    }

    fun dismissExtractionState() {
        _extractionState.value = ExtractionUiState.Idle
    }

    private data class LoadedFilePayload(
        val fileName: String,
        val mimeType: String?,
        val sizeBytes: Long,
        val bytes: ByteArray
    )

    private fun readFileMetadataAndBytes(context: Context, uri: Uri): Result<LoadedFilePayload> {
        return try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
            var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "uploaded_file"
            var reportedSize = -1L

            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        reportedSize = cursor.getLong(sizeIdx)
                    }
                }
            }

            // Fail fast before allocating memory if provider already reports > 10 MB
            if (reportedSize > FileIngestionValidator.MAX_UPLOAD_BYTES) {
                return Result.success(
                    LoadedFilePayload(
                        fileName = displayName,
                        mimeType = mimeType,
                        sizeBytes = reportedSize,
                        bytes = ByteArray(0)
                    )
                )
            }

            val stream = resolver.openInputStream(uri)
                ?: return Result.failure(IllegalStateException("Could not open the selected file."))

            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var totalRead = 0L
            val hardCap = FileIngestionValidator.MAX_UPLOAD_BYTES + 1L

            stream.use { input ->
                while (true) {
                    val read = input.read(chunk)
                    if (read <= 0) break
                    totalRead += read
                    if (totalRead <= hardCap) {
                        buffer.write(chunk, 0, read)
                    } else {
                        break
                    }
                }
            }

            val bytes = buffer.toByteArray()
            val actualSize = if (totalRead > FileIngestionValidator.MAX_UPLOAD_BYTES) totalRead else bytes.size.toLong()
            Result.success(
                LoadedFilePayload(
                    fileName = displayName,
                    mimeType = mimeType,
                    sizeBytes = actualSize,
                    bytes = bytes
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read uploaded file URI", e)
            Result.failure(Exception("Could not open that file. Please try selecting it again."))
        }
    }

    fun confirmReviewedDocument(
        existingDocumentId: String?,
        draft: ExtractedDocumentDraft,
        form: ReviewFormValues,
        workGiverPartyId: String,
        workGiverPartyName: String,
        payerPartyId: String,
        payerPartyName: String,
        workerConfirmed: Boolean,
        onSaved: () -> Unit
    ) {
        val wid = _activeWorkerId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.confirmDocumentAndCreateIncome(
                userId = currentUserId,
                workerId = wid,
                existingDocumentId = existingDocumentId,
                draft = draft,
                form = form,
                workGiverPartyId = workGiverPartyId,
                workGiverPartyName = workGiverPartyName,
                payerPartyId = payerPartyId,
                payerPartyName = payerPartyName,
                workerConfirmed = workerConfirmed
            )
            _isBusy.value = false
            result.onSuccess {
                _extractionState.value = ExtractionUiState.Idle
                _statusMessage.value = "Confirmed and saved as Document-extracted (upload, edit & confirm logged)."
                onSaved()
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not save confirmed record."
            }
        }
    }

    fun discardDraft(savedDocumentId: String?) {
        if (savedDocumentId.isNullOrBlank()) {
            _extractionState.value = ExtractionUiState.Idle
            _statusMessage.value = "Draft discarded. Nothing was saved."
            return
        }
        val wid = _activeWorkerId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.discardPendingDocument(currentUserId, wid, savedDocumentId)
            _isBusy.value = false
            result.onSuccess {
                _extractionState.value = ExtractionUiState.Idle
                _statusMessage.value = "Pending draft discarded."
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not discard draft."
            }
        }
    }

    // --- Reconciliation & Issue Actions ---

    fun confirmMatch(candidate: MatchCandidate) {
        val wid = _activeWorkerId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.confirmReconciliationMatch(currentUserId, wid, candidate)
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.value = "Match confirmed -> Source-matched."
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not confirm match."
            }
        }
    }

    fun rejectMatch(candidate: MatchCandidate) {
        val wid = _activeWorkerId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.rejectReconciliationMatch(currentUserId, wid, candidate)
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.value = "Suggested match rejected."
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not reject match."
            }
        }
    }

    fun classifyBankCredit(credit: BankCredit, classification: BankCreditClassification) {
        val wid = _activeWorkerId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.classifyBankCredit(
                userId = currentUserId,
                workerId = wid,
                credit = credit,
                classification = classification
            )
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.value = when (classification) {
                    BankCreditClassification.WORK_INCOME -> "Classified as Work income and reconciled."
                    BankCreditClassification.PERSONAL_TRANSFER -> "Marked as Personal transfer (excluded from work income)."
                    BankCreditClassification.NON_WORK_IGNORED -> "Marked as Non-work / ignored."
                    else -> "Bank credit classification updated."
                }
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not classify bank credit."
            }
        }
    }

    fun linkBankCreditToExistingIncome(credit: BankCredit, incomeRecord: IncomeRecord) {
        val wid = _activeWorkerId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.linkBankCreditToExistingIncome(
                userId = currentUserId,
                workerId = wid,
                credit = credit,
                incomeRecord = incomeRecord
            )
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.value = "Linked bank credit to ${incomeRecord.partyName} and logged to audit trail."
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not link bank credit to income."
            }
        }
    }

    fun createIncomeAndLinkBankCredit(
        credit: BankCredit,
        partyName: String,
        earnedDate: String,
        periodLabel: String,
        notes: String
    ) {
        val wid = _activeWorkerId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createIncomeAndLinkBankCredit(
                userId = currentUserId,
                workerId = wid,
                credit = credit,
                partyName = partyName,
                earnedDate = earnedDate,
                periodLabel = periodLabel,
                notes = notes
            )
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.value = "Created work income and matched bank credit -> Source-matched."
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not create linked work income."
            }
        }
    }

    fun ignoreDuplicateRecord(record: IncomeRecord) {
        val wid = _activeWorkerId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.ignoreDuplicateRecord(
                userId = currentUserId,
                workerId = wid,
                record = record
            )
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.value = "Duplicate alert ignored; copy kept archived and counted once."
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not ignore duplicate alert."
            }
        }
    }

    fun markRecordDisputed(record: IncomeRecord, reason: String) {
        val wid = _activeWorkerId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateIncomeEvidenceStatus(
                userId = currentUserId,
                workerId = wid,
                incomeRecordId = record.id,
                newStatus = EvidenceStatus.DISPUTED,
                note = reason.ifBlank { "Marked 'This is wrong' by worker for review." },
                actorRole = ActorRole.WORKER
            )
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.value = "Marked Disputed (both versions kept, never deleted)."
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not mark record disputed."
            }
        }
    }

    fun clearRecordDispute(record: IncomeRecord, hasBankMatch: Boolean) {
        val wid = _activeWorkerId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val nextStatus = EvidenceClassifier.onDisputeCleared(
                hasConfirmedBankMatch = hasBankMatch,
                hasConfirmedDocument = record.documentId.isNotBlank(),
                simulatedApi = record.simulatedApi
            )
            val result = repository.updateIncomeEvidenceStatus(
                userId = currentUserId,
                workerId = wid,
                incomeRecordId = record.id,
                newStatus = nextStatus,
                note = "Dispute resolved by worker -> ${nextStatus.label}.",
                actorRole = ActorRole.WORKER
            )
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.value = "Dispute cleared; reconciliation rules updated."
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not clear dispute."
            }
        }
    }

    fun removeDuplicateRecord(record: IncomeRecord) {
        // Non-destructive path preferred: route to ignoreDuplicateRecord so records are archived, not deleted.
        ignoreDuplicateRecord(record)
    }

    // --- Counterparty Confirmation (Tokenized Public Flow + Legacy Simulation) ---

    fun sendConfirmationRequest(record: IncomeRecord) {
        val wid = _activeWorkerId.value ?: return
        val workspace = (workspaceState.value as? UiState.Success)?.data
        val workerName = workspace?.activeWorker?.displayName.orEmpty()
        val existingCount = workspace?.confirmationRequests?.count { it.incomeRecordId == record.id } ?: 0

        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createConfirmationRequest(
                userId = currentUserId,
                workerId = wid,
                record = record,
                workerDisplayName = workerName,
                existingRequestCountForRecord = existingCount
            )
            _isBusy.value = false
            result.onSuccess { token ->
                _createdConfirmationLink.value = CreatedConfirmationLinkInfo(
                    token = token,
                    link = CounterpartyConfirmationRules.buildConfirmationLink(token),
                    partyName = record.partyName.ifBlank { "Client" },
                    amountRupees = record.amountRupees
                )
                _statusMessage.value = "7-day client confirmation link created for ${record.partyName}."
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not create confirmation request."
            }
        }
    }

    fun syncPendingCounterpartyResponses(unsynced: List<ConfirmationRequest>) {
        val wid = _activeWorkerId.value ?: return
        val toSync = unsynced.filter { req -> syncedConfirmationIds.add(req.id) }
        if (toSync.isEmpty()) return

        viewModelScope.launch {
            val result = repository.syncCounterpartyResponsesToLedger(
                userId = currentUserId,
                workerId = wid,
                unsyncedRequests = toSync
            )
            result.onSuccess { count ->
                if (count > 0) {
                    _statusMessage.value = "Applied $count client confirmation response(s) to your passport."
                }
            }.onFailure { err ->
                toSync.forEach { syncedConfirmationIds.remove(it.id) }
                Log.w(TAG, "Could not sync counterparty responses", err)
            }
        }
    }

    fun acceptClientCorrection(
        request: ConfirmationRequest,
        record: IncomeRecord,
        correctedAmountRupees: Long
    ) {
        val wid = _activeWorkerId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.acceptClientCorrection(
                userId = currentUserId,
                workerId = wid,
                request = request,
                record = record,
                correctedAmountRupees = correctedAmountRupees
            )
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.value = "Accepted corrected amount; dispute cleared and logged."
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not accept client correction."
            }
        }
    }

    fun disputeClientResponse(
        request: ConfirmationRequest,
        record: IncomeRecord,
        workerNote: String
    ) {
        val wid = _activeWorkerId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.disputeClientResponse(
                userId = currentUserId,
                workerId = wid,
                request = request,
                record = record,
                workerNote = workerNote
            )
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.value = "Client response marked Disputed (both versions kept, logged)."
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not dispute client response."
            }
        }
    }

    fun simulatePartyDecision(request: ConfirmationRequest, confirm: Boolean) {
        val wid = _activeWorkerId.value ?: return
        val decision = if (confirm) ConfirmationStatus.CONFIRMED_SIMULATED else ConfirmationStatus.DISPUTED
        val note = if (confirm) {
            "Simulated confirmation: ${request.partyName} confirmed work delivered & payment sent."
        } else {
            "Simulated dispute: ${request.partyName} flagged a mismatch on this payment."
        }
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.resolveConfirmationRequestSimulated(
                userId = currentUserId,
                workerId = wid,
                request = request,
                decision = decision,
                responseNote = note
            )
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.value = if (confirm) {
                    "Relationship & payment status updated (evidence label unchanged)."
                } else {
                    "Dispute recorded -> Disputed."
                }
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not record simulated response."
            }
        }
    }

    // --- Share Links & Reviewer View ---

    fun createShareLink(
        worker: WorkerProfile,
        recipientLabel: String,
        dateRangeLabel: String,
        includedPartyIds: List<String>,
        includedEvidenceLevels: List<String>,
        maskBankRefs: Boolean,
        expiresOn: String,
        incomeRecords: List<IncomeRecord>,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createShareLink(
                userId = currentUserId,
                worker = worker,
                recipientLabel = recipientLabel,
                dateRangeLabel = dateRangeLabel,
                includedPartyIds = includedPartyIds,
                includedEvidenceLevels = includedEvidenceLevels,
                maskBankRefs = maskBankRefs,
                expiresOn = expiresOn,
                incomeRecords = incomeRecords
            )
            _isBusy.value = false
            result.onSuccess { shareId ->
                _selectedShareLinkId.value = shareId
                _statusMessage.value = "Read-only share link created by your consent."
                onCreated(shareId)
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not create share link."
            }
        }
    }

    fun revokeShareLink(shareLink: ShareLink) {
        val wid = _activeWorkerId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.revokeShareLink(currentUserId, wid, shareLink)
            _isBusy.value = false
            result.onSuccess {
                _statusMessage.value = "Share link ${shareLink.shareCode} revoked."
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Could not revoke share link."
            }
        }
    }

    fun selectShareLinkForPreview(shareLinkId: String) {
        _selectedShareLinkId.value = shareLinkId
    }

    fun buildReviewerReport(workspace: PassportWorkspaceData, requestedId: String?): ReviewerReport? {
        val link = requestedId?.let { id -> workspace.shareLinks.firstOrNull { it.id == id } }
            ?: workspace.shareLinks.firstOrNull()
            ?: return null
        return PassportAggregator.buildReviewerReport(
            shareLink = link,
            allIncomeRecords = workspace.incomeRecords,
            allBankCredits = workspace.bankCredits
        )
    }

    private companion object {
        const val TAG = "PassportViewModel"
    }
}
