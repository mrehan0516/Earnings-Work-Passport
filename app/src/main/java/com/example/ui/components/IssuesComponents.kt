package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.rules.DisputeComparison
import com.example.domain.rules.DisputeVersion
import com.example.domain.rules.IssuesFilter
import com.example.domain.rules.PassportLedgerBuilder
import com.example.domain.rules.RupeeFormatter
import com.example.domain.rules.SuccessfulMatchDetail
import com.example.model.BankCredit
import com.example.model.IncomeRecord
import com.example.ui.theme.CardSurface
import com.example.ui.theme.EvidenceDisputedBg
import com.example.ui.theme.EvidenceDisputedInk
import com.example.ui.theme.EvidenceSourceMatchedBg
import com.example.ui.theme.EvidenceSourceMatchedInk
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.OnDarkInk
import com.example.ui.theme.RupeeAmountCompactStyle
import com.example.ui.theme.StoneSurface
import com.example.ui.theme.StoneVariant
import kotlin.math.abs

/**
 * Top filter chip bar for the Issues screen: All, Needs Action, Disputed, Matched (with counts).
 */
@Composable
fun IssuesFilterChipBar(
    selectedFilter: IssuesFilter,
    counts: Map<IssuesFilter, Int>,
    onSelectFilter: (IssuesFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("issues_filter_chip_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IssuesFilter.entries.forEach { filter ->
            val count = counts[filter] ?: 0
            val selected = filter == selectedFilter
            FilterChip(
                selected = selected,
                onClick = { onSelectFilter(filter) },
                label = {
                    Text(
                        text = "${filter.label} ($count)",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("issues_filter_${filter.name.lowercase()}"),
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = InkPrimary,
                    selectedLabelColor = OnDarkInk,
                    containerColor = CardSurface,
                    labelColor = InkPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = HairlineBorder,
                    selectedBorderColor = InkPrimary
                )
            )
        }
    }
}

/**
 * Side-by-side comparison panel rendered for EVERY disputed item ("numbers do not add up",
 * client corrections, bank mismatches, or worker "This is wrong" disputes).
 * Both versions remain visible and are never deleted.
 */
@Composable
fun DisputeSideBySidePanel(
    comparison: DisputeComparison,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dispute_side_by_side_panel"),
        shape = RoundedCornerShape(12.dp),
        color = StoneSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CompareArrows,
                    contentDescription = null,
                    tint = EvidenceDisputedInk,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Both versions kept side by side",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkPrimary
                )
            }
            Spacer(Modifier.height(10.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val sideBySide = maxWidth >= 280.dp
                if (sideBySide) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DisputeVersionColumn(
                            version = comparison.leftVersion,
                            highlightDisputed = false,
                            modifier = Modifier.weight(1f)
                        )
                        VerticalDivider(
                            color = HairlineBorder,
                            modifier = Modifier.fillMaxHeight()
                        )
                        DisputeVersionColumn(
                            version = comparison.rightVersion,
                            highlightDisputed = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DisputeVersionColumn(
                            version = comparison.leftVersion,
                            highlightDisputed = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                        HorizontalDivider(color = HairlineBorder)
                        DisputeVersionColumn(
                            version = comparison.rightVersion,
                            highlightDisputed = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = comparison.summaryNote,
                style = MaterialTheme.typography.labelSmall,
                color = InkSecondary
            )
        }
    }
}

@Composable
private fun DisputeVersionColumn(
    version: DisputeVersion,
    highlightDisputed: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (highlightDisputed) EvidenceDisputedBg else CardSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = version.title,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlightDisputed) EvidenceDisputedInk else InkSecondary
            )
            Text(
                text = version.amountDisplay,
                style = RupeeAmountCompactStyle.copy(fontWeight = FontWeight.Bold),
                color = if (highlightDisputed) EvidenceDisputedInk else InkPrimary
            )
            version.details.forEach { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkPrimary
                )
            }
        }
    }
}

/**
 * Structured two-record summary panel for confirmed successful matches.
 */
