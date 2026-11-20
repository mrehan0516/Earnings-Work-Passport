package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.rules.CounterpartyAnswerChoice
import com.example.domain.rules.CounterpartyConfirmationRules
import com.example.domain.rules.RupeeFormatter
import com.example.domain.rules.WorkerConfirmationResolution
import com.example.model.ConfirmationRequest
import com.example.model.ConfirmationStatus
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
import com.example.ui.theme.StoneSurface
import com.example.viewmodel.CreatedConfirmationLinkInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dialog displayed immediately after creating a 7-day counterparty confirmation link.
 * Offers Copy link and a quick Preview public page button (no sign-in view).
 */
@Composable
fun ConfirmationLinkCreatedDialog(
    info: CreatedConfirmationLinkInfo,
    onDismiss: () -> Unit,
    onOpenPublicPreview: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(info.token) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = InkPrimary
                )
                Text(
                    text = "Confirmation link ready",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Share this link with ${info.partyName} for ${RupeeFormatter.format(info.amountRupees)}. It expires in ${info.expiresInDays} days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkPrimary
                )
                Surface(
                    color = StoneSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = info.link,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = InkPrimary,
                        modifier = Modifier
                            .padding(12.dp)
                            .testTag("created_confirmation_link_text")
                    )
                }
                Text(
                    text = "Privacy: Your client sees ONLY who is asking, the work description, amount, and date for this single record — none of your other records.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(info.link))
                            copied = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("created_confirmation_copy_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InkPrimary,
                            contentColor = OnDarkInk
                        )
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Outlined.CheckCircle else Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (copied) "Copied" else "Copy link")
                    }
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenPublicPreview(info.token)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("created_confirmation_preview_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, InkPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardSurface,
                            contentColor = InkPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Preview")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("created_confirmation_done_button")
            ) {
                Text("Done", color = InkPrimary)
            }
        }
    )
}

