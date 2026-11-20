package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.rules.PassportAggregator
import com.example.domain.rules.RupeeFormatter
import com.example.model.EvidenceStatus
import com.example.model.ShareLink
import com.example.model.ShareLinkStatus
import com.example.ui.components.NotALenderNotice
import com.example.ui.components.PassportSectionCard
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.OnDarkInk
import com.example.ui.theme.RupeeAmountCompactStyle
import com.example.ui.theme.StoneSurface
import com.example.viewmodel.PassportWorkspaceData

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareScreen(
    workspace: PassportWorkspaceData,
    isBusy: Boolean,
    onCreateShareLink: (
        recipientLabel: String,
        dateRangeLabel: String,
        includedPartyIds: List<String>,
        includedEvidenceLevels: List<String>,
        maskBankRefs: Boolean,
        expiresOn: String
    ) -> Unit,
    onRevokeShareLink: (ShareLink) -> Unit,
    onOpenReviewerView: (String) -> Unit
) {
    val context = LocalContext.current
    var recipientLabel by rememberSaveable { mutableStateOf("") }
    var dateRangeLabel by rememberSaveable { mutableStateOf("Oct 2026 – Nov 2026") }
    var expiresOn by rememberSaveable { mutableStateOf("2026-12-31") }
    var maskBankRefs by rememberSaveable { mutableStateOf(true) }
    var consentChecked by rememberSaveable { mutableStateOf(false) }

    var selectedPartyIds by remember(workspace.parties) {
        mutableStateOf(workspace.parties.map { it.id }.toSet())
    }
    var selectedEvidenceCodes by remember {
        mutableStateOf(
            setOf(
                EvidenceStatus.SOURCE_VERIFIED_SIMULATED.code,
                EvidenceStatus.SOURCE_MATCHED.code,
                EvidenceStatus.DOCUMENT_EXTRACTED.code
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("share_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Share by Consent",
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkPrimary
                )
                Text(
                    text = "You decide what to include, who sees it, and when to revoke access. Every shared figure keeps its evidence label.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
            }
        }

        item {
            PassportSectionCard(modifier = Modifier.widthIn(max = 640.dp)) {
                Text(
                    text = "Create Read-Only Report",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = recipientLabel,
                    onValueChange = { recipientLabel = it },
                    label = { Text("Who are you sharing with?") },
                    placeholder = { Text("e.g. Landlord, Co-op Office, Visa Desk") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_recipient_input")
                )
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dateRangeLabel,
                        onValueChange = { dateRangeLabel = it },
                        label = { Text("Date range") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = expiresOn,
                        onValueChange = { expiresOn = it },
                        label = { Text("Valid until") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (workspace.parties.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Include work sources", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        workspace.parties.forEach { party ->
                            val isSelected = party.id in selectedPartyIds
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedPartyIds = if (isSelected) {
                                        selectedPartyIds - party.id
                                    } else {
                                        selectedPartyIds + party.id
                                    }
                                },
                                label = { Text(party.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = InkPrimary,
                                    selectedLabelColor = OnDarkInk
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Include evidence labels", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EvidenceStatus.entries.forEach { status ->
                        val isSelected = status.code in selectedEvidenceCodes
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedEvidenceCodes = if (isSelected) {
                                    selectedEvidenceCodes - status.code
                                } else {
                                    selectedEvidenceCodes + status.code
                                }
                            },
                            label = { Text(status.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = InkPrimary,
                                selectedLabelColor = OnDarkInk
                            )
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mask UTR & bank references",
                            style = MaterialTheme.typography.titleSmall,
                            color = InkPrimary
                        )
                        Text(
                            text = "Shows only the last 4 characters (e.g. ••••0441).",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }
                    Switch(
                        checked = maskBankRefs,
                        onCheckedChange = { maskBankRefs = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnDarkInk,
                            checkedTrackColor = InkPrimary
                        )
                    )
                }

                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { consentChecked = !consentChecked }
                        .testTag("share_consent_checkbox_row"),
                    shape = RoundedCornerShape(12.dp),
                    color = StoneSurface,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = consentChecked,
                            onCheckedChange = { consentChecked = it },
                            colors = CheckboxDefaults.colors(checkedColor = InkPrimary, checkmarkColor = OnDarkInk),
                            modifier = Modifier.testTag("share_consent_checkbox")
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "I consent to create a read-only summary with these selections. I understand this is not a credit score or loan approval.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkPrimary
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        onCreateShareLink(
                            recipientLabel,
                            dateRangeLabel,
                            selectedPartyIds.toList(),
                            selectedEvidenceCodes.toList(),
                            maskBankRefs,
                            expiresOn
                        )
                        recipientLabel = ""
                        consentChecked = false
                    },
                    enabled = !isBusy && recipientLabel.isNotBlank() && selectedEvidenceCodes.isNotEmpty() && consentChecked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("create_share_link_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = OnDarkInk)
                ) {
                    Text("Create Read-Only Report", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        item {
            Text(
                text = "Your Share Links (${workspace.shareLinks.size})",
                style = MaterialTheme.typography.titleMedium,
                color = InkPrimary,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
            )
        }

        items(workspace.shareLinks, key = { it.id }) { link ->
            val isActive = ShareLinkStatus.fromCode(link.status) == ShareLinkStatus.ACTIVE
            val report = PassportAggregator.buildReviewerReport(
                shareLink = link,
                allIncomeRecords = workspace.incomeRecords,
                allBankCredits = workspace.bankCredits
            )
            PassportSectionCard(modifier = Modifier.widthIn(max = 640.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = link.recipientLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = InkPrimary
                        )
                        Text(
                            text = "Code: ${link.shareCode} · ${link.dateRangeLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }
                    Surface(
                        color = StoneSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Text(
                            text = if (isActive) "Active" else "Revoked",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) InkPrimary else InkSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Included total: ${RupeeFormatter.format(report.totalRupees)} · Expires ${link.expiresOn}",
                    style = RupeeAmountCompactStyle.copy(fontWeight = FontWeight.Medium),
                    color = InkPrimary
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onOpenReviewerView(link.id) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("open_reviewer_view_${link.id}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = OnDarkInk)
                    ) {
                        Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reviewer View", style = MaterialTheme.typography.labelMedium)
                    }

                    if (isActive) {
                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Earnings & Work Passport (${link.shareCode})")
                                    putExtra(Intent.EXTRA_TEXT, report.plainTextExport)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share read-only report"))
                            },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, InkPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = CardSurface, contentColor = InkPrimary)
                        ) {
                            Icon(Icons.Outlined.IosShare, contentDescription = "Share report text", modifier = Modifier.size(18.dp))
                        }

                        OutlinedButton(
                            onClick = { onRevokeShareLink(link) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("revoke_share_link_${link.id}"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = CardSurface, contentColor = InkSecondary)
                        ) {
                            Icon(Icons.Outlined.LinkOff, contentDescription = "Revoke link", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        item {
            NotALenderNotice(
                compact = true,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .padding(bottom = 12.dp)
            )
        }
    }
}
