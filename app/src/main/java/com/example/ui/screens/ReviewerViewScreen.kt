package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.rules.PassportAggregator
import com.example.domain.rules.RupeeFormatter
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.EvidenceBadge
import com.example.ui.components.EvidenceTierSummaryRow
import com.example.ui.components.NotALenderNotice
import com.example.ui.components.PassportSectionCard
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.OnDarkInk
import com.example.ui.theme.RupeeAmountCardStyle
import com.example.ui.theme.RupeeAmountHeroStyle
import com.example.ui.theme.StoneSurface
import com.example.viewmodel.PassportWorkspaceData

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewerViewScreen(
    workspace: PassportWorkspaceData,
    selectedShareLinkId: String?,
    onSelectShareLink: (String) -> Unit,
    onBack: () -> Unit,
    onNavigateToShare: () -> Unit
) {
    val context = LocalContext.current
    val activeLink = selectedShareLinkId?.let { id -> workspace.shareLinks.firstOrNull { it.id == id } }
        ?: workspace.shareLinks.firstOrNull()

    val report = activeLink?.let { link ->
        PassportAggregator.buildReviewerReport(
            shareLink = link,
            allIncomeRecords = workspace.incomeRecords,
            allBankCredits = workspace.bankCredits
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reviewer_view_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("reviewer_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Go back",
                        tint = InkPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reviewer View (Public Read-Only)",
                        style = MaterialTheme.typography.headlineSmall,
                        color = InkPrimary
                    )
                    Text(
                        text = "Exact read-only report seen by the recipient with your consent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
            }
        }

        if (workspace.shareLinks.size > 1) {
            item {
                FlowRow(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    workspace.shareLinks.forEach { link ->
                        FilterChip(
                            selected = link.id == activeLink?.id,
                            onClick = { onSelectShareLink(link.id) },
                            label = { Text("${link.shareCode} (${link.recipientLabel.take(18)})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = InkPrimary,
                                selectedLabelColor = OnDarkInk
                            )
                        )
                    }
                }
            }
        }

        if (report == null) {
            item {
                EmptyStateCard(
                    title = "No shared report yet",
                    body = "Create a consent-based read-only share link in the Share tab to preview Reviewer View here.",
                    icon = Icons.Outlined.Lock,
                    modifier = Modifier.widthIn(max = 640.dp),
                    action = {
                        Button(
                            onClick = onNavigateToShare,
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = OnDarkInk)
                        ) {
                            Text("Go to Share")
                        }
                    }
                )
            }
        } else if (report.isRevoked) {
            item {
                EmptyStateCard(
                    title = "This Share Link Was Revoked",
                    body = "Worker ${report.shareLink.workerName} revoked read-only report ${report.shareLink.shareCode} for ${report.shareLink.recipientLabel}. Reviewers can no longer view its earnings figures.",
                    icon = Icons.Outlined.VisibilityOff,
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .testTag("reviewer_revoked_banner")
                )
            }
        } else {
            item {
                NotALenderNotice(modifier = Modifier.widthIn(max = 640.dp))
            }

            item {
                PassportSectionCard(modifier = Modifier.widthIn(max = 640.dp)) {
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
                                text = "READ-ONLY · ${report.shareLink.shareCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = "Valid until ${report.shareLink.expiresOn}",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = report.shareLink.workerName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = InkPrimary
                    )
                    Text(
                        text = "Shared with: ${report.shareLink.recipientLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                    Text(
                        text = "Period: ${report.shareLink.dateRangeLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Included earnings (non-disputed)",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSecondary
                    )
                    Text(
                        text = RupeeFormatter.format(report.totalRupees),
                        style = RupeeAmountHeroStyle,
                        color = InkPrimary,
                        modifier = Modifier.testTag("reviewer_total_amount")
                    )

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = HairlineBorder)
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Evidence-labeled totals",
                        style = MaterialTheme.typography.titleSmall,
                        color = InkPrimary
                    )
                    report.tierTotals.forEach { tier ->
                        EvidenceTierSummaryRow(
                            status = tier.status,
                            totalRupees = tier.totalRupees,
                            recordCount = tier.recordCount
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Earnings & Work Passport (${report.shareLink.shareCode})")
                                putExtra(Intent.EXTRA_TEXT, report.plainTextExport)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export read-only report"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("reviewer_export_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = OnDarkInk)
                    ) {
                        Icon(Icons.Outlined.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share Read-Only Summary")
                    }
                }
            }

            item {
                Text(
                    text = "Consented Ledger Entries (${report.lines.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary,
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                )
            }

            items(report.lines) { line ->
                PassportSectionCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(line.partyName, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                            Text(
                                text = listOf(line.earnedDate, line.periodLabel)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                        }
                        Text(
                            text = RupeeFormatter.format(line.amountRupees),
                            style = RupeeAmountCardStyle.copy(fontWeight = FontWeight.Bold),
                            color = InkPrimary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EvidenceBadge(status = line.evidenceStatus)
                        Text(
                            text = "Ref: ${line.displayReference}",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }
                }
            }

            item {
                PassportSectionCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Text("Worker Consent Record", style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(report.shareLink.consentText, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                }
            }
        }
    }
}
