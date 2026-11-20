package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.rules.EvidenceLedgerRow
import com.example.domain.rules.LedgerSummaryMetrics
import com.example.domain.rules.MonthlyReceivedBar
import com.example.domain.rules.PassportLedgerBuilder
import com.example.domain.rules.RupeeFormatter
import com.example.model.EvidenceStatus
import com.example.ui.theme.CardSurface
import com.example.ui.theme.EvidenceSourceMatchedBg
import com.example.ui.theme.EvidenceSourceMatchedInk
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.OnDarkInk
import com.example.ui.theme.RupeeAmountCardStyle
import com.example.ui.theme.RupeeAmountCompactStyle
import com.example.ui.theme.StoneSurface
import com.example.ui.theme.StoneVariant

/**
 * Four explicitly defined summary lines shown above the Evidence Ledger.
 * Never combines all earnings into one undifferentiated "monthly income" number.
 */
@Composable
fun EvidenceSummaryCard(
    summary: LedgerSummaryMetrics,
    onOpenIssues: () -> Unit,
    modifier: Modifier = Modifier
) {
    PassportSectionCard(modifier = modifier.testTag("passport_evidence_summary_card")) {
        Text(
            text = "Evidence Summary",
            style = MaterialTheme.typography.titleMedium,
            color = InkPrimary
        )
        Text(
            text = "Each figure is separated by its evidence definition — never merged into one undifferentiated income number.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary
        )
        Spacer(Modifier.height(12.dp))

        // 1. Received and bank-matched
        SummaryDefinitionRow(
            title = "Received and bank-matched",
            subtitle = "${summary.receivedBankMatchedCount} ${if (summary.receivedBankMatchedCount == 1) "payment" else "payments"} matched to bank credits (counted once)",
            amountRupees = summary.receivedBankMatchedRupees,
            emphasize = true,
            modifier = Modifier.testTag("summary_received_bank_matched")
        )
        HorizontalDivider(color = HairlineBorder, modifier = Modifier.padding(vertical = 8.dp))

        // 2. Claimed but unpaid or disputed
        val claimedDetail = buildString {
            val parts = mutableListOf<String>()
            if (summary.claimedUnpaidCount > 0) {
                parts.add("${summary.claimedUnpaidCount} unpaid (${RupeeFormatter.format(summary.claimedUnpaidRupees)})")
            }
            if (summary.disputedCount > 0) {
                parts.add("${summary.disputedCount} disputed (${RupeeFormatter.format(summary.disputedRupees)})")
            }
            if (parts.isEmpty()) {
                append("No pending statements, invoices, or disputes")
            } else {
                append(parts.joinToString(" · "))
            }
        }
        SummaryDefinitionRow(
            title = "Claimed but unpaid or disputed",
            subtitle = claimedDetail,
            amountRupees = summary.claimedUnpaidOrDisputedRupees,
            emphasize = false,
            modifier = Modifier.testTag("summary_claimed_unpaid_disputed")
        )
        HorizontalDivider(color = HairlineBorder, modifier = Modifier.padding(vertical = 8.dp))

        // 3. Self-declared
        SummaryDefinitionRow(
            title = "Self-declared",
            subtitle = "${summary.selfDeclaredCount} manual ${if (summary.selfDeclaredCount == 1) "entry" else "entries"} without document or bank match yet",
            amountRupees = summary.selfDeclaredRupees,
            emphasize = false,
            modifier = Modifier.testTag("summary_self_declared")
        )
        HorizontalDivider(color = HairlineBorder, modifier = Modifier.padding(vertical = 8.dp))

        // 4. Not counted (unclassified credits)
        val unclassifiedClickable = if (summary.notCountedUnclassifiedCreditsCount > 0) {
            Modifier.clickable(onClick = onOpenIssues)
        } else {
            Modifier
        }
        SummaryDefinitionRow(
            title = "Not counted (unclassified credits)",
            subtitle = if (summary.notCountedUnclassifiedCreditsCount > 0) {
                "${summary.notCountedUnclassifiedCreditsCount} unclassified / possible personal transfer · Tap to classify in Issues"
            } else {
                "No unclassified bank credits waiting for review"
            },
            amountRupees = summary.notCountedUnclassifiedCreditsRupees,
            emphasize = false,
            mutedAmount = true,
            modifier = unclassifiedClickable.testTag("summary_not_counted_unclassified")
        )
    }
}

