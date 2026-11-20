package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.ExtractedDocumentDraft
import com.example.domain.rules.AiUncertaintySummary
import com.example.domain.rules.DocumentExtractionValidator
import com.example.domain.rules.EditedFieldChange
import com.example.domain.rules.ExtractionValidationFlag
import com.example.domain.rules.PartyRules
import com.example.domain.rules.ReviewConfirmRules
import com.example.domain.rules.ReviewFormValues
import com.example.domain.rules.RupeeFormatter
import com.example.model.EvidenceStatus
import com.example.model.IncomeDocument
import com.example.model.Party
import com.example.model.PartyRole
import com.example.model.PartyType
import com.example.ui.components.AddPartyFormCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.EvidenceBadge
import com.example.ui.components.ManualIncomeEntryCard
import com.example.ui.components.NeutralSmallChip
import com.example.ui.components.OriginalFilePreviewCard
import com.example.ui.components.PassportSectionCard
import com.example.ui.components.SimilarPartyWarningCard
import com.example.ui.theme.CardSurface
import com.example.ui.theme.EvidenceDisputedBg
import com.example.ui.theme.EvidenceDisputedInk
import com.example.ui.theme.EvidenceDocExtractedBg
import com.example.ui.theme.EvidenceDocExtractedInk
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.OnDarkInk
import com.example.ui.theme.StoneSurface
import com.example.viewmodel.ExtractionUiState
import com.example.viewmodel.PassportWorkspaceData