@Composable
fun SuccessfulMatchSummaryPanel(
    detail: SuccessfulMatchDetail,
    modifier: Modifier = Modifier
) {
    val incomeDate = PassportLedgerBuilder.formatShortDayMonth(detail.incomeRecord.earnedDate)
        .ifBlank { detail.incomeRecord.earnedDate }
    val creditDate = PassportLedgerBuilder.formatShortDayMonth(detail.bankCredit.creditDate)
        .ifBlank { detail.bankCredit.creditDate }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("successful_match_panel_${detail.incomeRecord.id}"),
        shape = RoundedCornerShape(12.dp),
        color = EvidenceSourceMatchedBg,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircleOutline,
                    contentDescription = null,
                    tint = EvidenceSourceMatchedInk,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "One payment, counted once · ${detail.matchRule}",
                    style = MaterialTheme.typography.labelMedium,
                    color = EvidenceSourceMatchedInk
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("Income Record", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                        Text(
                            text = detail.incomeRecord.partyName,
                            style = MaterialTheme.typography.titleSmall,
                            color = InkPrimary
                        )
                        Text(
                            text = "${RupeeFormatter.format(detail.incomeRecord.amountRupees)} · $incomeDate",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("Bank Credit", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                        Text(
                            text = detail.bankCredit.narration.ifBlank { detail.bankCredit.bankLabel },
                            style = MaterialTheme.typography.titleSmall,
                            color = InkPrimary
                        )
                        Text(
                            text = "${RupeeFormatter.format(detail.bankCredit.amountRupees)} · $creditDate",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Material 3 ModalBottomSheet for "This is wrong" (note -> disputed).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ThisIsWrongBottomSheet(
    record: IncomeRecord,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmitNote: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var noteText by remember(record.id) { mutableStateOf(record.conflictReason) }
    var showError by remember { mutableStateOf(false) }

    val quickNotes = listOf(
        "Amount does not match what I earned",
        "Wrong bank credit linked",
        "Deductions look incorrect",
        "Client or payer details are wrong"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardSurface,
        contentColor = InkPrimary,
        modifier = Modifier.testTag("this_is_wrong_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ReportProblem,
                    contentDescription = null,
                    tint = EvidenceDisputedInk,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "This is wrong — flag as Disputed",
                    style = MaterialTheme.typography.headlineSmall,
                    color = InkPrimary
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = StoneSurface,
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${record.partyName} · ${RupeeFormatter.format(record.amountRupees)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = InkPrimary
                    )
                    Text(
                        text = "Both the original entry and your note will stay side by side and will never be deleted. This change is written to your audit log.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickNotes.forEach { preset ->
                    Surface(
                        modifier = Modifier.clickable {
                            noteText = preset
                            showError = false
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = StoneVariant,
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.labelSmall,
                            color = InkPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = {
                    noteText = it
                    if (showError && it.isNotBlank()) showError = false
                },
                label = { Text("What is wrong? (required note)") },
                placeholder = { Text("Explain the difference so reviewers see both versions") },
                isError = showError,
                supportingText = if (showError) {
                    { Text("Please add a short note explaining why this entry is disputed.") }
                } else null,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("this_is_wrong_note_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardSurface,
                        contentColor = InkSecondary
                    )
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val trimmed = noteText.trim()
                        if (trimmed.isBlank()) {
                            showError = true
                        } else {
                            onSubmitNote(trimmed)
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp)
                        .testTag("this_is_wrong_submit_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EvidenceDisputedInk,
                        contentColor = OnDarkInk
                    )
                ) {
                    Text("Mark Disputed", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Material 3 ModalBottomSheet for "Work payment (choose which income)".
 * Lists existing unmatched primary income records PLUS an inline "Create new income" option.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkPaymentPickerBottomSheet(
    credit: BankCredit,
    unmatchedIncomes: List<IncomeRecord>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSelectExistingIncome: (IncomeRecord) -> Unit,
    onCreateNewIncome: (partyName: String, earnedDate: String, periodLabel: String, notes: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCreateForm by remember(credit.id) { mutableStateOf(unmatchedIncomes.isEmpty()) }
    var newPartyName by remember(credit.id) {
        mutableStateOf(credit.narration.ifBlank { credit.bankLabel })
    }
    var newEarnedDate by remember(credit.id) {
        mutableStateOf(credit.creditDate.ifBlank { "2026-11-15" })
    }
    var newPeriodLabel by remember(credit.id) { mutableStateOf("Work payment") }
    var newNotes by remember(credit.id) { mutableStateOf("") }
    var partyError by remember { mutableStateOf(false) }

    val creditDateShort = PassportLedgerBuilder.formatShortDayMonth(credit.creditDate)
        .ifBlank { credit.creditDate }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardSurface,
        contentColor = InkPrimary,
        modifier = Modifier.testTag("work_payment_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalance,
                    contentDescription = null,
                    tint = InkPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Work payment — choose income",
                    style = MaterialTheme.typography.headlineSmall,
                    color = InkPrimary
                )
            }

            // Bank credit summary header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = StoneSurface,
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = credit.narration.ifBlank { credit.bankLabel },
                            style = MaterialTheme.typography.titleSmall,
                            color = InkPrimary
                        )
                        Text(
                            text = listOf(creditDateShort, credit.bankLabel)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = RupeeFormatter.format(credit.amountRupees),
                        style = RupeeAmountCompactStyle.copy(fontWeight = FontWeight.Bold),
                        color = InkPrimary
                    )
                }
            }

            // Section 1: Existing unmatched primary incomes
            Text(
                text = "Link to an existing unmatched income (${unmatchedIncomes.size})",
                style = MaterialTheme.typography.titleSmall,
                color = InkPrimary
            )

            if (unmatchedIncomes.isEmpty()) {
                Text(
                    text = "No unmatched income records available right now. Create a new work income below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    unmatchedIncomes.forEach { income ->
                        val delta = credit.amountRupees - income.amountRupees
                        val deltaNote = when {
                            abs(delta) <= 1L -> "Exact amount match"
                            else -> "Amount differs by ${RupeeFormatter.formatSignedDelta(delta)} (flags mismatch)"
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isBusy) { onSelectExistingIncome(income) }
                                .testTag("pick_existing_income_${income.id}"),
                            shape = RoundedCornerShape(12.dp),
                            color = CardSurface,
                            border = BorderStroke(1.dp, HairlineBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = income.partyName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = InkPrimary
                                    )
                                    Text(
                                        text = "${income.periodLabel.ifBlank { income.earnedDate }} · $deltaNote",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkSecondary
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = RupeeFormatter.format(income.amountRupees),
                                    style = RupeeAmountCompactStyle.copy(fontWeight = FontWeight.Bold),
                                    color = InkPrimary
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = HairlineBorder)

            // Section 2: Create new income inline option
            if (!showCreateForm) {
                OutlinedButton(
                    onClick = { showCreateForm = true },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("picker_show_create_new_income_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, InkPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardSurface,
                        contentColor = InkPrimary
                    )
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Create new income for this credit", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Create new work income (${RupeeFormatter.format(credit.amountRupees)})",
                        style = MaterialTheme.typography.titleSmall,
                        color = InkPrimary
                    )
                    OutlinedTextField(
                        value = newPartyName,
                        onValueChange = {
                            newPartyName = it
                            if (partyError && it.isNotBlank()) partyError = false
                        },
                        label = { Text("Work source / client name") },
                        isError = partyError,
                        supportingText = if (partyError) {
                            { Text("Enter who paid you for this work.") }
                        } else null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("picker_new_income_party_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newEarnedDate,
                            onValueChange = { newEarnedDate = it },
                            label = { Text("Earned date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newPeriodLabel,
                            onValueChange = { newPeriodLabel = it },
                            label = { Text("Period / job") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = newNotes,
                        onValueChange = { newNotes = it },
                        label = { Text("Optional note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val trimmedParty = newPartyName.trim()
                            if (trimmedParty.isBlank()) {
                                partyError = true
                            } else {
                                onCreateNewIncome(
                                    trimmedParty,
                                    newEarnedDate.trim(),
                                    newPeriodLabel.trim(),
                                    newNotes.trim()
                                )
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("picker_create_and_link_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InkPrimary,
                            contentColor = OnDarkInk
                        )
                    ) {
                        Text("Create & Link Work Payment", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
