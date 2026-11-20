package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.rules.IssueType
import com.example.domain.rules.IssuesFilter
import com.example.domain.rules.MatchCandidate
import com.example.domain.rules.PassportIssue
import com.example.domain.rules.RupeeFormatter
import com.example.model.BankCredit
import com.example.model.ConfirmationRequest
import com.example.model.EvidenceStatus
import com.example.model.IncomeDocument
import com.example.model.IncomeRecord
import com.example.model.ReconciliationDecision
import com.example.reconciliation.BankCreditClassification
import com.example.ui.components.CounterpartyConfirmationPanel
import com.example.ui.components.DisputeSideBySidePanel
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.EvidenceBadge
import com.example.ui.components.IssuesFilterChipBar
import com.example.ui.components.NeutralSmallChip
import com.example.ui.components.PassportSectionCard
import com.example.ui.components.SuccessfulMatchSummaryPanel
import com.example.ui.components.ThisIsWrongBottomSheet
import com.example.ui.components.WorkPaymentPickerBottomSheet
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.OnDarkInk
import com.example.ui.theme.RupeeAmountCompactStyle
import com.example.ui.theme.StoneSurface
import com.example.viewmodel.PassportWorkspaceData

@Composable
fun IssuesScreen(
    workspace: PassportWorkspaceData,
    isBusy: Boolean,
    onOpenDocumentReview: (IncomeDocument) -> Unit,
    onConfirmMatch: (MatchCandidate) -> Unit,
    onRejectMatch: (MatchCandidate) -> Unit,
    onMarkDisputed: (IncomeRecord, String) -> Unit,
    onClearDispute: (IncomeRecord, Boolean) -> Unit,
    onIgnoreDuplicate: (IncomeRecord) -> Unit,
    onRequestConfirmation: (IncomeRecord) -> Unit,
    onOpenPublicConfirmation: (String) -> Unit = {},
    onAcceptClientCorrection: (ConfirmationRequest, IncomeRecord, Long) -> Unit = { _, _, _ -> },
    onDisputeClientResponse: (ConfirmationRequest, IncomeRecord, String) -> Unit = { _, _, _ -> },
    onLinkCreditToIncome: (BankCredit, IncomeRecord) -> Unit,
    onCreateIncomeForCredit: (BankCredit, String, String, String, String) -> Unit,
    onMarkCreditNotIncome: (BankCredit) -> Unit,
    onNavigateToAddBankCredit: () -> Unit
) {
    var selectedFilterName by rememberSaveable { mutableStateOf(IssuesFilter.ALL.name) }
    val selectedFilter = remember(selectedFilterName) {
        IssuesFilter.entries.firstOrNull { it.name == selectedFilterName } ?: IssuesFilter.ALL
    }

    var disputeTargetRecord by remember { mutableStateOf<IncomeRecord?>(null) }
    var workPaymentCredit by remember { mutableStateOf<BankCredit?>(null) }

    val confirmedMatchedRecordIds = remember(workspace.reconciliations) {
        workspace.reconciliations
            .filter { ReconciliationDecision.fromCode(it.decisionStatus) == ReconciliationDecision.CONFIRMED }
            .map { it.incomeRecordId }
            .toSet()
    }

    val allItems = workspace.allIssueItems
    val filterCounts = remember(allItems) {
        IssuesFilter.entries.associateWith { filter -> allItems.count { it.matchesFilter(filter) } }
    }
    val visibleItems = remember(allItems, selectedFilter) {
        allItems.filter { it.matchesFilter(selectedFilter) }
    }

    val unmatchedPrimaryIncomes = remember(workspace.incomeRecords, confirmedMatchedRecordIds) {
        workspace.incomeRecords.filter { rec ->
            !rec.isSecondaryDuplicate() &&
                rec.id !in confirmedMatchedRecordIds &&
                rec.resolvedEvidence() != EvidenceStatus.SOURCE_MATCHED
        }
    }

    val maxSectionWidth = 880.dp

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("issues_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = maxSectionWidth)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Issues & Rule Matches",
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkPrimary
                )
                Text(
                    text = "Review duplicates, math checks, mismatches, unclassified credits, choices needed, and successful matches. Every decision is logged; disputed entries stay side by side and are never deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
            }
        }

        item {
            IssuesFilterChipBar(
                selectedFilter = selectedFilter,
                counts = filterCounts,
                onSelectFilter = { selectedFilterName = it.name },
                modifier = Modifier.widthIn(max = maxSectionWidth)
            )
        }

        if (visibleItems.isEmpty()) {
            item {
                val (emptyTitle, emptyBody) = when (selectedFilter) {
                    IssuesFilter.ALL -> "No issues or matches yet" to "Add a statement, invoice, or bank CSV to see deterministic rule checks and matches here."
                    IssuesFilter.NEEDS_ACTION -> "Nothing needs action right now" to "All open duplicates, mismatches, choices, and unclassified credits are resolved."
                    IssuesFilter.DISPUTED -> "No disputed items" to "Items with math errors, client amount differences, or 'This is wrong' notes appear here side by side."
                    IssuesFilter.MATCHED -> "No successful matches yet" to "Once an income record matches an independent bank credit, the pair appears here as one counted payment."
                }
                EmptyStateCard(
                    title = emptyTitle,
                    body = emptyBody,
                    icon = Icons.Outlined.CheckCircleOutline,
                    modifier = Modifier.widthIn(max = maxSectionWidth)
                )
            }
        } else {
            items(visibleItems, key = { it.id }) { issue ->
                IssueCard(
                    issue = issue,
                    workspace = workspace,
                    confirmedMatchedRecordIds = confirmedMatchedRecordIds,
                    isBusy = isBusy,
                    onOpenDocumentReview = onOpenDocumentReview,
                    onConfirmMatch = onConfirmMatch,
                    onRejectMatch = onRejectMatch,
                    onOpenThisIsWrong = { rec -> disputeTargetRecord = rec },
                    onClearDispute = onClearDispute,
                    onIgnoreDuplicate = onIgnoreDuplicate,
                    onRequestConfirmation = onRequestConfirmation,
                    onOpenPublicConfirmation = onOpenPublicConfirmation,
                    onAcceptClientCorrection = onAcceptClientCorrection,
                    onDisputeClientResponse = onDisputeClientResponse,
                    onOpenWorkPaymentPicker = { credit -> workPaymentCredit = credit },
                    onMarkCreditNotIncome = onMarkCreditNotIncome,
                    onNavigateToAddBankCredit = onNavigateToAddBankCredit,
                    modifier = Modifier.widthIn(max = maxSectionWidth)
                )
            }
        }
    }

    // "This is wrong" (note -> disputed) bottom sheet
    disputeTargetRecord?.let { rec ->
        ThisIsWrongBottomSheet(
            record = rec,
            isBusy = isBusy,
            onDismiss = { disputeTargetRecord = null },
            onSubmitNote = { note ->
                onMarkDisputed(rec, note)
                disputeTargetRecord = null
            }
        )
    }

    // "Work payment (choose which income)" picker bottom sheet
    workPaymentCredit?.let { credit ->
        WorkPaymentPickerBottomSheet(
            credit = credit,
            unmatchedIncomes = unmatchedPrimaryIncomes,
            isBusy = isBusy,
            onDismiss = { workPaymentCredit = null },
            onSelectExistingIncome = { chosenIncome ->
                onLinkCreditToIncome(credit, chosenIncome)
                workPaymentCredit = null
            },
            onCreateNewIncome = { partyName, earnedDate, periodLabel, notes ->
                onCreateIncomeForCredit(credit, partyName, earnedDate, periodLabel, notes)
                workPaymentCredit = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IssueCard(
    issue: PassportIssue,
    workspace: PassportWorkspaceData,
    confirmedMatchedRecordIds: Set<String>,
    isBusy: Boolean,
    onOpenDocumentReview: (IncomeDocument) -> Unit,
    onConfirmMatch: (MatchCandidate) -> Unit,
    onRejectMatch: (MatchCandidate) -> Unit,
    onOpenThisIsWrong: (IncomeRecord) -> Unit,
    onClearDispute: (IncomeRecord, Boolean) -> Unit,
    onIgnoreDuplicate: (IncomeRecord) -> Unit,
    onRequestConfirmation: (IncomeRecord) -> Unit,
    onOpenPublicConfirmation: (String) -> Unit,
    onAcceptClientCorrection: (ConfirmationRequest, IncomeRecord, Long) -> Unit,
    onDisputeClientResponse: (ConfirmationRequest, IncomeRecord, String) -> Unit,
    onOpenWorkPaymentPicker: (BankCredit) -> Unit,
    onMarkCreditNotIncome: (BankCredit) -> Unit,
    onNavigateToAddBankCredit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val linkedRecord = workspace.incomeRecords.firstOrNull { it.id == issue.incomeRecordId }
    val latestConfirmation = linkedRecord?.let { rec ->
        workspace.confirmationRequests.firstOrNull { it.incomeRecordId == rec.id }
    }

    PassportSectionCard(modifier = modifier.testTag("issue_card_${issue.id}")) {
        // Category tag + rupee amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = StoneSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Text(
                    text = issue.type.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text(
                text = RupeeFormatter.format(issue.amountRupees),
                style = RupeeAmountCompactStyle.copy(fontWeight = FontWeight.Bold),
                color = InkPrimary
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = issue.headline,
            style = MaterialTheme.typography.titleMedium,
            color = InkPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = issue.explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = InkSecondary
        )
        Spacer(Modifier.height(10.dp))

        // Evidence badge + neutral metadata chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (issue.type == IssueType.UNMATCHED_BANK_CREDIT) {
                val clsLabel = issue.creditClassification?.label ?: BankCreditClassification.UNCLASSIFIED.label
                NeutralSmallChip(
                    label = clsLabel,
                    modifier = Modifier.testTag("issue_credit_class_${issue.bankCreditId}")
                )
                NeutralSmallChip(label = "Not counted in work income")
            } else {
                EvidenceBadge(status = issue.evidenceStatus)
                if (linkedRecord?.simulatedApi == true) {
                    NeutralSmallChip(
                        label = "Simulated API",
                        modifier = Modifier.testTag("issue_simulated_api_${issue.id}")
                    )
                }
                if (issue.successfulMatch != null && issue.successfulMatch.linkedFileCount > 1) {
                    NeutralSmallChip(label = "${issue.successfulMatch.linkedFileCount} files linked")
                }
            }
        }

        // Side-by-side comparison for all disputed items ("Numbers do not add up" + Mismatches/Disputes)
        issue.disputeComparison?.let { comparison ->
            Spacer(Modifier.height(12.dp))
            DisputeSideBySidePanel(comparison = comparison)
        }

        // Two-record summary panel for successful matches
        issue.successfulMatch?.let { matchDetail ->
            Spacer(Modifier.height(12.dp))
            SuccessfulMatchSummaryPanel(detail = matchDetail)
        }

        // Counterparty confirmation status, link copy/preview, and worker resolution actions
        if (linkedRecord != null &&
            (latestConfirmation != null ||
                issue.type == IssueType.DISPUTED_RECORD ||
                issue.type == IssueType.UNMATCHED_INCOME)
        ) {
            Spacer(Modifier.height(12.dp))
            CounterpartyConfirmationPanel(
                record = linkedRecord,
                latestRequest = latestConfirmation,
                isBusy = isBusy,
                onRequestConfirmation = onRequestConfirmation,
                onOpenPublicConfirmation = onOpenPublicConfirmation,
                onAcceptClientCorrection = onAcceptClientCorrection,
                onDisputeClientResponse = onDisputeClientResponse
            )
        }

        Spacer(Modifier.height(12.dp))

        when (issue.type) {
            IssueType.PENDING_AI_REVIEW -> {
                val doc = workspace.documents.firstOrNull { it.id == issue.documentId }
                if (doc != null) {
                    Button(
                        onClick = { onOpenDocumentReview(doc) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("issue_review_doc_${doc.id}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = OnDarkInk)
                    ) {
                        Text("Review & Confirm Values")
                    }
                }
            }

            IssueType.NUMBERS_DO_NOT_ADD_UP, IssueType.DISPUTED_RECORD -> {
                // Disputed items are NEVER deleted; offer Clear Dispute or update dispute note
                if (linkedRecord != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onClearDispute(linkedRecord, linkedRecord.id in confirmedMatchedRecordIds) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("issue_clear_dispute_${linkedRecord.id}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = OnDarkInk)
                        ) {
                            Text("Resolve & Clear")
                        }
                        OutlinedButton(
                            onClick = { onOpenThisIsWrong(linkedRecord) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("issue_edit_dispute_note_${linkedRecord.id}"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardSurface,
                                contentColor = InkPrimary
                            )
                        ) {
                            Text("Edit Note")
                        }
                    }
                }
            }

            IssueType.SUGGESTED_BANK_MATCH, IssueType.AMOUNT_MISMATCH -> {
                val candidates = issue.multipleCandidates.ifEmpty { listOfNotNull(issue.candidate) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    candidates.forEachIndexed { idx, cand ->
                        if (idx > 0) HorizontalDivider(color = HairlineBorder)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (candidates.size > 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cand.bankCredit.narration.ifBlank { cand.bankCredit.bankLabel },
                                            style = MaterialTheme.typography.titleSmall,
                                            color = InkPrimary
                                        )
                                        Text(
                                            text = "${cand.bankCredit.creditDate} · ${cand.matchRule}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = InkSecondary
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = RupeeFormatter.format(cand.bankCredit.amountRupees),
                                        style = RupeeAmountCompactStyle,
                                        color = InkPrimary
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onConfirmMatch(cand) },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(48.dp)
                                        .testTag("issue_accept_match_${cand.bankCredit.id}"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = OnDarkInk)
                                ) {
                                    Text("Accept suggested match")
                                }
                                OutlinedButton(
                                    onClick = { onRejectMatch(cand) },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .height(48.dp)
                                        .testTag("issue_reject_match_${cand.bankCredit.id}"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = CardSurface,
                                        contentColor = InkSecondary
                                    )
                                ) {
                                    Text("Not a match")
                                }
                            }
                        }
                    }
                    if (linkedRecord != null) {
                        OutlinedButton(
                            onClick = { onOpenThisIsWrong(linkedRecord) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("issue_this_is_wrong_${linkedRecord.id}"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardSurface,
                                contentColor = InkSecondary
                            )
                        ) {
                            Text("This is wrong")
                        }
                    }
                }
            }

            IssueType.POTENTIAL_DUPLICATE -> {
                if (linkedRecord != null) {
                    OutlinedButton(
                        onClick = { onIgnoreDuplicate(linkedRecord) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("issue_ignore_duplicate_${linkedRecord.id}"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, InkPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardSurface,
                            contentColor = InkPrimary
                        )
                    ) {
                        Text("Ignore duplicate")
                    }
                }
            }

            IssueType.UNMATCHED_BANK_CREDIT -> {
                val credit = workspace.bankCredits.firstOrNull { it.id == issue.bankCreditId }
                if (credit != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onOpenWorkPaymentPicker(credit) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("issue_classify_work_payment_${credit.id}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = OnDarkInk)
                        ) {
                            Text("Work payment (choose which income)")
                        }
                        OutlinedButton(
                            onClick = { onMarkCreditNotIncome(credit) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("issue_classify_not_income_${credit.id}"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, InkPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardSurface,
                                contentColor = InkPrimary
                            )
                        ) {
                            Text("Not income (e.g. family transfer)")
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onNavigateToAddBankCredit,
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, InkPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardSurface,
                            contentColor = InkPrimary
                        )
                    ) {
                        Text("Add Matching Income Record")
                    }
                }
            }

            IssueType.UNMATCHED_INCOME -> {
                if (linkedRecord != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onRequestConfirmation(linkedRecord) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("issue_ask_party_${linkedRecord.id}"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, InkPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardSurface,
                                contentColor = InkPrimary
                            )
                        ) {
                            Text("Ask Party")
                        }
                        OutlinedButton(
                            onClick = { onOpenThisIsWrong(linkedRecord) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("issue_this_is_wrong_${linkedRecord.id}"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardSurface,
                                contentColor = InkSecondary
                            )
                        ) {
                            Text("This is wrong")
                        }
                    }
                }
            }

            IssueType.SUCCESSFUL_MATCH -> {
                if (linkedRecord != null) {
                    OutlinedButton(
                        onClick = { onOpenThisIsWrong(linkedRecord) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("issue_this_is_wrong_${linkedRecord.id}"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardSurface,
                            contentColor = InkSecondary
                        )
                    ) {
                        Text("This is wrong")
                    }
                }
            }
        }
    }
}