@Composable
fun ReviewConfirmScreen(
    workspace: PassportWorkspaceData,
    extractionState: ExtractionUiState,
    isBusy: Boolean,
    onSelectPendingDoc: (IncomeDocument) -> Unit,
    onQuickAddParty: (
        name: String,
        type: PartyType,
        roles: List<PartyRole>,
        phone: String,
        email: String,
        note: String,
        registrationNumber: String,
        onCreated: (partyId: String, partyName: String) -> Unit
    ) -> Unit,
    onConfirmSave: (
        existingDocId: String?,
        draft: ExtractedDocumentDraft,
        form: ReviewFormValues,
        workGiverPartyId: String,
        workGiverPartyName: String,
        payerPartyId: String,
        payerPartyName: String,
        workerConfirmed: Boolean
    ) -> Unit,
    onDiscardDraft: (String?) -> Unit,
    onAddManualSelfDeclared: (
        partyId: String,
        partyName: String,
        amountRupees: Long,
        earnedDate: String,
        periodLabel: String,
        referenceCode: String,
        notes: String
    ) -> Unit,
    onBack: () -> Unit,
    onNavigateToAddDoc: () -> Unit
) {
    val activeReview = extractionState as? ExtractionUiState.ReadyForReview
    var showManualEntry by remember { mutableStateOf(false) }

    // Switch back to document review automatically when a fresh draft arrives
    LaunchedEffect(activeReview) {
        if (activeReview != null) {
            showManualEntry = false
        }
    }

    // Auto-select the first pending seeded document if no draft is active and user isn't in manual mode
    LaunchedEffect(activeReview, workspace.pendingDocuments, showManualEntry) {
        if (activeReview == null && !showManualEntry && workspace.pendingDocuments.isNotEmpty()) {
            onSelectPendingDoc(workspace.pendingDocuments.first())
        }
    }

    val initialForm = remember(activeReview) {
        val draft = activeReview?.draft
        val raw = draft?.aiOriginalRaw
        val fallbackParty = draft?.partyName.orEmpty()
        ReviewFormValues(
            title = draft?.title.orEmpty(),
            platform = raw?.platform?.trim().orEmpty().ifBlank {
                if (raw?.payer.isNullOrBlank() && raw?.clientName.isNullOrBlank()) fallbackParty else ""
            },
            payer = raw?.payer?.trim().orEmpty(),
            clientName = raw?.clientName?.trim().orEmpty(),
            invoiceNumber = raw?.invoiceNumber?.trim().orEmpty(),
            recordDate = raw?.recordDate?.trim().orEmpty().ifBlank { draft?.earnedDate.orEmpty() },
            periodStart = raw?.periodStart?.trim().orEmpty(),
            periodEnd = raw?.periodEnd?.trim().orEmpty(),
            grossAmountText = raw?.grossAmountRaw?.trim().orEmpty(),
            deductionsText = raw?.deductionsRaw?.trim().orEmpty(),
            netAmountText = raw?.netAmountRaw?.trim().orEmpty().ifBlank {
                if (raw?.invoiceAmountRaw.isNullOrBlank() &&
                    raw?.grossAmountRaw.isNullOrBlank() &&
                    (draft?.amountRupees ?: 0L) > 0L
                ) {
                    draft?.amountRupees.toString()
                } else {
                    ""
                }
            },
            invoiceAmountText = raw?.invoiceAmountRaw?.trim().orEmpty(),
            reference = raw?.reference?.trim().orEmpty().ifBlank { draft?.referenceCode.orEmpty() },
            notes = raw?.notes?.trim().orEmpty().ifBlank { draft?.extractionNotes.orEmpty() }
        )
    }

    val initialWorkGiverName = remember(activeReview) {
        val raw = activeReview?.draft?.aiOriginalRaw
        listOfNotNull(
            raw?.platform?.trim()?.takeIf { it.isNotBlank() },
            raw?.clientName?.trim()?.takeIf { it.isNotBlank() },
            activeReview?.draft?.partyName?.trim()?.takeIf { it.isNotBlank() },
            raw?.payer?.trim()?.takeIf { it.isNotBlank() }
        ).firstOrNull().orEmpty()
    }
    val initialPayerRaw = remember(activeReview) {
        activeReview?.draft?.aiOriginalRaw?.payer?.trim().orEmpty()
    }
    val initialSameParty = remember(activeReview) {
        initialPayerRaw.isBlank() || initialPayerRaw.equals(initialWorkGiverName, ignoreCase = true)
    }

    var formState by remember(activeReview) { mutableStateOf(initialForm) }
    var workGiverPartyName by remember(activeReview) { mutableStateOf(initialWorkGiverName) }
    var workGiverPartyId by remember(activeReview) {
        mutableStateOf(
            workspace.parties.firstOrNull { it.name.equals(initialWorkGiverName, ignoreCase = true) }?.id.orEmpty()
        )
    }
    var samePartyPaid by remember(activeReview) { mutableStateOf(initialSameParty) }
    var payerPartyName by remember(activeReview) {
        mutableStateOf(if (initialSameParty) initialWorkGiverName else initialPayerRaw)
    }
    var payerPartyId by remember(activeReview) {
        mutableStateOf(
            if (initialSameParty) {
                workspace.parties.firstOrNull { it.name.equals(initialWorkGiverName, ignoreCase = true) }?.id.orEmpty()
            } else {
                workspace.parties.firstOrNull { it.name.equals(initialPayerRaw, ignoreCase = true) }?.id.orEmpty()
            }
        )
    }
    var workerCheckedAllValues by remember(activeReview) { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("review_confirm_screen")
    ) {
        val isWideLayout = maxWidth >= 720.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar with Back + Add income manually toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("review_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Go back",
                            tint = InkPrimary
                        )
                    }
                    Column {
                        Text(
                            text = "Review & Confirm",
                            style = MaterialTheme.typography.headlineSmall,
                            color = InkPrimary
                        )
                        Text(
                            text = "Nothing saves until you tap Confirm.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { showManualEntry = !showManualEntry },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("review_add_manual_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (showManualEntry) InkPrimary else HairlineBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (showManualEntry) InkPrimary else CardSurface,
                        contentColor = if (showManualEntry) OnDarkInk else InkPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (showManualEntry) "Back to Review" else "Add manually",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            when {
                showManualEntry -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            ManualIncomeEntryCard(
                                parties = workspace.parties,
                                isBusy = isBusy,
                                onSaveSelfDeclared = onAddManualSelfDeclared,
                                onCancel = if (activeReview != null) {
                                    { showManualEntry = false }
                                } else {
                                    null
                                },
                                modifier = Modifier.widthIn(max = 640.dp)
                            )
                        }
                    }
                }

                activeReview == null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            EmptyStateCard(
                                title = "No document ready for review",
                                body = "Upload a PDF or image in the Add tab to inspect Gemini AI extraction beside the original file, or add an entry manually without a document.",
                                icon = Icons.Outlined.CheckCircle,
                                modifier = Modifier.widthIn(max = 640.dp),
                                action = {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = onNavigateToAddDoc,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = InkPrimary,
                                                contentColor = OnDarkInk
                                            )
                                        ) {
                                            Text("Read a Document")
                                        }
                                        OutlinedButton(
                                            onClick = { showManualEntry = true },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("empty_state_add_manual_button"),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, HairlineBorder),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = CardSurface,
                                                contentColor = InkPrimary
                                            )
                                        ) {
                                            Text("Add income manually (Self-declared)")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                else -> {
                    val draft = activeReview.draft
                    val uncertainty = remember(draft.aiOriginalRaw) {
                        ReviewConfirmRules.summarizeAiUncertainty(draft.aiOriginalRaw)
                    }
                    val liveValidation = remember(formState, draft.aiOriginalRaw) {
                        ReviewConfirmRules.validateEditedForm(draft.aiOriginalRaw, formState)
                    }
                    val liveEdits = remember(formState, draft.aiOriginalRaw) {
                        ReviewConfirmRules.computeWorkerEdits(draft.aiOriginalRaw, formState)
                    }

                    val effectivePayerPartyId = if (samePartyPaid) workGiverPartyId else payerPartyId
                    val effectivePayerPartyName = if (samePartyPaid) workGiverPartyName else payerPartyName

                    val triggerConfirm = {
                        onConfirmSave(
                            activeReview.savedDocumentId,
                            draft,
                            formState,
                            workGiverPartyId,
                            workGiverPartyName.trim(),
                            effectivePayerPartyId,
                            effectivePayerPartyName.trim(),
                            workerCheckedAllValues
                        )
                    }

                    if (isWideLayout) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(0.45f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OriginalFilePreviewCard(
                                    draft = draft,
                                    isUnsavedInMemory = activeReview.savedDocumentId == null,
                                    isWidePane = true
                                )
                                PendingDocsQueueSection(
                                    pendingDocs = workspace.pendingDocuments,
                                    activeDocId = activeReview.savedDocumentId,
                                    onSelectPendingDoc = onSelectPendingDoc
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(0.55f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ReviewFormColumnContent(
                                    draft = draft,
                                    savedDocumentId = activeReview.savedDocumentId,
                                    parties = workspace.parties,
                                    formState = formState,
                                    onFormChange = { formState = it },
                                    workGiverPartyId = workGiverPartyId,
                                    workGiverPartyName = workGiverPartyName,
                                    onWorkGiverChange = { id, name ->
                                        workGiverPartyId = id
                                        workGiverPartyName = name
                                        if (samePartyPaid) {
                                            payerPartyId = id
                                            payerPartyName = name
                                        }
                                    },
                                    samePartyPaid = samePartyPaid,
                                    onSamePartyPaidChange = { same ->
                                        samePartyPaid = same
                                        if (same) {
                                            payerPartyId = workGiverPartyId
                                            payerPartyName = workGiverPartyName
                                        }
                                    },
                                    payerPartyId = payerPartyId,
                                    payerPartyName = payerPartyName,
                                    onPayerChange = { id, name ->
                                        payerPartyId = id
                                        payerPartyName = name
                                    },
                                    onQuickAddParty = onQuickAddParty,
                                    uncertainty = uncertainty,
                                    liveFlags = liveValidation.flags,
                                    liveEdits = liveEdits,
                                    workerChecked = workerCheckedAllValues,
                                    onWorkerCheckedChange = { workerCheckedAllValues = it },
                                    isBusy = isBusy,
                                    onConfirm = triggerConfirm,
                                    onDiscard = { onDiscardDraft(activeReview.savedDocumentId) }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                OriginalFilePreviewCard(
                                    draft = draft,
                                    isUnsavedInMemory = activeReview.savedDocumentId == null,
                                    isWidePane = false,
                                    modifier = Modifier.widthIn(max = 640.dp)
                                )
                            }

                            item {
                                Column(
                                    modifier = Modifier.widthIn(max = 640.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ReviewFormColumnContent(
                                        draft = draft,
                                        savedDocumentId = activeReview.savedDocumentId,
                                        parties = workspace.parties,
                                        formState = formState,
                                        onFormChange = { formState = it },
                                        workGiverPartyId = workGiverPartyId,
                                        workGiverPartyName = workGiverPartyName,
                                        onWorkGiverChange = { id, name ->
                                            workGiverPartyId = id
                                            workGiverPartyName = name
                                            if (samePartyPaid) {
                                                payerPartyId = id
                                                payerPartyName = name
                                            }
                                        },
                                        samePartyPaid = samePartyPaid,
                                        onSamePartyPaidChange = { same ->
                                            samePartyPaid = same
                                            if (same) {
                                                payerPartyId = workGiverPartyId
                                                payerPartyName = workGiverPartyName
                                            }
                                        },
                                        payerPartyId = payerPartyId,
                                        payerPartyName = payerPartyName,
                                        onPayerChange = { id, name ->
                                            payerPartyId = id
                                            payerPartyName = name
                                        },
                                        onQuickAddParty = onQuickAddParty,
                                        uncertainty = uncertainty,
                                        liveFlags = liveValidation.flags,
                                        liveEdits = liveEdits,
                                        workerChecked = workerCheckedAllValues,
                                        onWorkerCheckedChange = { workerCheckedAllValues = it },
                                        isBusy = isBusy,
                                        onConfirm = triggerConfirm,
                                        onDiscard = { onDiscardDraft(activeReview.savedDocumentId) }
                                    )
                                }
                            }

                            val otherPending = workspace.pendingDocuments.filter { it.id != activeReview.savedDocumentId }
                            if (otherPending.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Other Documents Waiting (${otherPending.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = InkPrimary,
                                        modifier = Modifier
                                            .widthIn(max = 640.dp)
                                            .fillMaxWidth()
                                    )
                                }
                                items(otherPending, key = { it.id }) { doc ->
                                    PendingDocumentQueueItem(
                                        doc = doc,
                                        onClick = { onSelectPendingDoc(doc) },
                                        modifier = Modifier.widthIn(max = 640.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewFormColumnContent(
    draft: ExtractedDocumentDraft,
    savedDocumentId: String?,
    parties: List<Party>,
    formState: ReviewFormValues,
    onFormChange: (ReviewFormValues) -> Unit,
    workGiverPartyId: String,
    workGiverPartyName: String,
    onWorkGiverChange: (partyId: String, partyName: String) -> Unit,
    samePartyPaid: Boolean,
    onSamePartyPaidChange: (Boolean) -> Unit,
    payerPartyId: String,
    payerPartyName: String,
    onPayerChange: (partyId: String, partyName: String) -> Unit,
    onQuickAddParty: (
        name: String,
        type: PartyType,
        roles: List<PartyRole>,
        phone: String,
        email: String,
        note: String,
        registrationNumber: String,
        onCreated: (partyId: String, partyName: String) -> Unit
    ) -> Unit,
    uncertainty: AiUncertaintySummary,
    liveFlags: List<ExtractionValidationFlag>,
    liveEdits: List<EditedFieldChange>,
    workerChecked: Boolean,
    onWorkerCheckedChange: (Boolean) -> Unit,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit
) {
    var showQuickAddWorkGiver by remember(draft.fileHash) { mutableStateOf(false) }
    var showQuickAddPayer by remember(draft.fileHash) { mutableStateOf(false) }

    val similarWorkGiverMatches = remember(workGiverPartyName, workGiverPartyId, parties) {
        if (workGiverPartyId.isNotBlank()) emptyList()
        else PartyRules.findSimilarParties(workGiverPartyName, parties)
    }
    val similarPayerMatches = remember(payerPartyName, payerPartyId, samePartyPaid, parties) {
        if (samePartyPaid || payerPartyId.isNotBlank()) emptyList()
        else PartyRules.findSimilarParties(payerPartyName, parties)
    }

    // 1. "AI was unsure" Callout Banner
    if (uncertainty.hasUncertainty) {
        Surface(
            color = EvidenceDocExtractedBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, EvidenceDocExtractedInk.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_unsure_banner")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HelpOutline,
                        contentDescription = null,
                        tint = EvidenceDocExtractedInk,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "AI was unsure — please check carefully",
                        style = MaterialTheme.typography.titleSmall,
                        color = EvidenceDocExtractedInk
                    )
                }
                if (uncertainty.missingFieldLabels.isNotEmpty()) {
                    Text(
                        text = "Missing / returned null: ${uncertainty.missingFieldLabels.joinToString(", ")}",
                        style = MaterialTheme.typography.labelMedium,
                        color = EvidenceDocExtractedInk
                    )
                }
                if (uncertainty.aiNotes.isNotBlank()) {
                    Text(
                        text = "AI note: ${uncertainty.aiNotes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = EvidenceDocExtractedInk
                    )
                }
            }
        }
    }

    // 2. Live Code Validation Flags Banner (e.g., "Numbers do not add up")
    if (liveFlags.isNotEmpty()) {
        Surface(
            color = EvidenceDisputedBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, EvidenceDisputedInk.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("validation_flags_banner")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = EvidenceDisputedInk,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (liveFlags.any { it.title == DocumentExtractionValidator.NUMBERS_MISMATCH_FLAG_TITLE }) {
                            DocumentExtractionValidator.NUMBERS_MISMATCH_FLAG_TITLE
                        } else {
                            "Validation Check Flagged (${liveFlags.size})"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = EvidenceDisputedInk
                    )
                }
                liveFlags.forEach { flag ->
                    Text(
                        text = "${flag.title}: ${flag.detail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = EvidenceDisputedInk
                    )
                }
            }
        }
    }

    // 3. Link Parties Card: "Who gave you this work?" and "Who paid you?"
    PassportSectionCard(modifier = Modifier.testTag("review_party_linking_card")) {
        Text(
            text = "Who gave you this work?",
            style = MaterialTheme.typography.titleMedium,
            color = InkPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Link an existing party or quick-add a new one. You can choose a different party for who paid you.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary
        )
        Spacer(Modifier.height(8.dp))

        if (parties.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                parties.forEach { party ->
                    val selected = workGiverPartyId == party.id
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onWorkGiverChange(party.id, party.name)
                            if (formState.platform.isBlank() && formState.clientName.isBlank()) {
                                onFormChange(formState.copy(platform = party.name))
                            }
                            showQuickAddWorkGiver = false
                        },
                        label = { Text(party.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = InkPrimary,
                            selectedLabelColor = OnDarkInk,
                            containerColor = StoneSurface,
                            labelColor = InkPrimary
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("work_giver_chip_${party.id}")
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = workGiverPartyName,
            onValueChange = { typed ->
                val exactMatch = parties.firstOrNull { it.name.equals(typed.trim(), ignoreCase = true) }
                onWorkGiverChange(exactMatch?.id.orEmpty(), typed)
            },
            label = { Text("Work giver party name") },
            placeholder = { Text("e.g. Swiggy Delivery, Kulkarni Family") },
            singleLine = true,
            supportingText = {
                if (workGiverPartyId.isNotBlank()) {
                    Text("Linked to existing party")
                } else if (workGiverPartyName.isNotBlank()) {
                    Text("Not yet linked — tap an existing party or quick-add below")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("review_work_giver_input")
        )

        if (similarWorkGiverMatches.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SimilarPartyWarningCard(
                matches = similarWorkGiverMatches,
                onUseExisting = { existing ->
                    onWorkGiverChange(existing.id, existing.name)
                    showQuickAddWorkGiver = false
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showQuickAddWorkGiver = !showQuickAddWorkGiver },
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("quick_add_work_giver_button"),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = CardSurface,
                contentColor = InkPrimary
            )
        ) {
            Icon(Icons.Outlined.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (showQuickAddWorkGiver) "Close Quick-Add" else "Quick-add work giver party",
                style = MaterialTheme.typography.labelLarge
            )
        }

        if (showQuickAddWorkGiver) {
            Spacer(Modifier.height(10.dp))
            AddPartyFormCard(
                existingParties = parties,
                isBusy = isBusy,
                initialName = workGiverPartyName,
                initialRoles = if (samePartyPaid) {
                    setOf(PartyRole.WORK_GIVER, PartyRole.PAYER)
                } else {
                    setOf(PartyRole.WORK_GIVER)
                },
                compactMode = true,
                heading = "Quick-add work source / client",
                submitLabel = "Save & Link Party",
                onSubmit = { name, type, roles, phone, email, note, regNum, _, _, _ ->
                    onQuickAddParty(name, type, roles, phone, email, note, regNum) { newId, newName ->
                        onWorkGiverChange(newId, newName)
                        showQuickAddWorkGiver = false
                    }
                },
                onUseExistingParty = { existing ->
                    onWorkGiverChange(existing.id, existing.name)
                    showQuickAddWorkGiver = false
                },
                onCancel = { showQuickAddWorkGiver = false }
            )
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = HairlineBorder)
        Spacer(Modifier.height(14.dp))

        Text(
            text = "Who paid you?",
            style = MaterialTheme.typography.titleMedium,
            color = InkPrimary
        )
        Spacer(Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSamePartyPaidChange(!samePartyPaid) }
                .testTag("review_same_payer_row"),
            shape = RoundedCornerShape(12.dp),
            color = StoneSurface,
            border = BorderStroke(1.dp, HairlineBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = samePartyPaid,
                    onCheckedChange = onSamePartyPaidChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = InkPrimary,
                        checkmarkColor = OnDarkInk
                    ),
                    modifier = Modifier.testTag("review_same_payer_checkbox")
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Same party paid me",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkPrimary
                    )
                    Text(
                        text = "Uncheck if a different payer or payment provider settled this.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
            }
        }

        if (samePartyPaid) {
            Spacer(Modifier.height(8.dp))
            NeutralSmallChip(
                label = "Payer: ${workGiverPartyName.ifBlank { "Same as work giver" }}"
            )
        } else {
            Spacer(Modifier.height(10.dp))
            if (parties.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    parties.forEach { party ->
                        val selected = payerPartyId == party.id
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onPayerChange(party.id, party.name)
                                onFormChange(formState.copy(payer = party.name))
                                showQuickAddPayer = false
                            },
                            label = { Text(party.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = InkPrimary,
                                selectedLabelColor = OnDarkInk,
                                containerColor = StoneSurface,
                                labelColor = InkPrimary
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("payer_chip_${party.id}")
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = payerPartyName,
                onValueChange = { typed ->
                    val exactMatch = parties.firstOrNull { it.name.equals(typed.trim(), ignoreCase = true) }
                    onPayerChange(exactMatch?.id.orEmpty(), typed)
                },
                label = { Text("Payer / payment provider name") },
                placeholder = { Text("e.g. Razorpay Payouts, Rajesh Kulkarni") },
                singleLine = true,
                supportingText = {
                    if (payerPartyId.isNotBlank()) {
                        Text("Linked to existing payer party")
                    } else if (payerPartyName.isNotBlank()) {
                        Text("Not yet linked — tap an existing party or quick-add below")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("review_distinct_payer_input")
            )

            if (similarPayerMatches.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SimilarPartyWarningCard(
                    matches = similarPayerMatches,
                    onUseExisting = { existing ->
                        onPayerChange(existing.id, existing.name)
                        onFormChange(formState.copy(payer = existing.name))
                        showQuickAddPayer = false
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showQuickAddPayer = !showQuickAddPayer },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("quick_add_payer_button"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CardSurface,
                    contentColor = InkPrimary
                )
            ) {
                Icon(Icons.Outlined.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (showQuickAddPayer) "Close Quick-Add" else "Quick-add payer party",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (showQuickAddPayer) {
                Spacer(Modifier.height(10.dp))
                AddPartyFormCard(
                    existingParties = parties,
                    isBusy = isBusy,
                    initialName = payerPartyName,
                    initialRoles = setOf(PartyRole.PAYER),
                    compactMode = true,
                    heading = "Quick-add payer / payment provider",
                    submitLabel = "Save & Link Payer",
                    onSubmit = { name, type, roles, phone, email, note, regNum, _, _, _ ->
                        onQuickAddParty(name, type, roles, phone, email, note, regNum) { newId, newName ->
                            onPayerChange(newId, newName)
                            onFormChange(formState.copy(payer = newName))
                            showQuickAddPayer = false
                        }
                    },
                    onUseExistingParty = { existing ->
                        onPayerChange(existing.id, existing.name)
                        onFormChange(formState.copy(payer = existing.name))
                        showQuickAddPayer = false
                    },
                    onCancel = { showQuickAddPayer = false }
                )
            }
        }
    }

    // 4. Editable Document Fields & Confirmation Card
    val isRecordDateValid = DocumentExtractionValidator.isValidStrictIsoDate(formState.recordDate)
    val isPeriodStartValid = formState.periodStart.isBlank() || DocumentExtractionValidator.isValidStrictIsoDate(formState.periodStart)
    val isPeriodEndValid = formState.periodEnd.isBlank() || DocumentExtractionValidator.isValidStrictIsoDate(formState.periodEnd)
    val confirmedAmount = formState.resolvedConfirmedAmountRupees()
    val effectiveWorkGiver = workGiverPartyName.trim().ifBlank { formState.resolvedPartyName() }
    val effectivePayer = if (samePartyPaid) effectiveWorkGiver else payerPartyName.trim()

    PassportSectionCard(modifier = Modifier.testTag("review_editable_fields_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EvidenceBadge(status = EvidenceStatus.DOCUMENT_EXTRACTED)
            Surface(
                color = StoneSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Text(
                    text = if (liveEdits.isNotEmpty()) {
                        "Edited (${liveEdits.size})"
                    } else {
                        draft.confidenceHint
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Compare each field with the original file. AI never marks income as verified, and code never changes numbers silently.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = formState.title,
            onValueChange = { onFormChange(formState.copy(title = it)) },
            label = { Text("Document title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        // Section: Extracted Platform / Payer / Client text from document
        Text(
            text = "Extracted Document Names",
            style = MaterialTheme.typography.labelLarge,
            color = InkPrimary
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = formState.platform,
            onValueChange = { onFormChange(formState.copy(platform = it)) },
            label = { Text("Platform (e.g. Urban Company, Swiggy)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("review_platform_input")
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = formState.payer,
                onValueChange = { onFormChange(formState.copy(payer = it)) },
                label = { Text("Payer") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("review_payer_input")
            )
            OutlinedTextField(
                value = formState.clientName,
                onValueChange = { onFormChange(formState.copy(clientName = it)) },
                label = { Text("Client name") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("review_client_input")
            )
        }
        Spacer(Modifier.height(12.dp))

        // Section: Invoice number & Reference
        Text(
            text = "Invoice & Reference",
            style = MaterialTheme.typography.labelLarge,
            color = InkPrimary
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = formState.invoiceNumber,
                onValueChange = { onFormChange(formState.copy(invoiceNumber = it)) },
                label = { Text("Invoice number") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("review_invoice_number_input")
            )
            OutlinedTextField(
                value = formState.reference,
                onValueChange = { onFormChange(formState.copy(reference = it)) },
                label = { Text("Reference / UTR") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("review_reference_input")
            )
        }
        Spacer(Modifier.height(12.dp))

        // Section: Date & Period
        Text(
            text = "Date & Earning Period",
            style = MaterialTheme.typography.labelLarge,
            color = InkPrimary
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = formState.recordDate,
            onValueChange = { onFormChange(formState.copy(recordDate = it)) },
            label = { Text("Record date (YYYY-MM-DD)") },
            placeholder = { Text("2026-11-15") },
            isError = formState.recordDate.isNotBlank() && !isRecordDateValid,
            supportingText = {
                if (formState.recordDate.isBlank()) {
                    Text("Required in YYYY-MM-DD")
                } else if (!isRecordDateValid) {
                    Text("Enter a valid YYYY-MM-DD date")
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("review_date_input")
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = formState.periodStart,
                onValueChange = { onFormChange(formState.copy(periodStart = it)) },
                label = { Text("Period start") },
                placeholder = { Text("YYYY-MM-DD") },
                isError = !isPeriodStartValid,
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("review_period_start_input")
            )
            OutlinedTextField(
                value = formState.periodEnd,
                onValueChange = { onFormChange(formState.copy(periodEnd = it)) },
                label = { Text("Period end") },
                placeholder = { Text("YYYY-MM-DD") },
                isError = !isPeriodEndValid,
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("review_period_end_input")
            )
        }
        Spacer(Modifier.height(12.dp))

        // Section: Amounts (plain numbers, never auto-recalculated)
        Text(
            text = "Amounts in Rupees (Rs)",
            style = MaterialTheme.typography.labelLarge,
            color = InkPrimary
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = formState.grossAmountText,
                onValueChange = { onFormChange(formState.copy(grossAmountText = filterPlainAmountInput(it))) },
                label = { Text("Gross (Rs)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .weight(1f)
                    .testTag("review_gross_input")
            )
            OutlinedTextField(
                value = formState.deductionsText,
                onValueChange = { onFormChange(formState.copy(deductionsText = filterPlainAmountInput(it))) },
                label = { Text("Deductions (Rs)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .weight(1f)
                    .testTag("review_deductions_input")
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = formState.netAmountText,
                onValueChange = { onFormChange(formState.copy(netAmountText = filterPlainAmountInput(it))) },
                label = { Text("Net amount (Rs)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .weight(1f)
                    .testTag("review_net_input")
            )
            OutlinedTextField(
                value = formState.invoiceAmountText,
                onValueChange = { onFormChange(formState.copy(invoiceAmountText = filterPlainAmountInput(it))) },
                label = { Text("Invoice amount (Rs)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .weight(1f)
                    .testTag("review_invoice_amount_input")
            )
        }
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = formState.notes,
            onValueChange = { onFormChange(formState.copy(notes = it)) },
            label = { Text("Notes (editable)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        // Audit transparency summary
        Surface(
            color = StoneSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "On Confirm, your Passport saves:",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkPrimary
                )
                Text(
                    text = "• Work giver: ${effectiveWorkGiver.ifBlank { "—" }} · Payer: ${effectivePayer.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Text(
                    text = "• Label: Document-extracted · worker_confirmed = true · original AI values preserved in ai_original",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Text(
                    text = "• Audit log entries: Upload, Edit (${liveEdits.size} field change${if (liveEdits.size == 1) "" else "s"}), and Confirm",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onWorkerCheckedChange(!workerChecked) }
                .testTag("review_confirm_checkbox_row"),
            shape = RoundedCornerShape(12.dp),
            color = StoneSurface,
            border = BorderStroke(1.dp, HairlineBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = workerChecked,
                    onCheckedChange = onWorkerCheckedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = InkPrimary,
                        checkmarkColor = OnDarkInk
                    ),
                    modifier = Modifier.testTag("review_confirm_checkbox")
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "I checked every value against the original file and confirm they are correct.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkPrimary
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onConfirm,
            enabled = !isBusy &&
                workerChecked &&
                effectiveWorkGiver.isNotBlank() &&
                effectivePayer.isNotBlank() &&
                confirmedAmount > 0L &&
                isRecordDateValid &&
                isPeriodStartValid &&
                isPeriodEndValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("confirm_and_save_document_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = InkPrimary,
                contentColor = OnDarkInk
            )
        ) {
            Text(
                text = "Confirm & Save (${RupeeFormatter.format(confirmedAmount)})",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDiscard,
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("discard_document_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = CardSurface,
                contentColor = InkSecondary
            )
        ) {
            Text(
                text = if (savedDocumentId.isNullOrBlank()) {
                    "Discard Draft (Nothing Saved)"
                } else {
                    "Discard Pending Draft"
                }
            )
        }
    }
}

@Composable
private fun PendingDocsQueueSection(
    pendingDocs: List<IncomeDocument>,
    activeDocId: String?,
    onSelectPendingDoc: (IncomeDocument) -> Unit
) {
    val others = pendingDocs.filter { it.id != activeDocId }
    if (others.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Other Documents Waiting (${others.size})",
            style = MaterialTheme.typography.titleSmall,
            color = InkPrimary
        )
        others.forEach { doc ->
            PendingDocumentQueueItem(doc = doc, onClick = { onSelectPendingDoc(doc) })
        }
    }
}

@Composable
private fun PendingDocumentQueueItem(
    doc: IncomeDocument,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PassportSectionCard(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = InkPrimary)
            Column(modifier = Modifier.weight(1f)) {
                Text(doc.title, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                Text(
                    "${doc.extractedPartyName} · ${RupeeFormatter.format(doc.extractedAmountRupees)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }
        }
    }
}

private fun filterPlainAmountInput(raw: String): String {
    var seenDot = false
    val sb = StringBuilder()
    for (ch in raw) {
        if (ch.isDigit()) {
            sb.append(ch)
        } else if (ch == '.' && !seenDot) {
            seenDot = true
            sb.append(ch)
        }
    }
    return sb.toString()
}
