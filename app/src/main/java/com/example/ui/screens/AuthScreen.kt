package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.attemptAutoSignIn
import com.example.data.onGoogleSignInClicked
import com.example.domain.rules.CounterpartyConfirmationRules
import com.example.model.EvidenceStatus
import com.example.ui.components.EvidenceBadge
import com.example.ui.components.NotALenderNotice
import com.example.ui.components.PassportSectionCard
import com.example.ui.components.TrustFooterBanner
import com.example.ui.theme.CardSurface
import com.example.ui.theme.EvidenceDisputedBg
import com.example.ui.theme.EvidenceDisputedInk
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.OnDarkInk
import com.example.ui.theme.PaperCanvas

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onOpenConfirmationLink: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var isCheckingAutoSignIn by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPasteLinkDialog by remember { mutableStateOf(false) }
    var pastedLinkInput by remember { mutableStateOf("") }
    var pasteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingAutoSignIn = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isCheckingAutoSignIn = false
            },
            scope = scope
        )
    }

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
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(InkPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Badge,
                        contentDescription = "Earnings and Work Passport emblem",
                        tint = OnDarkInk,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Earnings & Work Passport",
                        style = MaterialTheme.typography.displaySmall,
                        color = InkPrimary
                    )
                    Text(
                        text = "Build a clear, evidence-labeled record of your gig and freelance income in India—even without a payslip.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkSecondary
                    )
                }

                PassportSectionCard {
                    Text(
                        text = "Private to your account",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Sign in with Google so your Firestore records stay strictly private—only you can read or write your data. Right after sign-in, you can explore the synthetic demo worker \"Asha Rao\" or start your own profile.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                    Spacer(Modifier.height(16.dp))

                    if (!errorMessage.isNullOrBlank()) {
                        Surface(
                            color = EvidenceDisputedBg,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = EvidenceDisputedInk,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    errorMessage = msg
                                },
                                scope = scope,
                                onAuthCancelled = {
                                    isLoading = false
                                }
                            )
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("google_sign_in_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InkPrimary,
                            contentColor = OnDarkInk
                        )
                    ) {
                        if (isLoading || isCheckingAutoSignIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = OnDarkInk,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Sign in with Google",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                // Client / Counterparty zero-sign-in entry point
                PassportSectionCard {
                    Text(
                        text = "Asked to confirm a worker's record?",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "No sign-in needed. Paste the confirmation link shared by the worker to view only that single record and answer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            pasteError = null
                            showPasteLinkDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auth_paste_confirmation_link_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, InkPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardSurface,
                            contentColor = InkPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Paste Confirmation Link",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                PassportSectionCard {
                    Text(
                        text = "Every rupee figure shows its evidence label",
                        style = MaterialTheme.typography.titleSmall,
                        color = InkPrimary
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EvidenceStatus.entries.forEach { status ->
                            EvidenceBadge(status = status)
                        }
                    }
                }

                NotALenderNotice()
            }
        }

        TrustFooterBanner()
    }

    if (showPasteLinkDialog) {
        AlertDialog(
            onDismissRequest = { showPasteLinkDialog = false },
            containerColor = CardSurface,
            title = {
                Text(
                    text = "Open Confirmation Link",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Paste the full link (workpassport://confirm/…) or token sent by the worker. No account or sign-in is required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                    OutlinedTextField(
                        value = pastedLinkInput,
                        onValueChange = {
                            pastedLinkInput = it
                            pasteError = null
                        },
                        label = { Text("Confirmation link or token") },
                        placeholder = { Text("workpassport://confirm/...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_paste_link_input")
                    )
                    if (!pasteError.isNullOrBlank()) {
                        Text(
                            text = pasteError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = EvidenceDisputedInk
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val token = CounterpartyConfirmationRules.extractTokenFromInput(pastedLinkInput)
                        if (token.length < CounterpartyConfirmationRules.MIN_TOKEN_LENGTH) {
                            pasteError = "Please paste a valid confirmation link (at least 32 characters)."
                        } else {
                            showPasteLinkDialog = false
                            pastedLinkInput = ""
                            onOpenConfirmationLink(token)
                        }
                    },
                    modifier = Modifier.testTag("auth_open_pasted_link_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InkPrimary,
                        contentColor = OnDarkInk
                    )
                ) {
                    Text("Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteLinkDialog = false }) {
                    Text("Cancel", color = InkSecondary)
                }
            }
        )
    }
}
