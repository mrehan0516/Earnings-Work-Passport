package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.WorkerProfile
import com.example.ui.components.NotALenderNotice
import com.example.ui.components.PassportSectionCard
import com.example.ui.components.TrustFooterBanner
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.OnDarkInk
import com.example.ui.theme.PaperCanvas
import com.example.ui.theme.StoneSurface

@Composable
fun WelcomeScreen(
    existingWorkers: List<WorkerProfile>,
    isBusy: Boolean,
    onChooseAshaDemo: () -> Unit,
    onResetDemo: () -> Unit,
    onCreateCustomWorker: (name: String, city: String, workType: String) -> Unit,
    onSelectExistingWorker: (String) -> Unit
) {
    var workerName by rememberSaveable { mutableStateOf("") }
    var workerCity by rememberSaveable { mutableStateOf("Bengaluru") }
    var primaryWork by rememberSaveable { mutableStateOf("Delivery & Freelance Work") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperCanvas)
            .safeDrawingPadding()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Welcome to Earnings & Work Passport",
                        style = MaterialTheme.typography.headlineMedium,
                        color = InkPrimary
                    )
                    Text(
                        text = "Choose the synthetic demo worker \"Asha Rao\" to explore every screen right away, or enter a name to start a clean passport.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkSecondary
                    )
                }

                // Primary Demo Worker Option: Asha Rao
                PassportSectionCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayCircleOutline,
                            contentDescription = null,
                            tint = InkPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Demo Worker — Asha Rao (Fictional)",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkPrimary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Asha Rao · Gig Worker & Freelancer. Includes fictional RideCo weekly statements (with duplicate photo & simulated_api record), Ravi Design Studio confirmed invoice, Kumar Stores amount correction, cash tips, and 3 bank credits. All names and logos are fictional.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onChooseAshaDemo,
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("choose_asha_demo_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InkPrimary,
                            contentColor = OnDarkInk
                        )
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = OnDarkInk,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Load demo data",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onResetDemo,
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("welcome_reset_demo_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardSurface,
                            contentColor = InkPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = "Reset demo",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Existing custom profiles if user already created any
                val customWorkers = existingWorkers.filter { !it.demoProfile }
                if (customWorkers.isNotEmpty()) {
                    PassportSectionCard {
                        Text(
                            text = "Your Saved Passports",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkPrimary
                        )
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            customWorkers.forEach { profile ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isBusy) { onSelectExistingWorker(profile.id) }
                                        .testTag("select_worker_${profile.id}"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = StoneSurface,
                                    border = BorderStroke(1.dp, HairlineBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Person,
                                                contentDescription = null,
                                                tint = InkPrimary
                                            )
                                            Column {
                                                Text(
                                                    text = profile.displayName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = InkPrimary
                                                )
                                                Text(
                                                    text = listOf(profile.city, profile.primaryWorkType)
                                                        .filter { it.isNotBlank() }
                                                        .joinToString(" · "),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = InkSecondary
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                            contentDescription = "Open profile",
                                            tint = InkSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Create custom worker profile
                PassportSectionCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonAdd,
                            contentDescription = null,
                            tint = InkPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Or Start With Your Name",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkPrimary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = workerName,
                        onValueChange = { workerName = it },
                        label = { Text("Worker name") },
                        placeholder = { Text("e.g. Ravi Verma") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_worker_name_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = workerCity,
                        onValueChange = { workerCity = it },
                        label = { Text("City") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = primaryWork,
                        onValueChange = { primaryWork = it },
                        label = { Text("Main work") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = {
                            if (workerName.isNotBlank()) {
                                onCreateCustomWorker(workerName, workerCity, primaryWork)
                            }
                        },
                        enabled = !isBusy && workerName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("create_custom_worker_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, InkPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardSurface,
                            contentColor = InkPrimary
                        )
                    ) {
                        Text(
                            text = "Create Clean Passport",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                NotALenderNotice()
            }
        }

        TrustFooterBanner()
    }
}
