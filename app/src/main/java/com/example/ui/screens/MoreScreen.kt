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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.EvidenceStatus
import com.example.ui.components.EvidenceBadge
import com.example.ui.components.NotALenderNotice
import com.example.ui.components.PassportSectionCard
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.StoneSurface
import com.example.viewmodel.PassportWorkspaceData

@Composable
fun MoreScreen(
    workspace: PassportWorkspaceData,
    onNavigateToParties: () -> Unit,
    onNavigateToReviewConfirm: () -> Unit,
    onNavigateToReviewerView: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSwitchWorkerProfile: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("more_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    text = "More Tools",
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkPrimary
                )
                Text(
                    text = "Active profile: ${workspace.activeWorker?.displayName ?: "None"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MoreMenuRow(
                    icon = Icons.Outlined.Groups,
                    title = "Parties & Simulated Confirmations",
                    subtitle = "${workspace.parties.size} work sources · ${workspace.confirmationRequests.size} confirmation requests",
                    onClick = onNavigateToParties,
                    testTag = "more_open_parties"
                )
                MoreMenuRow(
                    icon = Icons.Outlined.Description,
                    title = "Review & Confirm AI Extracts",
                    subtitle = if (workspace.pendingDocuments.isNotEmpty()) {
                        "${workspace.pendingDocuments.size} document(s) waiting for your confirmation"
                    } else {
                        "Inspect and confirm values read by AI"
                    },
                    onClick = onNavigateToReviewConfirm,
                    testTag = "more_open_review"
                )
                MoreMenuRow(
                    icon = Icons.Outlined.Visibility,
                    title = "Reviewer View (Public Report Preview)",
                    subtitle = "${workspace.shareLinks.size} consent report(s) available to inspect",
                    onClick = onNavigateToReviewerView,
                    testTag = "more_open_reviewer"
                )
                MoreMenuRow(
                    icon = Icons.Outlined.Settings,
                    title = "Settings & Audit Log",
                    subtitle = "${workspace.auditLogs.size} transparency log entries · Reset demo · Sign out",
                    onClick = onNavigateToSettings,
                    testTag = "more_open_settings"
                )
                MoreMenuRow(
                    icon = Icons.Outlined.SwapHoriz,
                    title = "Switch Worker Profile",
                    subtitle = "Return to Welcome to switch between Asha Rao demo and your own name",
                    onClick = onSwitchWorkerProfile,
                    testTag = "more_switch_worker"
                )
            }
        }

        item {
            PassportSectionCard(modifier = Modifier.widthIn(max = 640.dp)) {
                Text(
                    text = "Evidence Label Guide",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Colors are used only for these 5 evidence labels, and the label text is always shown:",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EvidenceStatus.entries.forEach { status ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            EvidenceBadge(status = status)
                            Text(
                                text = status.shortExplanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                        }
                    }
                }
            }
        }

        item {
            NotALenderNotice(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun MoreMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StoneSurface,
                    modifier = Modifier.size(40.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(icon, contentDescription = null, tint = InkPrimary, modifier = Modifier.size(20.dp))
                    }
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = "Open $title",
                tint = InkSecondary
            )
        }
    }
}