/**
 * Reusable worker-facing panel for requesting client confirmation, copying/previewing
 * an active 7-day link, and reviewing or resolving a client's response
 * (Accept correction or Dispute response).
 *
 * Embedded in Passport `Why?` bottom sheet, Party Detail screen, and Issues screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CounterpartyConfirmationPanel(
    record: IncomeRecord,
    latestRequest: ConfirmationRequest?,
    isBusy: Boolean,
    onRequestConfirmation: (IncomeRecord) -> Unit,
    onOpenPublicConfirmation: (String) -> Unit,
    onAcceptClientCorrection: (ConfirmationRequest, IncomeRecord, Long) -> Unit,
    onDisputeClientResponse: (ConfirmationRequest, IncomeRecord, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var copiedToken by remember(latestRequest?.id) { mutableStateOf(false) }
    var showDisputeDialog by remember(latestRequest?.id) { mutableStateOf(false) }
    var disputeNoteInput by remember(latestRequest?.id) { mutableStateOf("") }

    val status = latestRequest?.let { ConfirmationStatus.fromCode(it.status) }
    val isExpired = latestRequest?.expiresAt?.toDate()?.time?.let { expMs ->
        CounterpartyConfirmationRules.isRequestExpired(expMs, System.currentTimeMillis())
    } == true

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("counterparty_confirmation_panel_${record.id}"),
        color = StoneSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Client confirmation",
                    style = MaterialTheme.typography.titleSmall,
                    color = InkPrimary
                )
                val badgeText = when {
                    latestRequest == null -> "Not requested"
                    status == ConfirmationStatus.PENDING && isExpired -> "Expired"
                    status != null -> status.label
                    else -> "Not requested"
                }
                NeutralSmallChip(label = badgeText)
            }

            if (latestRequest == null || (status == ConfirmationStatus.PENDING && isExpired)) {
                Text(
                    text = if (isExpired) {
                        "The previous confirmation link expired after 7 days. Create a fresh link to ask ${record.partyName.ifBlank { "your client" }}."
                    } else {
                        "Create a 7-day link asking ${record.partyName.ifBlank { "your client" }} 3 separate questions (work delivered, amount, payment). Positive confirmation updates relationship & payment status without changing the evidence label."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                OutlinedButton(
                    onClick = { onRequestConfirmation(record) },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("request_client_confirmation_${record.id}"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, InkPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardSurface,
                        contentColor = InkPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Request client confirmation")
                }
            } else if (status == ConfirmationStatus.PENDING && latestRequest.isPublicTokenRequest()) {
                val link = CounterpartyConfirmationRules.buildConfirmationLink(latestRequest.id)
                val expiryText = latestRequest.expiresAt?.toDate()?.let { date ->
                    "Expires ${formatShortDate(date)}"
                } ?: "Expires in 7 days"

                Text(
                    text = "Waiting for ${latestRequest.partyName} · Claim v${latestRequest.claimVersion} · $expiryText",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Surface(
                    color = CardSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = link,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = InkPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(link))
                            copiedToken = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("copy_confirmation_link_${latestRequest.id}"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, InkPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardSurface,
                            contentColor = InkPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (copiedToken) Icons.Outlined.CheckCircle else Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (copiedToken) "Copied" else "Copy link")
                    }
                    Button(
                        onClick = { onOpenPublicConfirmation(latestRequest.id) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("preview_confirmation_link_${latestRequest.id}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InkPrimary,
                            contentColor = OnDarkInk
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Open public page")
                    }
                }
            } else {
                // Responded / Declined / Disputed / Simulated
                val outcome = CounterpartyConfirmationRules.evaluateRequest(latestRequest)
                val parsed = CounterpartyConfirmationRules.parseResponses(latestRequest.responses)
                val respondedLabel = latestRequest.responded_at?.toDate()?.let { formatTimestamp(it) }
                    ?: latestRequest.updatedAt?.toDate()?.let { formatTimestamp(it) }
                    ?: ""

                Text(
                    text = buildString {
                        append("${latestRequest.partyName} responded to Claim v${parsed.claimVersionShown}")
                        if (respondedLabel.isNotBlank()) append(" on $respondedLabel")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )

                // Separate per-question answer breakdown
                Surface(
                    color = CardSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val workAnswer = parsed.workDelivered?.label
                            ?: latestRequest.workDelivered?.let { if (it) "Yes" else "No" }
                            ?: "Not answered"
                        val amountAnswer = when {
                            parsed.amountCorrect == CounterpartyAnswerChoice.CORRECTED_AMOUNT && parsed.correctedAmountRupees != null ->
                                "Corrected to ${RupeeFormatter.format(parsed.correctedAmountRupees)} (claimed ${RupeeFormatter.format(latestRequest.requestedAmountRupees)})"
                            parsed.amountCorrect != null -> parsed.amountCorrect.label
                            latestRequest.confirmedAmountRupees != null -> RupeeFormatter.format(latestRequest.confirmedAmountRupees)
                            else -> "Not answered"
                        }
                        val paidAnswer = when {
                            parsed.paidStatus == CounterpartyAnswerChoice.YES && parsed.paidDate.isNotBlank() ->
                                "Yes (${parsed.paidDate})"
                            parsed.paidStatus != null -> parsed.paidStatus.label
                            latestRequest.paymentSent != null ->
                                if (latestRequest.paymentSent) "Yes ${latestRequest.paymentDate}".trim() else "No"
                            else -> "Not answered"
                        }

                        AnswerLineRow(question = "1. Work delivered", answer = workAnswer)
                        HorizontalDivider(color = HairlineBorder)
                        AnswerLineRow(question = "2. Amount correct", answer = amountAnswer)
                        HorizontalDivider(color = HairlineBorder)
                        AnswerLineRow(question = "3. Paid & when", answer = paidAnswer)
                    }
                }

                // Propagated status chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (outcome.contactVerified) {
                        NeutralSmallChip(label = "contact_verified")
                    }
                    if (outcome.workConfirmed) {
                        NeutralSmallChip(label = "counterparty_acknowledged")
                    }
                    if (outcome.paymentConfirmed) {
                        NeutralSmallChip(label = "payer_confirmed")
                    }
                    if (outcome.triggersDispute) {
                        Surface(
                            color = EvidenceDisputedBg,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "Dispute flagged",
                                style = MaterialTheme.typography.labelSmall,
                                color = EvidenceDisputedInk,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = EvidenceSourceMatchedBg,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "Evidence label unchanged",
                                style = MaterialTheme.typography.labelSmall,
                                color = EvidenceSourceMatchedInk,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (outcome.workerResolution == WorkerConfirmationResolution.CORRECTION_ACCEPTED ||
                    outcome.workerResolution == WorkerConfirmationResolution.DISPUTED_BY_WORKER
                ) {
                    Text(
                        text = "Your decision: ${outcome.workerResolution.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkPrimary
                    )
                }

                // Worker actions: Accept correction or Dispute response
                val correctedAmt = outcome.correctedAmountRupees
                val showAcceptCorrection = correctedAmt != null &&
                    outcome.workerResolution != WorkerConfirmationResolution.CORRECTION_ACCEPTED
                val showDisputeAction = outcome.workerResolution != WorkerConfirmationResolution.DISPUTED_BY_WORKER

                if (showAcceptCorrection || showDisputeAction) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showAcceptCorrection) {
                            val acceptedAmount = requireNotNull(correctedAmt)
                            Button(
                                onClick = { onAcceptClientCorrection(latestRequest, record, acceptedAmount) },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("accept_client_correction_${latestRequest.id}"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = InkPrimary,
                                    contentColor = OnDarkInk
                                )
                            ) {
                                Text("Accept correction (${RupeeFormatter.format(correctedAmt)})")
                            }
                        }
                        if (showDisputeAction) {
                            OutlinedButton(
                                onClick = { showDisputeDialog = true },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("dispute_client_response_${latestRequest.id}"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, EvidenceDisputedInk),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = CardSurface,
                                    contentColor = EvidenceDisputedInk
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ReportProblem,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Dispute response")
                            }
                        }
                    }
                }

                // Allow sending a new versioned confirmation link if needed
                TextButton(
                    onClick = { onRequestConfirmation(record) },
                    enabled = !isBusy,
                    modifier = Modifier
                        .align(Alignment.End)
                        .heightIn(min = 48.dp)
                        .testTag("request_new_confirmation_${record.id}")
                ) {
                    Text("Request new link (v${latestRequest.claimVersion + 1})", color = InkPrimary)
                }
            }
        }
    }

    if (showDisputeDialog && latestRequest != null) {
        AlertDialog(
            onDismissRequest = { showDisputeDialog = false },
            containerColor = CardSurface,
            title = {
                Text(
                    text = "Dispute client response",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Both your version and ${latestRequest.partyName}'s answer will stay side by side in Issues and will never be deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                    OutlinedTextField(
                        value = disputeNoteInput,
                        onValueChange = { disputeNoteInput = it },
                        label = { Text("Your note (optional)") },
                        placeholder = { Text("e.g., Client missed bonus or deducted fee without invoice") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dispute_client_response_note_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisputeDialog = false
                        onDisputeClientResponse(latestRequest, record, disputeNoteInput)
                        disputeNoteInput = ""
                    },
                    enabled = !isBusy,
                    modifier = Modifier.testTag("dispute_client_response_confirm_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EvidenceDisputedInk,
                        contentColor = OnDarkInk
                    )
                ) {
                    Text("Mark Disputed")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisputeDialog = false }) {
                    Text("Cancel", color = InkSecondary)
                }
            }
        )
    }
}

@Composable
private fun AnswerLineRow(question: String, answer: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary,
            modifier = Modifier.weight(0.95f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = answer,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = InkPrimary,
            modifier = Modifier.weight(1.05f)
        )
    }
}

private fun formatShortDate(date: Date): String =
    SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(date)

private fun formatTimestamp(date: Date): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH).format(date)
