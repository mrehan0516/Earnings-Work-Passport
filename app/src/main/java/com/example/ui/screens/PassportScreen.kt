package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.example.domain.rules.EvidenceLedgerRow
import com.example.domain.rules.PassportLedgerBuilder
import com.example.domain.rules.RupeeFormatter
import com.example.model.ConfirmationRequest
import com.example.model.IncomeRecord
import com.example.ui.components.CounterpartyConfirmationPanel
import com.example.ui.components.EvidenceBadge
import com.example.ui.components.EvidenceLedgerCard
import com.example.ui.components.EvidenceSummaryCard
import com.example.ui.components.FriendlyLedgerEmptyState
import com.example.ui.components.LabelsLegendCard
import com.example.ui.components.MonthlyReceivedBarChartCard
import com.example.ui.components.NotALenderNotice
import com.example.ui.components.PassportSectionCard
import com.example.ui.components.WhyExplanationBottomSheet
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.OnDarkInk
import com.example.ui.theme.RupeeAmountCardStyle
import com.example.ui.theme.StoneSurface
import com.example.viewmodel.PassportWorkspaceData

@Composable
fun PassportScreen(
    workspace: PassportWorkspaceData,
    isBusy: Boolean = false,
    onNavigateToAdd: () -> Unit,
    onNavigateToIssues: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToParties: () -> Unit,
    onNavigateToReviewerView: () -> Unit,
    onRequestConfirmation: (IncomeRecord) -> Unit = {},
    onOpenPublicConfirmation: (String) -> Unit = {},
    onAcceptClientCorrection: (ConfirmationRequest, IncomeRecord, Long) -> Unit = { _, _, _ -> },
    onDisputeClientResponse: (ConfirmationRequest, IncomeRecord, String) -> Unit = { _, _, _ -> },
    onLoadAshaDemo: (() -> Unit)? = null
) {
    val worker = workspace.activeWorker
    val summary = workspace.summary

    val ledgerModel = remember(
        workspace.incomeRecords,
        workspace.bankCredits,
        workspace.relationships,
        workspace.reconciliations,
        workspace.confirmationRequests,
        workspace.reconciliationResult
    ) {
        PassportLedgerBuilder.build(
            incomeRecords = workspace.incomeRecords,
            bankCredits = workspace.bankCredits,
            relationships = workspace.relationships,
            reconciliations = workspace.reconciliations,
            confirmations = workspace.confirmationRequests,
            reconciliationResult = workspace.reconciliationResult
        )
    }

    var legendExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedWhyRow by remember { mutableStateOf<EvidenceLedgerRow?>(null) }

    val maxSectionWidth = 920.dp

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("passport_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Worker Identity Header (no undifferentiated combined monthly income number)
        item {
            PassportSectionCard(modifier = Modifier.widthIn(max = maxSectionWidth)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = worker?.displayName ?: "Worker Passport",
                            style = MaterialTheme.typography.headlineMedium,
                            color = InkPrimary
                        )
                        val subtitle = listOfNotNull(
                            worker?.city?.takeIf { it.isNotBlank() },
                            worker?.primaryWorkType?.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                        if (subtitle.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSecondary
                            )
                        }
                    }
                    if (worker?.demoProfile == true) {
                        Surface(
                            color = StoneSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, HairlineBorder)
                        ) {
                            Text(
                                text = "Synthetic Demo",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        if (!ledgerModel.hasAnyActivity) {
            // Friendly empty state when no earnings or bank credits exist yet
            item {
                FriendlyLedgerEmptyState(
                    onAddFirstRecord = onNavigateToAdd,
                    onLoadAshaDemo = onLoadAshaDemo,
                    modifier = Modifier.widthIn(max = maxSectionWidth)
                )
            }

            // Collapsible "What do these labels mean?" legend is still helpful on an empty passport
            item {
                LabelsLegendCard(
                    expanded = legendExpanded,
                    onToggle = { legendExpanded = !legendExpanded },
                    modifier = Modifier.widthIn(max = maxSectionWidth)
                )
            }
        } else {
            // 2. Four defined summary lines above the Evidence Ledger
            item {
                EvidenceSummaryCard(
                    summary = ledgerModel.summary,
                    onOpenIssues = onNavigateToIssues,
                    modifier = Modifier.widthIn(max = maxSectionWidth)
                )
            }

            // 3. Monthly bar chart of received (bank-matched) amounts
            item {
                MonthlyReceivedBarChartCard(
                    bars = ledgerModel.monthlyBars,
                    modifier = Modifier.widthIn(max = maxSectionWidth)
                )
            }

            // 4. Actionable alerts banner (Pending AI confirmations or Open Issues)
            if (workspace.pendingDocuments.isNotEmpty() || workspace.issues.isNotEmpty()) {
                item {
                    PassportSectionCard(modifier = Modifier.widthIn(max = maxSectionWidth)) {
                        Text(
                            text = "Needs your decision",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkPrimary
                        )
                        Spacer(Modifier.height(10.dp))
                        if (workspace.pendingDocuments.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onNavigateToReview)
                                    .testTag("passport_pending_docs_banner"),
                                shape = RoundedCornerShape(12.dp),
                                color = StoneSurface,
                                border = BorderStroke(1.dp, HairlineBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Description,
                                            contentDescription = null,
                                            tint = InkPrimary
                                        )
                                        Column {
                                            Text(
                                                text = "${workspace.pendingDocuments.size} document(s) awaiting confirmation",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = InkPrimary
                                            )
                                            Text(
                                                text = "AI read candidate values. Review & confirm each field.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = InkSecondary
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                        contentDescription = "Open Review and Confirm",
                                        tint = InkSecondary
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (workspace.issues.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onNavigateToIssues)
                                    .testTag("passport_open_issues_banner"),
                                shape = RoundedCornerShape(12.dp),
                                color = StoneSurface,
                                border = BorderStroke(1.dp, HairlineBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.FactCheck,
                                            contentDescription = null,
                                            tint = InkPrimary
                                        )
                                        Column {
                                            Text(
                                                text = "${workspace.issues.size} open issue(s) & rule matches",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = InkPrimary
                                            )
                                            Text(
                                                text = if (workspace.matchCandidates.isNotEmpty()) {
                                                    "${workspace.matchCandidates.size} bank credit match suggestion(s) ready."
                                                } else {
                                                    "Inspect unclassified credits, duplicates, amount gaps, and disputes."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = InkSecondary
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                        contentDescription = "Open Issues",
                                        tint = InkSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Quick Actions Row
            item {
                Row(
                    modifier = Modifier
                        .widthIn(max = maxSectionWidth)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onNavigateToAdd,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("passport_add_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InkPrimary,
                            contentColor = OnDarkInk
                        )
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Income", style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = onNavigateToReviewerView,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("passport_reviewer_preview_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, InkPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardSurface,
                            contentColor = InkPrimary
                        )
                    ) {
                        Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reviewer View", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // 6. Collapsible "What do these labels mean?" Legend
            item {
                LabelsLegendCard(
                    expanded = legendExpanded,
                    onToggle = { legendExpanded = !legendExpanded },
                    modifier = Modifier.widthIn(max = maxSectionWidth)
                )
            }

            // 7. Evidence Ledger Section Header
            item {
                Row(
                    modifier = Modifier
                        .widthIn(max = maxSectionWidth)
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Evidence Ledger (${ledgerModel.rows.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkPrimary
                    )
                    Text(
                        text = "${workspace.bankCredits.size} bank credits",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
            }

            // 8. Adaptive 6-Column Evidence Ledger (Table on wide screens, structured rows on phones)
            if (ledgerModel.rows.isEmpty()) {
                item {
                    FriendlyLedgerEmptyState(
                        onAddFirstRecord = onNavigateToAdd,
                        onLoadAshaDemo = onLoadAshaDemo,
                        modifier = Modifier.widthIn(max = maxSectionWidth)
                    )
                }
            } else {
                item {
                    EvidenceLedgerCard(
                        rows = ledgerModel.rows,
                        onWhyClick = { row -> selectedWhyRow = row },
                        modifier = Modifier.widthIn(max = maxSectionWidth)
                    )
                }
            }
        }

        // 9. Work sources preview
        item {
            PassportSectionCard(modifier = Modifier.widthIn(max = maxSectionWidth)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.Groups, contentDescription = null, tint = InkPrimary)
                        Text(
                            text = "Work Sources (${workspace.parties.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkPrimary
                        )
                    }
                    Text(
                        text = "Manage",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkPrimary,
                        modifier = Modifier
                            .clickable(onClick = onNavigateToParties)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("passport_manage_parties_link")
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (summary.partySummaries.isEmpty()) {
                    Text(
                        text = "No platforms or clients added yet. Tap Add Income to register who pays you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                } else {
                    summary.partySummaries.take(4).forEachIndexed { index, ps ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = HairlineBorder,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = ps.party.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InkPrimary
                                )
                                Text(
                                    text = ps.relationship?.roleTitle ?: ps.party.partyType,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSecondary
                                )
                                EvidenceBadge(status = ps.highestEvidence)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = RupeeFormatter.format(ps.totalRupees),
                                style = RupeeAmountCardStyle,
                                color = InkPrimary
                            )
                        }
                    }
                }
            }
        }

        // 10. Not-a-lender notice
        item {
            NotALenderNotice(
                compact = true,
                modifier = Modifier
                    .widthIn(max = maxSectionWidth)
                    .padding(top = 4.dp, bottom = 12.dp)
            )
        }
    }

    // Bottom sheet for plain-language "Why?" explanation + Client Confirmation actions
    selectedWhyRow?.let { activeRow ->
        val liveRow = ledgerModel.rows.firstOrNull { it.recordId == activeRow.recordId } ?: activeRow
        val activeRecord = workspace.incomeRecords.firstOrNull { it.id == liveRow.recordId }
        val latestConfirmation = workspace.confirmationRequests.firstOrNull { it.incomeRecordId == liveRow.recordId }

        WhyExplanationBottomSheet(
            row = liveRow,
            onDismiss = { selectedWhyRow = null },
            confirmationSection = if (activeRecord != null) {
                {
                    CounterpartyConfirmationPanel(
                        record = activeRecord,
                        latestRequest = latestConfirmation,
                        isBusy = isBusy,
                        onRequestConfirmation = onRequestConfirmation,
                        onOpenPublicConfirmation = { token ->
                            selectedWhyRow = null
                            onOpenPublicConfirmation(token)
                        },
                        onAcceptClientCorrection = onAcceptClientCorrection,
                        onDisputeClientResponse = onDisputeClientResponse
                    )
                }
            } else null
        )
    }
}
