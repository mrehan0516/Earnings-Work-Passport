package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ActorRole
import com.example.ui.components.NotALenderNotice
import com.example.ui.components.PassportSectionCard
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.OnDarkInk
import com.example.ui.theme.StoneSurface
import com.example.viewmodel.PassportWorkspaceData

@Composable
fun SettingsScreen(
    workspace: PassportWorkspaceData,
    userEmail: String?,
    isBusy: Boolean,
    onSwitchProfile: () -> Unit,
    onLoadAshaDemo: () -> Unit,
    onResetAshaDemo: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
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
                IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Go back",
                        tint = InkPrimary
                    )
                }
                Column {
                    Text(
                        text = "Settings & Audit Log",
                        style = MaterialTheme.typography.headlineSmall,
                        color = InkPrimary
                    )
                    Text(
                        text = "Privacy controls, demo reset, and complete decision history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
            }
        }

        item {
            PassportSectionCard(modifier = Modifier.widthIn(max = 640.dp)) {
                Text("Profile & Account", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Active worker: ${workspace.activeWorker?.displayName ?: "Not selected"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkPrimary
                )
                if (!userEmail.isNullOrBlank()) {
                    Text(
                        text = "Signed in as: $userEmail (owner-only Firestore isolation)",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }

                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onSwitchProfile,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_switch_profile_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, InkPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = CardSurface, contentColor = InkPrimary)
                ) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Switch Worker Profile (Welcome)")
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onLoadAshaDemo,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_load_demo_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, InkPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = CardSurface, contentColor = InkPrimary)
                ) {
                    Icon(Icons.Outlined.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Load demo data")
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onResetAshaDemo,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_reset_demo_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = CardSurface, contentColor = InkPrimary)
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reset demo")
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onSignOut,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_sign_out_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = OnDarkInk)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }

        item {
            NotALenderNotice(modifier = Modifier.widthIn(max = 640.dp))
        }

        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = InkPrimary)
                Text(
                    text = "Audit Log (${workspace.auditLogs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary
                )
            }
        }

        if (workspace.auditLogs.isEmpty()) {
            item {
                PassportSectionCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Text(
                        text = "No audit entries recorded yet for this worker profile.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }
            }
        } else {
            items(workspace.auditLogs, key = { it.id }) { entry ->
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
                                text = ActorRole.fromCode(entry.actorRole).label,
                                style = MaterialTheme.typography.labelSmall,
                                color = InkPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = entry.targetCollection,
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSecondary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = entry.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkPrimary
                    )
                }
            }
        }
    }
}