@Composable
private fun SummaryDefinitionRow(
    title: String,
    subtitle: String,
    amountRupees: Long,
    emphasize: Boolean,
    mutedAmount: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = InkPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = RupeeFormatter.format(amountRupees),
            style = if (emphasize) {
                RupeeAmountCardStyle.copy(fontWeight = FontWeight.Bold)
            } else {
                RupeeAmountCompactStyle.copy(fontWeight = FontWeight.SemiBold)
            },
            color = when {
                emphasize -> EvidenceSourceMatchedInk
                mutedAmount -> InkSecondary
                else -> InkPrimary
            }
        )
    }
}

/**
 * Simple 6-month bar chart plotting ONLY received (bank-matched) work income amounts.
 * Uses Jetpack Compose Canvas sized from DrawScope for clean responsive rendering.
 */
@Composable
fun MonthlyReceivedBarChartCard(
    bars: List<MonthlyReceivedBar>,
    modifier: Modifier = Modifier
) {
    PassportSectionCard(modifier = modifier.testTag("passport_monthly_bar_chart_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.BarChart,
                    contentDescription = null,
                    tint = InkPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Monthly Received (Bank-Matched)",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkPrimary
                    )
                    Text(
                        text = "Only bank-matched work payouts · Last 6 months",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (bars.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = StoneSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Text(
                    text = "No received bank-matched payouts yet. Match a bank credit or import a bank statement CSV to plot monthly received totals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            val maxAmount = bars.maxOfOrNull { it.amountRupees }.coerceAtLeastOrOne()
            val chartDescription = bars.joinToString(", ") {
                "${it.fullMonthLabel}: ${RupeeFormatter.format(it.amountRupees)}"
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Monthly received bank-matched bar chart. $chartDescription" }
            ) {
                // Value labels above each bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    bars.forEach { bar ->
                        Text(
                            text = if (bar.amountRupees > 0L) formatCompactRupees(bar.amountRupees) else "",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (bar.amountRupees > 0L) EvidenceSourceMatchedInk else InkSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Responsive Canvas bar plot
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .testTag("passport_monthly_bar_canvas")
                ) {
                    val slotWidth = size.width / bars.size.coerceAtLeast(1)
                    val barWidth = (slotWidth * 0.52f).coerceAtMost(44.dp.toPx())
                    val baselineY = size.height - 2.dp.toPx()
                    val usableHeight = (baselineY - 6.dp.toPx()).coerceAtLeast(12f)
                    val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())

                    // Subtle baseline
                    drawLine(
                        color = HairlineBorder,
                        start = Offset(0f, baselineY),
                        end = Offset(size.width, baselineY),
                        strokeWidth = 1.dp.toPx()
                    )

                    bars.forEachIndexed { idx, bar ->
                        val left = (idx * slotWidth) + ((slotWidth - barWidth) / 2f)
                        if (bar.amountRupees > 0L) {
                            val ratio = (bar.amountRupees.toFloat() / maxAmount.toFloat()).coerceIn(0.08f, 1f)
                            val barHeight = usableHeight * ratio
                            drawRoundRect(
                                color = EvidenceSourceMatchedInk,
                                topLeft = Offset(left, baselineY - barHeight),
                                size = Size(barWidth, barHeight),
                                cornerRadius = radius
                            )
                        } else {
                            // Subtle zero-month stub
                            val stubHeight = 4.dp.toPx()
                            drawRoundRect(
                                color = StoneVariant,
                                topLeft = Offset(left, baselineY - stubHeight),
                                size = Size(barWidth, stubHeight),
                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Month abbreviations below each bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    bars.forEach { bar ->
                        Text(
                            text = bar.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (bar.amountRupees > 0L) InkPrimary else InkSecondary,
                            fontWeight = if (bar.amountRupees > 0L) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private fun Long?.coerceAtLeastOrOne(): Long = (this ?: 1L).coerceAtLeast(1L)

private fun formatCompactRupees(amount: Long): String {
    return when {
        amount >= 100_000L -> {
            val lakhs = amount / 100_000.0
            String.format(java.util.Locale.ENGLISH, "Rs %.1fL", lakhs)
        }
        amount >= 10_000L -> {
            val k = amount / 1_000.0
            if (amount % 1_000L == 0L) "Rs ${amount / 1_000L}k"
            else String.format(java.util.Locale.ENGLISH, "Rs %.1fk", k)
        }
        else -> RupeeFormatter.format(amount)
    }
}

/**
 * Collapsible "What do these labels mean?" legend listing all five official evidence labels
 * plus the core rule: "A label rises only when an independent record supports it."
 */
@Composable
fun LabelsLegendCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    PassportSectionCard(modifier = modifier.testTag("passport_labels_legend_card")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onToggle)
                .testTag("passport_labels_legend_toggle"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = InkPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "What do these labels mean?",
                    style = MaterialTheme.typography.titleSmall,
                    color = InkPrimary
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse labels legend" else "Expand labels legend",
                tint = InkSecondary
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = StoneSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = InkPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = PassportLedgerBuilder.LABEL_PRINCIPLE_TEXT,
                            style = MaterialTheme.typography.labelMedium,
                            color = InkPrimary
                        )
                    }
                }

                val legendItems = listOf(
                    EvidenceStatus.SELF_DECLARED to "Entered manually by the worker. Not yet supported by an uploaded document or bank credit.",
                    EvidenceStatus.DOCUMENT_EXTRACTED to "Read from an uploaded statement, invoice, or note and explicitly confirmed by the worker.",
                    EvidenceStatus.SOURCE_MATCHED to "Backed by a deterministic bank credit match. Both records represent one payment, counted once.",
                    EvidenceStatus.SOURCE_VERIFIED_SIMULATED to "Imported from a simulated platform API feed (always marked Simulated).",
                    EvidenceStatus.DISPUTED to "A math mismatch, client amount conflict, or open dispute was flagged. Overrides every other label."
                )

                legendItems.forEachIndexed { index, (status, description) ->
                    if (index > 0) HorizontalDivider(color = HairlineBorder)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        EvidenceBadge(status = status)
                        Text(
                            text = description,
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
 * Adaptive 6-column Evidence Ledger:
 * - On wide screens (>= 600.dp): true 6-column table with header row (Income, Amount, Evidence, Relationship, Payment, Why?).
 * - On compact phones (< 600.dp): structured 6-field ledger rows displaying every column clearly with zero horizontal clipping.
 */
@Composable
fun EvidenceLedgerCard(
    rows: List<EvidenceLedgerRow>,
    onWhyClick: (EvidenceLedgerRow) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isWideTable = maxWidth >= 600.dp
        if (isWideTable) {
            WideEvidenceLedgerTable(rows = rows, onWhyClick = onWhyClick)
        } else {
            CompactEvidenceLedgerList(rows = rows, onWhyClick = onWhyClick)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WideEvidenceLedgerTable(
    rows: List<EvidenceLedgerRow>,
    onWhyClick: (EvidenceLedgerRow) -> Unit
) {
    PassportSectionCard(modifier = Modifier.testTag("evidence_ledger_wide_table")) {
        // 6-column table header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StoneSurface, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Income", style = MaterialTheme.typography.labelMedium, color = InkSecondary, modifier = Modifier.weight(1.3f))
            Text("Amount", style = MaterialTheme.typography.labelMedium, color = InkSecondary, modifier = Modifier.weight(1.0f))
            Text("Evidence", style = MaterialTheme.typography.labelMedium, color = InkSecondary, modifier = Modifier.weight(1.1f))
            Text("Relationship", style = MaterialTheme.typography.labelMedium, color = InkSecondary, modifier = Modifier.weight(0.95f))
            Text("Payment", style = MaterialTheme.typography.labelMedium, color = InkSecondary, modifier = Modifier.weight(0.95f))
            Text("Why?", style = MaterialTheme.typography.labelMedium, color = InkSecondary, textAlign = TextAlign.End, modifier = Modifier.width(72.dp))
        }

        Spacer(Modifier.height(6.dp))

        rows.forEachIndexed { idx, row ->
            if (idx > 0) HorizontalDivider(color = HairlineBorder)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 4.dp)
                    .testTag("evidence_ledger_row_${row.recordId}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Col 1: Income (source, period)
                Column(
                    modifier = Modifier.weight(1.3f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = row.sourceName,
                        style = MaterialTheme.typography.titleSmall,
                        color = InkPrimary
                    )
                    Text(
                        text = row.periodDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                    if (row.linkedFileCount > 1 || row.simulatedApi) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (row.simulatedApi) {
                                NeutralSmallChip(
                                    label = "Simulated API",
                                    modifier = Modifier.testTag("simulated_api_pill_${row.recordId}")
                                )
                            }
                            if (row.linkedFileCount > 1) {
                                NeutralSmallChip(
                                    label = "${row.linkedFileCount} files linked",
                                    modifier = Modifier.testTag("grouped_files_chip_${row.recordId}")
                                )
                            }
                        }
                    }
                }

                // Col 2: Amount (gross / net / invoice)
                Column(
                    modifier = Modifier.weight(1.0f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = row.primaryAmountLabel,
                        style = RupeeAmountCompactStyle.copy(fontWeight = FontWeight.Bold),
                        color = InkPrimary
                    )
                    row.amountBreakdownLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }
                }

                // Col 3: Evidence label chip
                Box(modifier = Modifier.weight(1.1f)) {
                    EvidenceBadge(status = row.evidenceStatus)
                }

                // Col 4: Relationship status chip
                Box(modifier = Modifier.weight(0.95f)) {
                    NeutralSmallChip(
                        label = row.relationshipStatusLabel,
                        modifier = Modifier.testTag("ledger_relationship_chip_${row.recordId}")
                    )
                }

                // Col 5: Payment status chip
                Box(modifier = Modifier.weight(0.95f)) {
                    NeutralSmallChip(
                        label = row.paymentStatusLabel,
                        modifier = Modifier.testTag("ledger_payment_chip_${row.recordId}")
                    )
                }

                // Col 6: Why? button
                OutlinedButton(
                    onClick = { onWhyClick(row) },
                    modifier = Modifier
                        .width(72.dp)
                        .heightIn(min = 48.dp)
                        .testTag("ledger_why_button_${row.recordId}"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardSurface,
                        contentColor = InkPrimary
                    )
                ) {
                    Text("Why?", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactEvidenceLedgerList(
    rows: List<EvidenceLedgerRow>,
    onWhyClick: (EvidenceLedgerRow) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("evidence_ledger_compact_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { row ->
            PassportSectionCard(modifier = Modifier.testTag("evidence_ledger_row_${row.recordId}")) {
                // Row header: Col 1 (Income: source, period) + Col 2 (Amount: gross / net / invoice)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = row.sourceName,
                            style = MaterialTheme.typography.titleMedium,
                            color = InkPrimary
                        )
                        Text(
                            text = row.periodDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = row.primaryAmountLabel,
                            style = RupeeAmountCompactStyle.copy(fontWeight = FontWeight.Bold),
                            color = InkPrimary
                        )
                        row.amountBreakdownLines.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = HairlineBorder)
                Spacer(Modifier.height(10.dp))

                // Col 3: Evidence label chip + linked / simulated neutral pills
                Text(
                    text = "Evidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSecondary
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EvidenceBadge(status = row.evidenceStatus)
                    if (row.simulatedApi) {
                        NeutralSmallChip(
                            label = "Simulated API",
                            modifier = Modifier.testTag("simulated_api_pill_${row.recordId}")
                        )
                    }
                    if (row.linkedFileCount > 1) {
                        NeutralSmallChip(
                            label = "${row.linkedFileCount} files linked",
                            modifier = Modifier.testTag("grouped_files_chip_${row.recordId}")
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Col 4 (Relationship status) + Col 5 (Payment status) + Col 6 (Why? button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "Relationship",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkSecondary
                            )
                            NeutralSmallChip(
                                label = row.relationshipStatusLabel,
                                modifier = Modifier.testTag("ledger_relationship_chip_${row.recordId}")
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "Payment",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkSecondary
                            )
                            NeutralSmallChip(
                                label = row.paymentStatusLabel,
                                modifier = Modifier.testTag("ledger_payment_chip_${row.recordId}")
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { onWhyClick(row) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("ledger_why_button_${row.recordId}"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, InkPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardSurface,
                            contentColor = InkPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Why?", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/**
 * Material 3 ModalBottomSheet showing the deterministic plain-language explanation
 * behind an income ledger row's evidence label, grouping, relationship, and payment status.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WhyExplanationBottomSheet(
    row: EvidenceLedgerRow,
    onDismiss: () -> Unit,
    confirmationSection: (@Composable () -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardSurface,
        contentColor = InkPrimary,
        modifier = Modifier.testTag("why_explanation_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Why this label?",
                style = MaterialTheme.typography.labelMedium,
                color = InkSecondary
            )
            Text(
                text = row.whyHeadline,
                style = MaterialTheme.typography.headlineSmall,
                color = InkPrimary
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EvidenceBadge(status = row.evidenceStatus)
                NeutralSmallChip(label = row.relationshipStatusLabel)
                NeutralSmallChip(label = row.paymentStatusLabel)
                if (row.simulatedApi) {
                    NeutralSmallChip(label = "Simulated API")
                }
                if (row.linkedFileCount > 1) {
                    NeutralSmallChip(label = "${row.linkedFileCount} files linked")
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (row.evidenceStatus == EvidenceStatus.SOURCE_MATCHED) EvidenceSourceMatchedBg else StoneSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Text(
                    text = row.whyExplanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkPrimary,
                    modifier = Modifier
                        .padding(14.dp)
                        .testTag("why_explanation_body")
                )
            }

            if (row.whyFacts.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = StoneSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.whyFacts.forEachIndexed { idx, (label, value) ->
                            if (idx > 0) HorizontalDivider(color = HairlineBorder)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkSecondary
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkPrimary
                                )
                            }
                        }
                    }
                }
            }

            confirmationSection?.invoke()

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("why_explanation_close_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = InkPrimary,
                    contentColor = OnDarkInk
                )
            ) {
                Text("Got it", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Friendly empty state for workers who have not added any earnings or bank credits yet.
 */
@Composable
fun FriendlyLedgerEmptyState(
    onAddFirstRecord: () -> Unit,
    onLoadAshaDemo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    EmptyStateCard(
        title = "Your Evidence Ledger is ready",
        body = "Add a platform statement, client invoice, bank CSV, or manual entry. Every figure will appear with its evidence label, payment status, and a clear \"Why?\" explanation.",
        icon = Icons.Outlined.AccountBalance,
        modifier = modifier.testTag("passport_friendly_empty_state"),
        action = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddFirstRecord,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("empty_state_add_first_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InkPrimary,
                        contentColor = OnDarkInk
                    )
                ) {
                    Text("Add First Record", style = MaterialTheme.typography.labelLarge)
                }
                if (onLoadAshaDemo != null) {
                    OutlinedButton(
                        onClick = onLoadAshaDemo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("empty_state_load_demo_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, InkPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardSurface,
                            contentColor = InkPrimary
                        )
                    ) {
                        Text("Load Asha Rao Demo Data", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    )
}
