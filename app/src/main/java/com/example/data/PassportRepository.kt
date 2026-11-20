package com.example.data

import android.content.Context
import com.example.R
import com.example.domain.rules.CounterpartyConfirmationRules
import com.example.domain.rules.DemoDataFactory
import com.example.domain.rules.EvidenceClassifier
import com.example.domain.rules.MatchCandidate
import com.example.domain.rules.ParsedBankCsvCredit
import com.example.domain.rules.PartyRules
import com.example.domain.rules.PassportAggregator
import com.example.domain.rules.ReconciliationEngine
import com.example.domain.rules.ReviewConfirmRules
import com.example.domain.rules.ReviewFormValues
import com.example.domain.rules.RupeeFormatter
import com.example.domain.rules.WorkerConfirmationResolution
import com.example.reconciliation.BankCreditClassification
import com.example.reconciliation.MatchOutcome
import com.example.reconciliation.PaymentStatus
import com.example.reconciliation.ReconciliationEvidenceLabel
import com.example.reconciliation.RelationshipOutcomeStatus
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
import com.example.model.PartyContactStatus
import com.example.model.PartyRelationshipStatus
import com.example.model.PartyRole
import com.example.model.PartyType
import com.example.model.PayoutCycle
import com.example.model.Reconciliation
import com.example.model.ReconciliationDecision
import com.example.model.RelationshipStatus
import com.example.model.ReviewStatus
import com.example.model.ShareLink
import com.example.model.ShareLinkStatus
import com.example.model.WorkRelationship
import com.example.model.WorkerProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/**
 * Firestore repository managing all 10 user-isolated collections for Earnings & Work Passport.
 * Always connects to the provisioned named database via R.string.firestore_database_id.
 */
class PassportRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google before accessing Firestore.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user write." }
        }
        return uid
    }

    private fun newSafeId(prefix: String): String {
        val token = UUID.randomUUID().toString().replace("-", "").take(12)
        return "${prefix}_$token"
    }

    private fun <T> observeCollection(
        collectionName: String,
        userId: String,
        clazz: Class<T>
    ): Flow<List<T>> = db.collection(collectionName)
        .whereEqualTo("userId", userId)
        .snapshots()
        .map { snapshot ->
            snapshot.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val toLog = firestoreError ?: (error as? Exception)
            if (toLog != null && auth.currentUser?.uid == userId) {
                handleFirestoreError(toLog, OperationType.LIST, collectionName)
            }
            throw error
        }

    fun observeWorkers(userId: String): Flow<List<WorkerProfile>> =
        observeCollection(COL_WORKERS, userId, WorkerProfile::class.java)

    fun observeParties(userId: String): Flow<List<Party>> =
        observeCollection(COL_PARTIES, userId, Party::class.java)

    fun observeRelationships(userId: String): Flow<List<WorkRelationship>> =
        observeCollection(COL_RELATIONSHIPS, userId, WorkRelationship::class.java)

    fun observeIncomeRecords(userId: String): Flow<List<IncomeRecord>> =
        observeCollection(COL_INCOME_RECORDS, userId, IncomeRecord::class.java)

    fun observeDocuments(userId: String): Flow<List<IncomeDocument>> =
        observeCollection(COL_DOCUMENTS, userId, IncomeDocument::class.java)

    fun observeBankCredits(userId: String): Flow<List<BankCredit>> =
        observeCollection(COL_BANK_CREDITS, userId, BankCredit::class.java)

    fun observeReconciliations(userId: String): Flow<List<Reconciliation>> =
        observeCollection(COL_RECONCILIATIONS, userId, Reconciliation::class.java)

    fun observeConfirmationRequests(userId: String): Flow<List<ConfirmationRequest>> =
        observeCollection(COL_CONFIRMATION_REQUESTS, userId, ConfirmationRequest::class.java)

    fun observeShareLinks(userId: String): Flow<List<ShareLink>> =
        observeCollection(COL_SHARE_LINKS, userId, ShareLink::class.java)

    fun observeAuditLogs(userId: String): Flow<List<AuditLogEntry>> =
        observeCollection(COL_AUDIT_LOG, userId, AuditLogEntry::class.java)

    // --- Worker Profiles & Synthetic Demo Seeding ---

    suspend fun ensureAshaDemoProfile(userId: String, forceReset: Boolean = false): Result<String> {
        val workerId = DemoDataFactory.demoWorkerIdForUser(userId)
        val path = "$COL_WORKERS/$workerId"
        return try {
            val uid = requireUserId(userId)
            val seed = DemoDataFactory.buildAshaDemoSeed(uid)
            val existingDoc = db.collection(COL_WORKERS).document(workerId).get().await()
            val existingDupDoc = if (existingDoc.exists() && !forceReset) {
                db.collection(COL_INCOME_RECORDS).document("${workerId}_inc_rideco_nov9_dup").get().await()
            } else {
                null
            }
            val isCurrentCanonicalDemo = existingDoc.exists() &&
                existingDoc.getString("primaryWorkType") == seed.workerPrimaryWork &&
                !existingDupDoc?.getString("groupedIntoRecordId").isNullOrBlank()
            if (isCurrentCanonicalDemo && !forceReset) {
                return Result.success(workerId)
            }
            if (existingDoc.exists()) {
                deleteWorkerScopedData(uid, workerId)
            }
            val batch = db.batch()
            val now = FieldValue.serverTimestamp()

            batch.set(
                db.collection(COL_WORKERS).document(seed.workerId),
                mapOf(
                    "userId" to uid,
                    "displayName" to seed.workerDisplayName,
                    "city" to seed.workerCity,
                    "primaryWorkType" to seed.workerPrimaryWork,
                    "demoProfile" to true,
                    "createdAt" to now,
                    "updatedAt" to now
                )
            )

            fun addAllToBatch(collection: String, items: List<Map<String, Any>>, includeUpdatedAt: Boolean = true) {
                items.forEach { raw ->
                    val docId = raw["_docId"] as String
                    val payload = raw.filterKeys { it != "_docId" }.toMutableMap()
                    payload["createdAt"] = now
                    if (includeUpdatedAt) payload["updatedAt"] = now
                    batch.set(db.collection(collection).document(docId), payload)
                }
            }

            addAllToBatch(COL_PARTIES, seed.parties)
            addAllToBatch(COL_RELATIONSHIPS, seed.relationships)
            addAllToBatch(COL_DOCUMENTS, seed.documents)
            addAllToBatch(COL_INCOME_RECORDS, seed.incomeRecords)
            addAllToBatch(COL_BANK_CREDITS, seed.bankCredits)
            addAllToBatch(COL_RECONCILIATIONS, seed.reconciliations)
            addAllToBatch(COL_CONFIRMATION_REQUESTS, seed.confirmationRequests)
            addAllToBatch(COL_SHARE_LINKS, seed.shareLinks)
            addAllToBatch(COL_AUDIT_LOG, seed.auditLogs, includeUpdatedAt = false)

            batch.commit().await()
            Result.success(seed.workerId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun deleteWorkerScopedData(userId: String, workerId: String) {
        val childCollections = listOf(
            COL_PARTIES,
            COL_RELATIONSHIPS,
            COL_DOCUMENTS,
            COL_INCOME_RECORDS,
            COL_BANK_CREDITS,
            COL_RECONCILIATIONS,
            COL_CONFIRMATION_REQUESTS,
            COL_SHARE_LINKS,
            COL_AUDIT_LOG
        )
        val batch = db.batch()
        for (col in childCollections) {
            val snap = db.collection(col)
                .whereEqualTo("userId", userId)
                .whereEqualTo("workerId", workerId)
                .get()
                .await()
            snap.documents.forEach { batch.delete(it.reference) }
        }
        batch.delete(db.collection(COL_WORKERS).document(workerId))
        batch.commit().await()
    }

    suspend fun createCustomWorker(
        userId: String,
        displayName: String,
        city: String,
        primaryWorkType: String
    ): Result<String> {
        val workerId = newSafeId("worker")
        val path = "$COL_WORKERS/$workerId"
        return try {
            val uid = requireUserId(userId)
            val cleanName = displayName.trim().take(80)
            require(cleanName.isNotBlank()) { "Please enter a worker name." }

            val batch = db.batch()
            val now = FieldValue.serverTimestamp()
            batch.set(
                db.collection(COL_WORKERS).document(workerId),
                mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "city" to city.trim().take(60),
                    "primaryWorkType" to primaryWorkType.trim().take(80),
                    "demoProfile" to false,
                    "createdAt" to now,
                    "updatedAt" to now
                )
            )
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "WORKER_PROFILE_CREATED",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_WORKERS,
                    "targetId" to workerId,
                    "summary" to "Created worker profile for $cleanName.",
                    "createdAt" to now
                )
            )
            batch.commit().await()
            Result.success(workerId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- Parties & Relationships ---

    suspend fun addPartyAndRelationship(
        userId: String,
        workerId: String,
        name: String,
        partyType: PartyType,
        roles: List<PartyRole>,
        phone: String = "",
        email: String = "",
        note: String = "",
        registrationNumber: String = "",
        contactHint: String = "",
        roleTitle: String = "",
        payoutCycle: PayoutCycle = PayoutCycle.WEEKLY,
        startedOn: String = "2026-11-01"
    ): Result<String> {
        val partyId = newSafeId("party")
        val relId = newSafeId("rel")
        val path = "$COL_PARTIES/$partyId"
        return try {
            val uid = requireUserId(userId)
            val cleanName = name.trim().take(100)
            require(cleanName.isNotBlank()) { "Please enter a display name for this work source, client, or payer." }

            val cleanRoles = PartyRules.defaultRolesIfEmpty(roles)
            val roleCodes = cleanRoles.map { it.code }
            val cleanPhone = phone.trim().take(30)
            val cleanEmail = email.trim().take(100)
            val cleanNote = note.trim().take(300)
            val cleanReg = PartyRules.sanitizeRegistrationNumber(partyType, registrationNumber)
            val resolvedHint = contactHint.trim().ifBlank {
                listOf(cleanPhone, cleanEmail).filter { it.isNotBlank() }.joinToString(" · ")
            }.take(100)
            val cleanRoleTitle = roleTitle.trim().ifBlank {
                cleanRoles.joinToString(" & ") { it.shortLabel }
            }.take(80)

            val batch = db.batch()
            val now = FieldValue.serverTimestamp()

            batch.set(
                db.collection(COL_PARTIES).document(partyId),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "name" to cleanName,
                    "partyType" to partyType.code,
                    "contactHint" to resolvedHint,
                    "roles" to roleCodes,
                    "phone" to cleanPhone,
                    "email" to cleanEmail,
                    "note" to cleanNote,
                    "registrationNumber" to cleanReg,
                    "partyContactStatus" to PartyContactStatus.ADDED_BY_WORKER.code,
                    "createdAt" to now,
                    "updatedAt" to now
                )
            )
            batch.set(
                db.collection(COL_RELATIONSHIPS).document(relId),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "partyId" to partyId,
                    "roleTitle" to cleanRoleTitle,
                    "payoutCycle" to payoutCycle.code,
                    "status" to RelationshipStatus.ACTIVE.code,
                    "relationshipStatus" to PartyRelationshipStatus.WORKER_DECLARED.code,
                    "roles" to roleCodes,
                    "startedOn" to startedOn.trim().ifBlank { "2026-11-01" }.take(30),
                    "createdAt" to now,
                    "updatedAt" to now
                )
            )
            val roleSummary = cleanRoles.joinToString(" & ") { it.shortLabel }
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "PARTY_ADDED",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_PARTIES,
                    "targetId" to partyId,
                    "summary" to "Added party '$cleanName' (${partyType.label}; $roleSummary) as added_by_worker / worker_declared.".take(300),
                    "createdAt" to now
                )
            )
            batch.commit().await()
            Result.success(partyId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- Self-Declared Income Record & Bank Credit ---

    suspend fun addSelfDeclaredIncome(
        userId: String,
        workerId: String,
        partyId: String,
        partyName: String,
        amountRupees: Long,
        earnedDate: String,
        periodLabel: String,
        referenceCode: String,
        notes: String
    ): Result<String> {
        val recordId = newSafeId("inc")
        val path = "$COL_INCOME_RECORDS/$recordId"
        return try {
            val uid = requireUserId(userId)
            val cleanParty = partyName.trim().take(100)
            require(cleanParty.isNotBlank()) { "Please enter who paid you." }
            require(amountRupees in 1L..100_000_000L) { "Please enter a valid rupee amount." }

            val status = EvidenceClassifier.forSelfDeclaredEntry()
            val batch = db.batch()
            val now = FieldValue.serverTimestamp()

            batch.set(
                db.collection(COL_INCOME_RECORDS).document(recordId),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "partyId" to partyId.trim().take(128),
                    "partyName" to cleanParty,
                    "documentId" to "",
                    "amountRupees" to amountRupees,
                    "earnedDate" to earnedDate.trim().ifBlank { "2026-11-15" }.take(30),
                    "periodLabel" to periodLabel.trim().take(80),
                    "referenceCode" to referenceCode.trim().take(80),
                    "evidenceStatus" to status.code,
                    "workerConfirmed" to true,
                    "notes" to notes.trim().take(500),
                    "createdAt" to now,
                    "updatedAt" to now
                )
            )
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "SELF_DECLARED_ADDED",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_INCOME_RECORDS,
                    "targetId" to recordId,
                    "summary" to "Added ${RupeeFormatter.format(amountRupees)} from $cleanParty as Self-declared.",
                    "createdAt" to now
                )
            )
            batch.commit().await()
            runReconciliationAndPersist(uid, workerId)
            Result.success(recordId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addBankCredit(
        userId: String,
        workerId: String,
        bankLabel: String,
        creditDate: String,
        amountRupees: Long,
        narration: String,
        utrReference: String
    ): Result<String> {
        val creditId = newSafeId("bc")
        val path = "$COL_BANK_CREDITS/$creditId"
        return try {
            val uid = requireUserId(userId)
            val cleanBank = bankLabel.trim().ifBlank { "Bank Account (Demo)" }.take(80)
            require(amountRupees in 1L..100_000_000L) { "Please enter a valid bank credit amount." }

            val batch = db.batch()
            val now = FieldValue.serverTimestamp()

            batch.set(
                db.collection(COL_BANK_CREDITS).document(creditId),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "bankLabel" to cleanBank,
                    "creditDate" to creditDate.trim().ifBlank { "2026-11-16" }.take(30),
                    "amountRupees" to amountRupees,
                    "narration" to narration.trim().take(200),
                    "utrReference" to utrReference.trim().take(80),
                    "matchedIncomeRecordId" to "",
                    "createdAt" to now,
                    "updatedAt" to now
                )
            )
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "BANK_CREDIT_ADDED",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_BANK_CREDITS,
                    "targetId" to creditId,
                    "summary" to "Added bank credit ${RupeeFormatter.format(amountRupees)} ($cleanBank) for rule matching.",
                    "createdAt" to now
                )
            )
            batch.commit().await()
            runReconciliationAndPersist(uid, workerId)
            Result.success(creditId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- File Hash Duplicate Detection & Bank CSV Import (Code-Only, No AI) ---

    suspend fun hasDocumentWithFileHash(
        userId: String,
        workerId: String,
        fileHash: String
    ): Result<Boolean> {
        val cleanHash = fileHash.trim()
        if (cleanHash.isEmpty()) return Result.success(false)
        return try {
            val uid = requireUserId(userId)
            val snap = db.collection(COL_DOCUMENTS)
                .whereEqualTo("userId", uid)
                .get()
                .await()
            val exists = snap.documents.any { doc ->
                doc.getString("workerId") == workerId &&
                    doc.getString("fileHash").orEmpty().equals(cleanHash, ignoreCase = true)
            }
            Result.success(exists)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, COL_DOCUMENTS)
            Result.failure(e)
        }
    }

    suspend fun importBankCreditsFromCsv(
        userId: String,
        workerId: String,
        fileName: String,
        fileHash: String,
        bankLabel: String,
        credits: List<ParsedBankCsvCredit>
    ): Result<Int> {
        val docId = newSafeId("doc_csv")
        val path = "$COL_DOCUMENTS/$docId"
        return try {
            val uid = requireUserId(userId)
            require(credits.isNotEmpty()) { "No valid bank credit rows found in CSV." }

            val cleanBank = bankLabel.trim().ifBlank { "Bank Statement CSV" }.take(80)
            val cleanTitle = fileName.trim().ifBlank { "Bank Statement CSV" }.take(120)
            val totalRupees = credits.sumOf { it.amountRupees }.coerceIn(0L, 100_000_000L)
            val latestDate = credits.maxOfOrNull { it.date } ?: "2026-11-15"
            val now = FieldValue.serverTimestamp()

            // Chunk into Firestore batches (<= 400 writes per batch) so large statements never exceed 500-write limit
            val firstChunkSize = minOf(credits.size, 390)
            val firstChunk = credits.subList(0, firstChunkSize)
            val remaining = if (credits.size > firstChunkSize) credits.subList(firstChunkSize, credits.size) else emptyList()

            val primaryBatch = db.batch()
            primaryBatch.set(
                db.collection(COL_DOCUMENTS).document(docId),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "partyId" to "",
                    "title" to cleanTitle,
                    "documentType" to DocumentType.BANK_STATEMENT_CSV.code,
                    "sourceKind" to DocumentSourceKind.CSV_IMPORT.code,
                    "reviewStatus" to ReviewStatus.CONFIRMED.code,
                    "fileHash" to fileHash.trim().take(128),
                    "extractedPartyName" to cleanBank,
                    "extractedAmountRupees" to totalRupees,
                    "extractedDate" to latestDate.take(30),
                    "extractedReference" to "${credits.size} credit rows".take(80),
                    "extractionNotes" to "Parsed ${credits.size} bank credits from CSV in code (no AI).".take(500),
                    "createdAt" to now,
                    "updatedAt" to now
                )
            )

            firstChunk.forEach { row ->
                val creditId = newSafeId("bc")
                primaryBatch.set(
                    db.collection(COL_BANK_CREDITS).document(creditId),
                    mapOf(
                        "userId" to uid,
                        "workerId" to workerId,
                        "bankLabel" to cleanBank,
                        "creditDate" to row.date.take(30),
                        "amountRupees" to row.amountRupees.coerceIn(1L, 100_000_000L),
                        "narration" to row.descriptor.take(200),
                        "utrReference" to row.utrReference.take(80),
                        "matchedIncomeRecordId" to "",
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                )
            }

            primaryBatch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "CSV_BANK_CREDITS_IMPORTED",
                    "actorRole" to ActorRole.RULES.code,
                    "targetCollection" to COL_BANK_CREDITS,
                    "targetId" to docId,
                    "summary" to "Parsed ${credits.size} bank credits (${RupeeFormatter.format(totalRupees)}) from CSV '$cleanTitle' in code (no AI).".take(300),
                    "createdAt" to now
                )
            )
            primaryBatch.commit().await()

            // Commit any additional chunks if the CSV had > 390 credit rows
            remaining.chunked(400).forEach { chunk ->
                val extraBatch = db.batch()
                chunk.forEach { row ->
                    val creditId = newSafeId("bc")
                    extraBatch.set(
                        db.collection(COL_BANK_CREDITS).document(creditId),
                        mapOf(
                            "userId" to uid,
                            "workerId" to workerId,
                            "bankLabel" to cleanBank,
                            "creditDate" to row.date.take(30),
                            "amountRupees" to row.amountRupees.coerceIn(1L, 100_000_000L),
                            "narration" to row.descriptor.take(200),
                            "utrReference" to row.utrReference.take(80),
                            "matchedIncomeRecordId" to "",
                            "createdAt" to now,
                            "updatedAt" to now
                        )
                    )
                }
                extraBatch.commit().await()
            }

            runReconciliationAndPersist(uid, workerId)
            Result.success(credits.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- AI Document Reading -> Mandatory Worker Confirmation ---

    suspend fun savePendingAiDocument(
        userId: String,
        workerId: String,
        partyId: String,
        draft: ExtractedDocumentDraft
    ): Result<String> {
        val docId = newSafeId("doc")
        val path = "$COL_DOCUMENTS/$docId"
        return try {
            val uid = requireUserId(userId)
            val batch = db.batch()
            val now = FieldValue.serverTimestamp()

            batch.set(
                db.collection(COL_DOCUMENTS).document(docId),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "partyId" to partyId.trim().take(128),
                    "title" to draft.title.ifBlank { "Income Document" }.take(120),
                    "documentType" to draft.documentType.code,
                    "sourceKind" to draft.sourceKind.code,
                    "reviewStatus" to ReviewStatus.PENDING_REVIEW.code,
                    "fileHash" to draft.fileHash.trim().take(128),
                    "extractedPartyName" to draft.partyName.take(100),
                    "extractedAmountRupees" to draft.amountRupees.coerceIn(0L, 100_000_000L),
                    "extractedDate" to draft.earnedDate.take(30),
                    "extractedReference" to draft.referenceCode.take(80),
                    "extractionNotes" to draft.extractionNotes.take(500),
                    "createdAt" to now,
                    "updatedAt" to now
                )
            )
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "AI_DOCUMENT_READ",
                    "actorRole" to ActorRole.AI.code,
                    "targetCollection" to COL_DOCUMENTS,
                    "targetId" to docId,
                    "summary" to "AI read ${RupeeFormatter.format(draft.amountRupees)} from '${draft.title.take(40)}' (awaiting worker confirmation).",
                    "createdAt" to now
                )
            )
            batch.commit().await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun confirmDocumentAndCreateIncome(
        userId: String,
        workerId: String,
        existingDocumentId: String?,
        draft: ExtractedDocumentDraft,
        form: ReviewFormValues,
        workGiverPartyId: String,
        workGiverPartyName: String,
        payerPartyId: String,
        payerPartyName: String,
        workerConfirmed: Boolean
    ): Result<String> {
        val docId = existingDocumentId?.takeIf { it.isNotBlank() } ?: newSafeId("doc")
        val recordId = newSafeId("inc")
        val path = "$COL_INCOME_RECORDS/$recordId"
        return try {
            val uid = requireUserId(userId)
            val evidenceStatus = EvidenceClassifier.forWorkerConfirmedDocument(workerConfirmed)
            val cleanWorkGiverName = workGiverPartyName.trim().ifBlank { form.resolvedPartyName() }.take(100)
            val cleanPayerName = payerPartyName.trim().ifBlank { cleanWorkGiverName }.take(100)
            val cleanParty = cleanWorkGiverName.ifBlank { cleanPayerName }.take(100)
            val primaryPartyId = workGiverPartyId.trim().ifBlank { payerPartyId.trim() }.take(128)
            val confirmedAmount = form.resolvedConfirmedAmountRupees()
            require(cleanParty.isNotBlank()) { "Please confirm who gave you this work or who paid you." }
            require(confirmedAmount in 1L..100_000_000L) { "Please confirm a valid amount in Rupees." }

            val cleanTitle = form.title.trim().ifBlank { draft.title.ifBlank { "$cleanParty Document" } }.take(120)
            val cleanDate = form.recordDate.trim().ifBlank { "2026-11-15" }.take(30)
            val cleanPeriodLabel = form.resolvedPeriodLabel().take(80)
            val cleanRef = form.resolvedReference().take(80)
            val cleanNotes = form.notes.trim().ifBlank { "Confirmed by worker after AI extraction." }.take(500)
            val cleanHash = draft.fileHash.trim().take(128)
            val aiOriginalMap = ReviewConfirmRules.buildAiOriginalMap(draft.aiOriginalRaw)
            val edits = ReviewConfirmRules.computeWorkerEdits(draft.aiOriginalRaw, form)

            val batch = db.batch()
            val now = FieldValue.serverTimestamp()

            if (existingDocumentId.isNullOrBlank()) {
                batch.set(
                    db.collection(COL_DOCUMENTS).document(docId),
                    mapOf(
                        "userId" to uid,
                        "workerId" to workerId,
                        "partyId" to primaryPartyId,
                        "title" to cleanTitle,
                        "documentType" to draft.documentType.code,
                        "sourceKind" to draft.sourceKind.code,
                        "reviewStatus" to ReviewStatus.CONFIRMED.code,
                        "fileHash" to cleanHash,
                        "extractedPartyName" to cleanParty,
                        "extractedAmountRupees" to confirmedAmount,
                        "extractedDate" to cleanDate,
                        "extractedReference" to cleanRef,
                        "extractionNotes" to cleanNotes,
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                )
            } else {
                batch.update(
                    db.collection(COL_DOCUMENTS).document(docId),
                    mapOf(
                        "partyId" to primaryPartyId,
                        "reviewStatus" to ReviewStatus.CONFIRMED.code,
                        "extractedPartyName" to cleanParty,
                        "extractedAmountRupees" to confirmedAmount,
                        "extractedDate" to cleanDate,
                        "extractedReference" to cleanRef,
                        "updatedAt" to now
                    )
                )
            }

            val incomePayload = linkedMapOf<String, Any>(
                "userId" to uid,
                "workerId" to workerId,
                "partyId" to primaryPartyId,
                "partyName" to cleanParty,
                "workGiverPartyId" to workGiverPartyId.trim().take(128),
                "workGiverPartyName" to cleanWorkGiverName,
                "payerPartyId" to payerPartyId.trim().take(128),
                "payerPartyName" to cleanPayerName,
                "documentId" to docId,
                "amountRupees" to confirmedAmount,
                "earnedDate" to cleanDate,
                "periodLabel" to cleanPeriodLabel,
                "referenceCode" to cleanRef,
                "invoiceNumber" to form.invoiceNumber.trim().take(80),
                "periodStart" to form.periodStart.trim().take(30),
                "periodEnd" to form.periodEnd.trim().take(30),
                "grossAmountRupees" to ReviewFormValues.parseRupeesOrZero(form.grossAmountText),
                "deductionsRupees" to ReviewFormValues.parseRupeesOrZero(form.deductionsText),
                "netAmountRupees" to ReviewFormValues.parseRupeesOrZero(form.netAmountText),
                "invoiceAmountRupees" to ReviewFormValues.parseRupeesOrZero(form.invoiceAmountText),
                "evidenceStatus" to evidenceStatus.code,
                "workerConfirmed" to true,
                "aiOriginal" to aiOriginalMap,
                "notes" to cleanNotes,
                "createdAt" to now,
                "updatedAt" to now
            )

            batch.set(
                db.collection(COL_INCOME_RECORDS).document(recordId),
                incomePayload
            )

            // 1) Audit log entry for upload
            val hashHint = if (cleanHash.isNotBlank()) " · sha256:${cleanHash.take(10)}" else ""
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "DOCUMENT_UPLOADED",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_DOCUMENTS,
                    "targetId" to docId,
                    "summary" to "Uploaded '$cleanTitle' (${draft.sourceKind.code}$hashHint) and read candidate values with AI.".take(300),
                    "createdAt" to now
                )
            )

            // 2) Audit log entry for edit
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "DOCUMENT_VALUES_EDITED",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_INCOME_RECORDS,
                    "targetId" to recordId,
                    "summary" to ReviewConfirmRules.formatEditAuditSummary(edits).take(300),
                    "createdAt" to now
                )
            )

            // 3) Audit log entry for confirm
            val partyLinkDetail = if (cleanPayerName.isNotBlank() && !cleanPayerName.equals(cleanWorkGiverName, ignoreCase = true)) {
                "work by $cleanWorkGiverName, paid by $cleanPayerName"
            } else {
                "from $cleanParty"
            }
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "WORKER_CONFIRMED_DOCUMENT",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_INCOME_RECORDS,
                    "targetId" to recordId,
                    "summary" to "Worker confirmed ${RupeeFormatter.format(confirmedAmount)} ($partyLinkDetail) -> Document-extracted.".take(300),
                    "createdAt" to now
                )
            )

            batch.commit().await()
            runReconciliationAndPersist(uid, workerId)
            Result.success(recordId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun discardPendingDocument(
        userId: String,
        workerId: String,
        documentId: String
    ): Result<Unit> {
        val path = "$COL_DOCUMENTS/$documentId"
        return try {
            val uid = requireUserId(userId)
            val batch = db.batch()
            val now = FieldValue.serverTimestamp()
            batch.update(
                db.collection(COL_DOCUMENTS).document(documentId),
                mapOf("reviewStatus" to ReviewStatus.DISCARDED.code, "updatedAt" to now)
            )
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "DOCUMENT_DISCARDED",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_DOCUMENTS,
                    "targetId" to documentId,
                    "summary" to "Worker discarded pending AI document draft.",
                    "createdAt" to now
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Deterministic Reconciliation Confirmation & Disputes ---

    suspend fun confirmReconciliationMatch(
        userId: String,
        workerId: String,
        candidate: MatchCandidate
    ): Result<Unit> {
        val recId = newSafeId("rec")
        val path = "$COL_RECONCILIATIONS/$recId"
        return try {
            val uid = requireUserId(userId)
            val nextStatus = EvidenceClassifier.onBankMatchConfirmed(candidate.incomeRecord.resolvedEvidence())
            val batch = db.batch()
            val now = FieldValue.serverTimestamp()

            batch.set(
                db.collection(COL_RECONCILIATIONS).document(recId),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "incomeRecordId" to candidate.incomeRecord.id,
                    "bankCreditId" to candidate.bankCredit.id,
                    "matchRule" to candidate.matchRule.take(120),
                    "amountDeltaRupees" to candidate.amountDeltaRupees,
                    "dateGapDays" to candidate.dateGapDays.coerceIn(0L, 365L),
                    "decisionStatus" to ReconciliationDecision.CONFIRMED.code,
                    "matchOutcome" to MatchOutcome.STRONG.code,
                    "createdAt" to now,
                    "updatedAt" to now
                )
            )
            batch.update(
                db.collection(COL_INCOME_RECORDS).document(candidate.incomeRecord.id),
                mapOf(
                    "evidenceStatus" to nextStatus.code,
                    "matchOutcome" to MatchOutcome.STRONG.code,
                    "notes" to "Matched by rule: ${candidate.matchRule}".take(500),
                    "updatedAt" to now
                )
            )
            batch.update(
                db.collection(COL_BANK_CREDITS).document(candidate.bankCredit.id),
                mapOf(
                    "matchedIncomeRecordId" to candidate.incomeRecord.id,
                    "classificationStatus" to BankCreditClassification.MATCHED.code,
                    "evidenceStatus" to EvidenceStatus.SOURCE_MATCHED.code,
                    "updatedAt" to now
                )
            )
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "RECONCILIATION_CONFIRMED",
                    "actorRole" to ActorRole.RULES.code,
                    "targetCollection" to COL_INCOME_RECORDS,
                    "targetId" to candidate.incomeRecord.id,
                    "summary" to "Matched ${candidate.incomeRecord.partyName} (${RupeeFormatter.format(candidate.incomeRecord.amountRupees)}) to ${candidate.bankCredit.bankLabel} -> both labeled ${nextStatus.label}.".take(300),
                    "createdAt" to now
                )
            )
            batch.commit().await()
            runReconciliationAndPersist(uid, workerId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun rejectReconciliationMatch(
        userId: String,
        workerId: String,
        candidate: MatchCandidate
    ): Result<Unit> {
        val recId = newSafeId("rec")
        val path = "$COL_RECONCILIATIONS/$recId"
        return try {
            val uid = requireUserId(userId)
            val batch = db.batch()
            val now = FieldValue.serverTimestamp()
            batch.set(
                db.collection(COL_RECONCILIATIONS).document(recId),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "incomeRecordId" to candidate.incomeRecord.id,
                    "bankCreditId" to candidate.bankCredit.id,
                    "matchRule" to candidate.matchRule.take(120),
                    "amountDeltaRupees" to candidate.amountDeltaRupees,
                    "dateGapDays" to candidate.dateGapDays.coerceIn(0L, 365L),
                    "decisionStatus" to ReconciliationDecision.REJECTED.code,
                    "matchOutcome" to candidate.outcome.code,
                    "createdAt" to now,
                    "updatedAt" to now
                )
            )
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "RECONCILIATION_REJECTED",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_RECONCILIATIONS,
                    "targetId" to recId,
                    "summary" to "Worker rejected suggested bank match for ${candidate.incomeRecord.partyName}.",
                    "createdAt" to now
                )
            )
            batch.commit().await()
            runReconciliationAndPersist(uid, workerId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun classifyBankCredit(
        userId: String,
        workerId: String,
        credit: BankCredit,
        classification: BankCreditClassification
    ): Result<Unit> {
        val path = "$COL_BANK_CREDITS/${credit.id}"
        return try {
            val uid = requireUserId(userId)
            val batch = db.batch()
            val now = FieldValue.serverTimestamp()

            batch.update(
                db.collection(COL_BANK_CREDITS).document(credit.id),
                mapOf(
                    "classificationStatus" to classification.code,
                    "updatedAt" to now
                )
            )

            val descriptor = credit.narration.ifBlank { credit.bankLabel }.take(100)
            val actionNote = when (classification) {
                BankCreditClassification.PERSONAL_TRANSFER ->
                    "Worker classified bank credit '$descriptor' (${RupeeFormatter.format(credit.amountRupees)}) as Not income (family / personal transfer)."
                BankCreditClassification.NON_WORK_IGNORED ->
                    "Worker classified bank credit '$descriptor' (${RupeeFormatter.format(credit.amountRupees)}) as Not income (ignored)."
                else ->
                    "Worker classified bank credit '$descriptor' (${RupeeFormatter.format(credit.amountRupees)}) as ${classification.label}."
            }
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "CREDIT_CLASSIFIED_BY_WORKER",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_BANK_CREDITS,
                    "targetId" to credit.id,
                    "summary" to actionNote.take(300),
                    "createdAt" to now
                )
            )

            // If worker classifies an unmatched bank credit as work income directly without selecting an existing income
            if (classification == BankCreditClassification.WORK_INCOME) {
                val recordId = newSafeId("inc")
                batch.set(
                    db.collection(COL_INCOME_RECORDS).document(recordId),
                    mapOf(
                        "userId" to uid,
                        "workerId" to workerId,
                        "partyId" to "",
                        "partyName" to descriptor,
                        "workGiverPartyId" to "",
                        "workGiverPartyName" to descriptor,
                        "payerPartyId" to "",
                        "payerPartyName" to descriptor,
                        "documentId" to "",
                        "amountRupees" to credit.amountRupees.coerceIn(1L, 100_000_000L),
                        "earnedDate" to credit.creditDate.ifBlank { "2026-11-15" }.take(30),
                        "periodLabel" to "Classified from bank credit",
                        "referenceCode" to credit.utrReference.take(80),
                        "evidenceStatus" to EvidenceStatus.SELF_DECLARED.code,
                        "workerConfirmed" to true,
                        "simulatedApi" to false,
                        "notes" to "Created when worker classified bank credit '$descriptor' as work income.".take(500),
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                )
                batch.set(
                    db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                    mapOf(
                        "userId" to uid,
                        "workerId" to workerId,
                        "actionType" to "WORK_INCOME_FROM_CREDIT",
                        "actorRole" to ActorRole.WORKER.code,
                        "targetCollection" to COL_INCOME_RECORDS,
                        "targetId" to recordId,
                        "summary" to "Created work income (${RupeeFormatter.format(credit.amountRupees)}) from classified bank credit '$descriptor'.".take(300),
                        "createdAt" to now
                    )
                )
            }

            batch.commit().await()
            runReconciliationAndPersist(uid, workerId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Links an unclassified bank credit to an existing unmatched income record chosen by the worker.
     * Creates a CONFIRMED reconciliation, updates both records, writes audit_log entries, and re-runs reconciliation.
     */
    suspend fun linkBankCreditToExistingIncome(
        userId: String,
        workerId: String,
        credit: BankCredit,
        incomeRecord: IncomeRecord
    ): Result<Unit> {
        val recId = newSafeId("rec")
        val path = "$COL_BANK_CREDITS/${credit.id}"
        return try {
            val uid = requireUserId(userId)
            val amountDelta = credit.amountRupees - incomeRecord.amountRupees
            val hasAmountConflict = kotlin.math.abs(amountDelta) > 1L
            val dateGap = (RupeeFormatter.daysBetween(incomeRecord.earnedDate, credit.creditDate) ?: 0L)
                .coerceIn(0L, 365L)

            val batch = db.batch()
            val now = FieldValue.serverTimestamp()

            batch.set(
                db.collection(COL_RECONCILIATIONS).document(recId),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "incomeRecordId" to incomeRecord.id,
                    "bankCreditId" to credit.id,
                    "matchRule" to "Worker linked bank credit to ${incomeRecord.partyName}".take(120),
                    "amountDeltaRupees" to amountDelta,
                    "dateGapDays" to dateGap,
                    "decisionStatus" to ReconciliationDecision.CONFIRMED.code,
                    "matchOutcome" to MatchOutcome.STRONG.code,
                    "createdAt" to now,
                    "updatedAt" to now
                )
            )

            batch.update(
                db.collection(COL_BANK_CREDITS).document(credit.id),
                mapOf(
                    "matchedIncomeRecordId" to incomeRecord.id,
                    "classificationStatus" to BankCreditClassification.MATCHED.code,
                    "evidenceStatus" to EvidenceStatus.SOURCE_MATCHED.code,
                    "updatedAt" to now
                )
            )

            val nextIncomeStatus = when {
                incomeRecord.resolvedEvidence() == EvidenceStatus.DISPUTED -> EvidenceStatus.DISPUTED
                hasAmountConflict -> EvidenceStatus.DISPUTED
                else -> EvidenceStatus.SOURCE_MATCHED
            }
            val incomeUpdates = mutableMapOf<String, Any>(
                "evidenceStatus" to nextIncomeStatus.code,
                "matchOutcome" to MatchOutcome.STRONG.code,
                "updatedAt" to now
            )
            if (hasAmountConflict) {
                incomeUpdates["manuallyDisputed"] = true
                incomeUpdates["conflictReason"] =
                    "Linked bank credit is ${RupeeFormatter.format(credit.amountRupees)} vs recorded ${RupeeFormatter.format(incomeRecord.amountRupees)}."
                        .take(300)
            }
            batch.update(
                db.collection(COL_INCOME_RECORDS).document(incomeRecord.id),
                incomeUpdates
            )

            val descriptor = credit.narration.ifBlank { credit.bankLabel }.take(80)
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "BANK_CREDIT_LINKED_TO_INCOME",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_BANK_CREDITS,
                    "targetId" to credit.id,
                    "summary" to "Worker classified '$descriptor' (${RupeeFormatter.format(credit.amountRupees)}) as work payment for ${incomeRecord.partyName} -> ${nextIncomeStatus.label}.".take(300),
                    "createdAt" to now
                )
            )

            batch.commit().await()
            runReconciliationAndPersist(uid, workerId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Creates a new worker-confirmed income record from an unclassified bank credit and links them
     * immediately in one atomic batch, writing audit_log entries and running reconciliation.
     */
    suspend fun createIncomeAndLinkBankCredit(
        userId: String,
        workerId: String,
        credit: BankCredit,
        partyName: String,
        earnedDate: String,
        periodLabel: String,
        notes: String
    ): Result<String> {
        val recordId = newSafeId("inc")
        val recId = newSafeId("rec")
        val path = "$COL_INCOME_RECORDS/$recordId"
        return try {
            val uid = requireUserId(userId)
            val cleanParty = partyName.trim().ifBlank { credit.narration.ifBlank { credit.bankLabel } }.take(100)
            val cleanDate = earnedDate.trim().ifBlank { credit.creditDate.ifBlank { "2026-11-15" } }.take(30)
            val cleanPeriod = periodLabel.trim().ifBlank { "Work payment from bank credit" }.take(80)
            val cleanNotes = notes.trim().ifBlank {
                "Created from bank credit '${credit.narration.ifBlank { credit.bankLabel }}' and linked by worker."
            }.take(500)
            val dateGap = (RupeeFormatter.daysBetween(cleanDate, credit.creditDate) ?: 0L).coerceIn(0L, 365L)

            val batch = db.batch()
            val now = FieldValue.serverTimestamp()

            batch.set(
                db.collection(COL_INCOME_RECORDS).document(recordId),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "partyId" to "",
                    "partyName" to cleanParty,
                    "workGiverPartyId" to "",
                    "workGiverPartyName" to cleanParty,
                    "payerPartyId" to "",
                    "payerPartyName" to cleanParty,
                    "documentId" to "",
                    "amountRupees" to credit.amountRupees.coerceIn(1L, 100_000_000L),
                    "earnedDate" to cleanDate,
                    "periodLabel" to cleanPeriod,
                    "referenceCode" to credit.utrReference.take(80),
                    "evidenceStatus" to EvidenceStatus.SOURCE_MATCHED.code,
                    "workerConfirmed" to true,
                    "simulatedApi" to false,
                    "matchOutcome" to MatchOutcome.STRONG.code,
                    "notes" to cleanNotes,
                    "createdAt" to now,
                    "updatedAt" to now
                )
            )

            batch.set(
                db.collection(COL_RECONCILIATIONS).document(recId),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "incomeRecordId" to recordId,
                    "bankCreditId" to credit.id,
                    "matchRule" to "Worker created work income & linked bank credit".take(120),
                    "amountDeltaRupees" to 0L,
                    "dateGapDays" to dateGap,
                    "decisionStatus" to ReconciliationDecision.CONFIRMED.code,
                    "matchOutcome" to MatchOutcome.STRONG.code,
                    "createdAt" to now,
                    "updatedAt" to now
                )
            )

            batch.update(
                db.collection(COL_BANK_CREDITS).document(credit.id),
                mapOf(
                    "matchedIncomeRecordId" to recordId,
                    "classificationStatus" to BankCreditClassification.MATCHED.code,
                    "evidenceStatus" to EvidenceStatus.SOURCE_MATCHED.code,
                    "updatedAt" to now
                )
            )

            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "WORK_INCOME_FROM_CREDIT",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_INCOME_RECORDS,
                    "targetId" to recordId,
                    "summary" to "Worker created '$cleanParty' (${RupeeFormatter.format(credit.amountRupees)}) and linked bank credit -> Source-matched.".take(300),
                    "createdAt" to now
                )
            )

            batch.commit().await()
            runReconciliationAndPersist(uid, workerId)
            Result.success(recordId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Non-destructively archives a grouped duplicate copy ("Ignore duplicate"):
     * keeps the copy grouped and counted once without deleting it, dismisses the open alert,
     * and writes an audit_log entry.
     */
    suspend fun ignoreDuplicateRecord(
        userId: String,
        workerId: String,
        record: IncomeRecord
    ): Result<Unit> {
        val path = "$COL_INCOME_RECORDS/${record.id}"
        return try {
            val uid = requireUserId(userId)
            val batch = db.batch()
            val now = FieldValue.serverTimestamp()
            batch.update(
                db.collection(COL_INCOME_RECORDS).document(record.id),
                mapOf(
                    "duplicateIgnored" to true,
                    "updatedAt" to now
                )
            )
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "WORKER_IGNORED_DUPLICATE",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_INCOME_RECORDS,
                    "targetId" to record.id,
                    "summary" to "Worker ignored duplicate alert for ${record.partyName} (${RupeeFormatter.format(record.amountRupees)}); copy remains archived and counted once.".take(300),
                    "createdAt" to now
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateIncomeEvidenceStatus(
        userId: String,
        workerId: String,
        incomeRecordId: String,
        newStatus: EvidenceStatus,
        note: String,
        actorRole: ActorRole
    ): Result<Unit> {
        val path = "$COL_INCOME_RECORDS/$incomeRecordId"
        return try {
            val uid = requireUserId(userId)
            val isDispute = newStatus == EvidenceStatus.DISPUTED
            val cleanNote = note.trim()
            val batch = db.batch()
            val now = FieldValue.serverTimestamp()

            // Preserve original record `notes` when marking Disputed so both versions stay visible side by side
            val updates = mutableMapOf<String, Any>(
                "evidenceStatus" to newStatus.code,
                "manuallyDisputed" to isDispute,
                "conflictReason" to if (isDispute) cleanNote.take(300) else "",
                "updatedAt" to now
            )
            batch.update(
                db.collection(COL_INCOME_RECORDS).document(incomeRecordId),
                updates
            )
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to if (isDispute) "WORKER_MARKED_DISPUTED" else "DISPUTE_CLEARED",
                    "actorRole" to actorRole.code,
                    "targetCollection" to COL_INCOME_RECORDS,
                    "targetId" to incomeRecordId,
                    "summary" to if (isDispute) {
                        "Worker marked 'This is wrong' -> Disputed. Note: ${cleanNote.take(180)}".take(300)
                    } else {
                        "Dispute cleared -> ${newStatus.label}. ${cleanNote.take(150)}".take(300)
                    },
                    "createdAt" to now
                )
            )
            batch.commit().await()
            runReconciliationAndPersist(uid, workerId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteIncomeRecord(
        userId: String,
        workerId: String,
        incomeRecordId: String,
        summaryLabel: String
    ): Result<Unit> {
        val path = "$COL_INCOME_RECORDS/$incomeRecordId"
        return try {
            val uid = requireUserId(userId)
            // Safeguard: disputed records are NEVER deleted
            val existingSnap = db.collection(COL_INCOME_RECORDS).document(incomeRecordId).get().await()
            val existingStatus = existingSnap.getString("evidenceStatus").orEmpty()
            val manuallyDisputed = existingSnap.getBoolean("manuallyDisputed") == true
            if (existingStatus == EvidenceStatus.DISPUTED.code || manuallyDisputed) {
                return Result.failure(
                    IllegalStateException("Disputed records show both versions side by side and are never deleted.")
                )
            }
            val batch = db.batch()
            val now = FieldValue.serverTimestamp()
            batch.delete(db.collection(COL_INCOME_RECORDS).document(incomeRecordId))
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "INCOME_RECORD_REMOVED",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_INCOME_RECORDS,
                    "targetId" to incomeRecordId,
                    "summary" to "Removed income record: ${summaryLabel.take(160)}",
                    "createdAt" to now
                )
            )
            batch.commit().await()
            runReconciliationAndPersist(uid, workerId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Counterparty Confirmation Requests (Tokenized Public Flow + Legacy Simulation) ---

    suspend fun createConfirmationRequest(
        userId: String,
        workerId: String,
        record: IncomeRecord,
        workerDisplayName: String = "",
        existingRequestCountForRecord: Int = 0
    ): Result<String> {
        val token = CounterpartyConfirmationRules.generateRandomToken()
        val path = "$COL_CONFIRMATION_REQUESTS/$token"
        return try {
            val uid = requireUserId(userId)
            val resolvedWorkerName = workerDisplayName.trim().ifBlank {
                db.collection(COL_WORKERS).document(workerId).get().await()
                    .getString("displayName").orEmpty().ifBlank { "Worker" }
            }.take(80)

            val partyId = record.partyId.ifBlank { "party_unlinked" }
            val claimVersion = (existingRequestCountForRecord + 1).coerceIn(1, 1000)
            val workDescription = buildString {
                if (record.periodLabel.isNotBlank()) append(record.periodLabel)
                else append("Work for ${record.partyName.ifBlank { "Client" }}")
                if (record.invoiceNumber.isNotBlank()) append(" · Invoice ${record.invoiceNumber}")
                else if (record.referenceCode.isNotBlank()) append(" · Ref ${record.referenceCode}")
            }.take(240)
            val demoOtpCode = CounterpartyConfirmationRules.generateDemoOtpCode()
            val expiresAtTs = Timestamp(
                Date(CounterpartyConfirmationRules.computeExpiryEpochMs(System.currentTimeMillis()))
            )
            val claimSnapshot = mapOf(
                "workerName" to resolvedWorkerName,
                "workDescription" to workDescription,
                "requestedAmountRupees" to record.amountRupees,
                "earnedDate" to record.earnedDate.take(30),
                "periodLabel" to record.periodLabel.take(80),
                "claimVersion" to claimVersion
            )

            val batch = db.batch()
            val now = FieldValue.serverTimestamp()
            batch.set(
                db.collection(COL_CONFIRMATION_REQUESTS).document(token),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "incomeRecordId" to record.id,
                    "partyId" to partyId,
                    "partyName" to record.partyName.ifBlank { "Client" }.take(100),
                    "workerName" to resolvedWorkerName,
                    "workDescription" to workDescription,
                    "earnedDate" to record.earnedDate.take(30),
                    "periodLabel" to record.periodLabel.take(80),
                    "requestedAmountRupees" to record.amountRupees,
                    "claimVersion" to claimVersion,
                    "claimSnapshot" to claimSnapshot,
                    "demoOtpCode" to demoOtpCode,
                    "expiresAt" to expiresAtTs,
                    "status" to ConfirmationStatus.PENDING.code,
                    "responseNote" to "",
                    "responses" to emptyMap<String, Any>(),
                    "otp_attempts" to 0,
                    "workerResolution" to "",
                    "createdAt" to now,
                    "updatedAt" to now
                )
            )
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "CONFIRMATION_REQUESTED",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_CONFIRMATION_REQUESTS,
                    "targetId" to token,
                    "summary" to "Created 7-day counterparty confirmation link (claim v$claimVersion) for ${record.partyName} (${RupeeFormatter.format(record.amountRupees)}).".take(300),
                    "createdAt" to now
                )
            )
            batch.commit().await()
            Result.success(token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Syncs newly received public counterparty confirmation responses into the worker's private
     * collections (`parties.partyContactStatus = contact_verified`, `relationships.relationshipStatus =
     * counterparty_acknowledged`, `income_records.paymentStatus = payer_confirmed`, and dispute flag
     * on any "No" or corrected amount) without ever upgrading `evidence_label`.
     */
    suspend fun syncCounterpartyResponsesToLedger(
        userId: String,
        workerId: String,
        unsyncedRequests: List<ConfirmationRequest>
    ): Result<Int> {
        if (unsyncedRequests.isEmpty()) return Result.success(0)
        return try {
            val uid = requireUserId(userId)
            val existingParties = db.collection(COL_PARTIES)
                .whereEqualTo("userId", uid).get().await()
                .toObjects(Party::class.java)
                .filter { it.workerId == workerId }
                .associateBy { it.id }

            val batch = db.batch()
            val now = FieldValue.serverTimestamp()
            val updatedPartyIds = mutableSetOf<String>()

            unsyncedRequests.forEach { req ->
                val outcome = CounterpartyConfirmationRules.evaluateRequest(req)
                val nextResolution = if (req.workerResolution.isBlank()) {
                    WorkerConfirmationResolution.PENDING_REVIEW.code
                } else {
                    req.workerResolution
                }

                batch.update(
                    db.collection(COL_CONFIRMATION_REQUESTS).document(req.id),
                    mapOf(
                        "appliedToLedgerAt" to now,
                        "workerResolution" to nextResolution,
                        "responseNote" to outcome.summaryLine.take(300),
                        "updatedAt" to now
                    )
                )

                val party = existingParties[req.partyId]
                if (outcome.contactVerified && party != null &&
                    party.partyContactStatus != PartyContactStatus.CONTACT_VERIFIED.code &&
                    updatedPartyIds.add(party.id)
                ) {
                    batch.update(
                        db.collection(COL_PARTIES).document(party.id),
                        mapOf(
                            "partyContactStatus" to PartyContactStatus.CONTACT_VERIFIED.code,
                            "updatedAt" to now
                        )
                    )
                    batch.set(
                        db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                        mapOf(
                            "userId" to uid,
                            "workerId" to workerId,
                            "actionType" to "PARTY_CONTACT_VERIFIED",
                            "actorRole" to ActorRole.RULES.code,
                            "targetCollection" to COL_PARTIES,
                            "targetId" to party.id,
                            "summary" to "${party.name} contact status -> contact_verified after DEMO OTP confirmation (channel control only, not legal identity).".take(300),
                            "createdAt" to now
                        )
                    )
                }

                val parsed = CounterpartyConfirmationRules.parseResponses(req.responses)
                val disputeTag = if (outcome.triggersDispute) " Triggered Disputed flag." else " Evidence label unchanged."
                batch.set(
                    db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                    mapOf(
                        "userId" to uid,
                        "workerId" to workerId,
                        "actionType" to "COUNTERPARTY_RESPONSE_RECORDED",
                        "actorRole" to ActorRole.PARTY_SIMULATED.code,
                        "targetCollection" to COL_CONFIRMATION_REQUESTS,
                        "targetId" to req.id,
                        "summary" to "${req.partyName} answered claim v${parsed.claimVersionShown}: ${outcome.summaryLine}.$disputeTag".take(300),
                        "createdAt" to now
                    )
                )
            }

            batch.commit().await()
            runReconciliationAndPersist(uid, workerId)
            Result.success(unsyncedRequests.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, COL_CONFIRMATION_REQUESTS)
            Result.failure(e)
        }
    }

    /**
     * Worker accepts the counterparty's corrected amount: updates the income record amount,
     * clears the dispute flag, re-runs deterministic reconciliation, and writes an audit_log entry.
     */
    suspend fun acceptClientCorrection(
        userId: String,
        workerId: String,
        request: ConfirmationRequest,
        record: IncomeRecord,
        correctedAmountRupees: Long
    ): Result<Unit> {
        val path = "$COL_INCOME_RECORDS/${record.id}"
        return try {
            val uid = requireUserId(userId)
            require(correctedAmountRupees in 1L..100_000_000L) {
                "Corrected amount must be a valid rupee value."
            }
            val oldAmount = record.amountRupees
            val cleanBaseline = EvidenceClassifier.onDisputeCleared(
                hasConfirmedBankMatch = false,
                hasConfirmedDocument = record.documentId.isNotBlank(),
                simulatedApi = record.simulatedApi
            )
            val batch = db.batch()
            val now = FieldValue.serverTimestamp()

            val recordUpdates = linkedMapOf<String, Any>(
                "amountRupees" to correctedAmountRupees,
                "evidenceStatus" to cleanBaseline.code,
                "manuallyDisputed" to false,
                "conflictReason" to "",
                "updatedAt" to now
            )
            if (record.invoiceAmountRupees > 0L) {
                recordUpdates["invoiceAmountRupees"] = correctedAmountRupees
            }
            if (record.netAmountRupees > 0L && record.grossAmountRupees == 0L) {
                recordUpdates["netAmountRupees"] = correctedAmountRupees
            }

            batch.update(
                db.collection(COL_INCOME_RECORDS).document(record.id),
                recordUpdates
            )
            batch.update(
                db.collection(COL_CONFIRMATION_REQUESTS).document(request.id),
                mapOf(
                    "workerResolution" to WorkerConfirmationResolution.CORRECTION_ACCEPTED.code,
                    "appliedToLedgerAt" to now,
                    "responseNote" to "Worker accepted ${request.partyName}'s corrected amount (${RupeeFormatter.format(correctedAmountRupees)}).".take(300),
                    "updatedAt" to now
                )
            )
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "CLIENT_CORRECTION_ACCEPTED",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_INCOME_RECORDS,
                    "targetId" to record.id,
                    "summary" to "Worker accepted ${request.partyName}'s correction: ${RupeeFormatter.format(oldAmount)} -> ${RupeeFormatter.format(correctedAmountRupees)}. Dispute cleared and reconciliation re-run.".take(300),
                    "createdAt" to now
                )
            )
            batch.commit().await()
            runReconciliationAndPersist(uid, workerId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Worker disputes the counterparty's confirmation response: keeps/sets DISPUTED,
     * preserves both versions side by side (never deleted), and logs an audit_log entry.
     */
    suspend fun disputeClientResponse(
        userId: String,
        workerId: String,
        request: ConfirmationRequest,
        record: IncomeRecord,
        workerNote: String
    ): Result<Unit> {
        val path = "$COL_INCOME_RECORDS/${record.id}"
        return try {
            val uid = requireUserId(userId)
            val cleanNote = workerNote.trim().ifBlank {
                "Worker disputed ${request.partyName}'s confirmation response."
            }.take(300)
            val batch = db.batch()
            val now = FieldValue.serverTimestamp()

            batch.update(
                db.collection(COL_INCOME_RECORDS).document(record.id),
                mapOf(
                    "evidenceStatus" to EvidenceStatus.DISPUTED.code,
                    "manuallyDisputed" to true,
                    "conflictReason" to cleanNote,
                    "updatedAt" to now
                )
            )
            batch.update(
                db.collection(COL_CONFIRMATION_REQUESTS).document(request.id),
                mapOf(
                    "workerResolution" to WorkerConfirmationResolution.DISPUTED_BY_WORKER.code,
                    "appliedToLedgerAt" to now,
                    "responseNote" to cleanNote,
                    "updatedAt" to now
                )
            )
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "WORKER_DISPUTED_CLIENT_RESPONSE",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_INCOME_RECORDS,
                    "targetId" to record.id,
                    "summary" to "Worker disputed ${request.partyName}'s confirmation response -> Disputed (both versions kept). Note: ${cleanNote.take(160)}".take(300),
                    "createdAt" to now
                )
            )
            batch.commit().await()
            runReconciliationAndPersist(uid, workerId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun resolveConfirmationRequestSimulated(
        userId: String,
        workerId: String,
        request: ConfirmationRequest,
        decision: ConfirmationStatus,
        responseNote: String,
        workDelivered: Boolean = true,
        confirmedAmountRupees: Long = request.requestedAmountRupees,
        paymentSent: Boolean = decision == ConfirmationStatus.CONFIRMED_SIMULATED,
        paymentDate: String = if (decision == ConfirmationStatus.CONFIRMED_SIMULATED) "2026-11-19" else ""
    ): Result<Unit> {
        val path = "$COL_CONFIRMATION_REQUESTS/${request.id}"
        return try {
            val uid = requireUserId(userId)
            val batch = db.batch()
            val now = FieldValue.serverTimestamp()
            batch.update(
                db.collection(COL_CONFIRMATION_REQUESTS).document(request.id),
                mapOf(
                    "status" to decision.code,
                    "responseNote" to responseNote.trim().take(300),
                    "workDelivered" to workDelivered,
                    "confirmedAmountRupees" to confirmedAmountRupees.coerceIn(0L, 100_000_000L),
                    "paymentSent" to paymentSent,
                    "paymentDate" to paymentDate.trim().take(30),
                    "updatedAt" to now
                )
            )
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "PARTY_RESPONSE_SIMULATED",
                    "actorRole" to ActorRole.PARTY_SIMULATED.code,
                    "targetCollection" to COL_CONFIRMATION_REQUESTS,
                    "targetId" to request.id,
                    "summary" to "${request.partyName} answered (${decision.label}). Running reconciliation rules.".take(300),
                    "createdAt" to now
                )
            )
            batch.commit().await()
            // Rule 7: Reconciliation updates relationship/payment status without changing evidence label (except DISPUTED)
            runReconciliationAndPersist(uid, workerId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Loads the worker's current collections, runs the standalone plain-code ReconciliationModule,
     * persists all derived label/grouping/match/classification/relationship updates, and writes
     * an audit_log entry for every label or status change.
     */
    suspend fun runReconciliationAndPersist(
        userId: String,
        workerId: String
    ): Result<Int> {
        return try {
            val uid = requireUserId(userId)
            val workerDoc = db.collection(COL_WORKERS).document(workerId).get().await()
            val workerName = workerDoc.getString("displayName").orEmpty()

            val incomes = db.collection(COL_INCOME_RECORDS)
                .whereEqualTo("userId", uid).get().await()
                .toObjects(IncomeRecord::class.java)
                .filter { it.workerId == workerId }
            val documents = db.collection(COL_DOCUMENTS)
                .whereEqualTo("userId", uid).get().await()
                .toObjects(IncomeDocument::class.java)
                .filter { it.workerId == workerId }
            val parties = db.collection(COL_PARTIES)
                .whereEqualTo("userId", uid).get().await()
                .toObjects(Party::class.java)
                .filter { it.workerId == workerId }
            val credits = db.collection(COL_BANK_CREDITS)
                .whereEqualTo("userId", uid).get().await()
                .toObjects(BankCredit::class.java)
                .filter { it.workerId == workerId }
            val reconciliations = db.collection(COL_RECONCILIATIONS)
                .whereEqualTo("userId", uid).get().await()
                .toObjects(Reconciliation::class.java)
                .filter { it.workerId == workerId }
            val confirmations = db.collection(COL_CONFIRMATION_REQUESTS)
                .whereEqualTo("userId", uid).get().await()
                .toObjects(ConfirmationRequest::class.java)
                .filter { it.workerId == workerId }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            val relationships = db.collection(COL_RELATIONSHIPS)
                .whereEqualTo("userId", uid).get().await()
                .toObjects(WorkRelationship::class.java)
                .filter { it.workerId == workerId }

            val result = ReconciliationEngine.reconcileWorkspace(
                workerDisplayName = workerName,
                incomeRecords = incomes,
                documents = documents,
                parties = parties,
                bankCredits = credits,
                confirmations = confirmations,
                relationships = relationships,
                reconciliations = reconciliations
            )

            val ops = mutableListOf<(com.google.firebase.firestore.WriteBatch, Any) -> Unit>()

            // 1. Update changed income_records
            incomes.forEach { inc ->
                val computed = result.incomeResults[inc.id] ?: return@forEach
                val nextLabel = computed.evidenceLabel.code
                val nextGroupKey = computed.duplicateGroupKey.take(128)
                val nextGroupedInto = computed.groupedIntoRecordId.take(128)
                val nextOutcome = computed.matchOutcome.code
                val nextPayStatus = computed.paymentStatus.code
                val nextPayDate = computed.paymentDate.take(30)
                val nextConflict = computed.conflictReason.take(200)

                val changed = inc.evidenceStatus != nextLabel ||
                    inc.duplicateGroupKey != nextGroupKey ||
                    inc.groupedIntoRecordId != nextGroupedInto ||
                    inc.matchOutcome != nextOutcome ||
                    inc.paymentStatus != nextPayStatus ||
                    inc.paymentDate != nextPayDate ||
                    inc.conflictReason != nextConflict

                if (changed) {
                    ops.add { batch, now ->
                        batch.update(
                            db.collection(COL_INCOME_RECORDS).document(inc.id),
                            mapOf(
                                "evidenceStatus" to nextLabel,
                                "duplicateGroupKey" to nextGroupKey,
                                "groupedIntoRecordId" to nextGroupedInto,
                                "matchOutcome" to nextOutcome,
                                "paymentStatus" to nextPayStatus,
                                "paymentDate" to nextPayDate,
                                "conflictReason" to nextConflict,
                                "updatedAt" to now
                            )
                        )
                    }
                }
            }

            // 2. Update changed bank_credits
            credits.forEach { bc ->
                val computed = result.bankCreditResults[bc.id] ?: return@forEach
                val nextMatchedId = computed.matchedIncomeRecordId.take(128)
                val nextClass = computed.classification.code
                val nextEvidence = computed.evidenceLabel?.code.orEmpty()

                val changed = bc.matchedIncomeRecordId != nextMatchedId ||
                    bc.classificationStatus != nextClass ||
                    bc.evidenceStatus != nextEvidence

                if (changed) {
                    ops.add { batch, now ->
                        batch.update(
                            db.collection(COL_BANK_CREDITS).document(bc.id),
                            mapOf(
                                "matchedIncomeRecordId" to nextMatchedId,
                                "classificationStatus" to nextClass,
                                "evidenceStatus" to nextEvidence,
                                "updatedAt" to now
                            )
                        )
                    }
                }
            }

            // 3. Update changed relationships
            relationships.forEach { rel ->
                val computed = result.relationshipResults[rel.id] ?: return@forEach
                val nextRelStatus = computed.relationshipStatus.code
                if (rel.relationshipStatus != nextRelStatus) {
                    ops.add { batch, now ->
                        batch.update(
                            db.collection(COL_RELATIONSHIPS).document(rel.id),
                            mapOf(
                                "relationshipStatus" to nextRelStatus,
                                "updatedAt" to now
                            )
                        )
                    }
                }
            }

            // 4. Create new CONFIRMED reconciliations for auto-applied STRONG matches
            result.newStrongMatches.forEach { strong ->
                val recId = newSafeId("rec")
                ops.add { batch, now ->
                    batch.set(
                        db.collection(COL_RECONCILIATIONS).document(recId),
                        mapOf(
                            "userId" to uid,
                            "workerId" to workerId,
                            "incomeRecordId" to strong.incomeRecordId,
                            "bankCreditId" to strong.bankCreditId,
                            "matchRule" to strong.candidate.matchRule.take(120),
                            "amountDeltaRupees" to strong.candidate.amountDeltaRupees,
                            "dateGapDays" to strong.candidate.dateGapDays.coerceIn(0L, 365L),
                            "decisionStatus" to ReconciliationDecision.CONFIRMED.code,
                            "matchOutcome" to MatchOutcome.STRONG.code,
                            "createdAt" to now,
                            "updatedAt" to now
                        )
                    )
                }
            }

            // 5. Append one audit_log entry for EVERY label or status change
            result.auditChanges.forEach { change ->
                val logId = newSafeId("log")
                ops.add { batch, now ->
                    batch.set(
                        db.collection(COL_AUDIT_LOG).document(logId),
                        mapOf(
                            "userId" to uid,
                            "workerId" to workerId,
                            "actionType" to change.actionType.take(60),
                            "actorRole" to ActorRole.RULES.code,
                            "targetCollection" to change.targetCollection.take(40),
                            "targetId" to change.targetId.take(128),
                            "summary" to change.summary.take(300),
                            "createdAt" to now
                        )
                    )
                }
            }

            if (ops.isNotEmpty()) {
                val now = FieldValue.serverTimestamp()
                ops.chunked(380).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { apply -> apply(batch, now) }
                    batch.commit().await()
                }
            }

            Result.success(result.auditChanges.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$COL_WORKERS/$workerId")
            Result.failure(e)
        }
    }

    // --- Consent-Scoped Share Links ---

    suspend fun createShareLink(
        userId: String,
        worker: WorkerProfile,
        recipientLabel: String,
        dateRangeLabel: String,
        includedPartyIds: List<String>,
        includedEvidenceLevels: List<String>,
        maskBankRefs: Boolean,
        expiresOn: String,
        incomeRecords: List<IncomeRecord>
    ): Result<String> {
        val shareId = newSafeId("share")
        val path = "$COL_SHARE_LINKS/$shareId"
        return try {
            val uid = requireUserId(userId)
            val cleanRecipient = recipientLabel.trim().take(100)
            require(cleanRecipient.isNotBlank()) { "Please enter who you are sharing this report with." }
            require(includedEvidenceLevels.isNotEmpty()) { "Select at least one evidence level to include." }

            val (totalRupees, summaryLines) = PassportAggregator.buildShareSnapshot(
                incomeRecords = incomeRecords,
                includedPartyIds = includedPartyIds.toSet(),
                includedEvidenceCodes = includedEvidenceLevels.toSet(),
                maskBankRefs = maskBankRefs
            )
            val prefix = worker.displayName.uppercase().replace(Regex("[^A-Z]"), "").take(4).ifBlank { "WORK" }
            val codeSuffix = (1000..9999).random()
            val shareCode = "$prefix-$codeSuffix"
            val consentText = "Worker (${worker.displayName}) consented to share this read-only earnings report with $cleanRecipient. Not a credit score or loan approval."

            val batch = db.batch()
            val now = FieldValue.serverTimestamp()
            batch.set(
                db.collection(COL_SHARE_LINKS).document(shareId),
                mapOf(
                    "userId" to uid,
                    "workerId" to worker.id,
                    "workerName" to worker.displayName.take(80),
                    "shareCode" to shareCode,
                    "recipientLabel" to cleanRecipient,
                    "dateRangeLabel" to dateRangeLabel.trim().ifBlank { "Recent earnings" }.take(80),
                    "includedPartyIds" to includedPartyIds.take(50),
                    "includedEvidenceLevels" to includedEvidenceLevels.take(10),
                    "maskBankRefs" to maskBankRefs,
                    "expiresOn" to expiresOn.trim().ifBlank { "2026-12-31" }.take(30),
                    "status" to ShareLinkStatus.ACTIVE.code,
                    "totalIncludedRupees" to totalRupees,
                    "summaryLines" to summaryLines.take(100),
                    "consentText" to consentText.take(400),
                    "createdAt" to now,
                    "updatedAt" to now
                )
            )
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to worker.id,
                    "actionType" to "SHARE_LINK_CREATED",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_SHARE_LINKS,
                    "targetId" to shareId,
                    "summary" to "Created read-only consent report $shareCode for $cleanRecipient (${RupeeFormatter.format(totalRupees)}).",
                    "createdAt" to now
                )
            )
            batch.commit().await()
            Result.success(shareId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun revokeShareLink(
        userId: String,
        workerId: String,
        shareLink: ShareLink
    ): Result<Unit> {
        val path = "$COL_SHARE_LINKS/${shareLink.id}"
        return try {
            val uid = requireUserId(userId)
            val batch = db.batch()
            val now = FieldValue.serverTimestamp()
            batch.update(
                db.collection(COL_SHARE_LINKS).document(shareLink.id),
                mapOf(
                    "status" to ShareLinkStatus.REVOKED.code,
                    "updatedAt" to now
                )
            )
            batch.set(
                db.collection(COL_AUDIT_LOG).document(newSafeId("log")),
                mapOf(
                    "userId" to uid,
                    "workerId" to workerId,
                    "actionType" to "SHARE_LINK_REVOKED",
                    "actorRole" to ActorRole.WORKER.code,
                    "targetCollection" to COL_SHARE_LINKS,
                    "targetId" to shareLink.id,
                    "summary" to "Revoked read-only share link ${shareLink.shareCode} (${shareLink.recipientLabel}).",
                    "createdAt" to now
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    companion object {
        const val COL_WORKERS = "workers"
        const val COL_PARTIES = "parties"
        const val COL_RELATIONSHIPS = "relationships"
        const val COL_INCOME_RECORDS = "income_records"
        const val COL_DOCUMENTS = "documents"
        const val COL_BANK_CREDITS = "bank_credits"
        const val COL_RECONCILIATIONS = "reconciliations"
        const val COL_CONFIRMATION_REQUESTS = "confirmation_requests"
        const val COL_SHARE_LINKS = "share_links"
        const val COL_AUDIT_LOG = "audit_log"
    }
}
