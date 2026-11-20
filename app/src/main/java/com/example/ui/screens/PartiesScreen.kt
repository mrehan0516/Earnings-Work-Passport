package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.rules.CounterpartyConfirmationRules
import com.example.domain.rules.PartyRules
import com.example.domain.rules.RupeeFormatter
import com.example.domain.rules.WorkerConfirmationResolution
import com.example.model.ConfirmationRequest
import com.example.model.ConfirmationStatus
import com.example.model.EvidenceStatus
import com.example.model.IncomeRecord
import com.example.model.PartyRelationshipStatus
import com.example.model.PartyRole
import com.example.model.PartyType
import com.example.model.PayoutCycle
import com.example.ui.components.AddPartyFormCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.EvidenceBadge
import com.example.ui.components.NeutralSmallChip
import com.example.ui.components.PartyRolePillsRow
import com.example.ui.components.PartyStatusChipsRow
import com.example.ui.components.PassportSectionCard
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.OnDarkInk
import com.example.ui.theme.RupeeAmountCardStyle
import com.example.ui.theme.RupeeAmountCompactStyle
import com.example.ui.theme.StoneSurface
import com.example.viewmodel.PassportWorkspaceData

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartiesScreen(
    workspace: PassportWorkspaceData,
    isBusy: Boolean,
    onAddParty: (
        name: String,
        type: PartyType,
        roles: List<PartyRole>,
        phone: String,
        email: String,
        note: String,
        registrationNumber: String,
        roleTitle: String,
        cycle: PayoutCycle,
        startedOn: String
    ) -> Unit,
    onOpenPartyDetail: (String) -> Unit,
    onSendConfirmationRequest: (IncomeRecord) -> Unit,
    onOpenPublicConfirmation: (String) -> Unit = {},
    onAcceptClientCorrection: (ConfirmationRequest, IncomeRecord, Long) -> Unit = { _, _, _ -> },
    onDisputeClientResponse: (ConfirmationRequest, IncomeRecord, String) -> Unit = { _, _, _ -> },
    onSimulatePartyDecision: (ConfirmationRequest, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val requestedRecordIds = workspace.confirmationRequests.map { it.incomeRecordId }.toSet()
    var showAddPartyForm by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("parties_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("parties_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Go back",
                        tint = InkPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Parties & Confirmations",
                        style = MaterialTheme.typography.headlineSmall,
                        color = InkPrimary
                    )
                    Text(
                        text = "Work sources, clients, platforms, and payers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { showAddPartyForm = !showAddPartyForm },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("parties_toggle_add_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (showAddPartyForm) InkPrimary else HairlineBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (showAddPartyForm) InkPrimary else CardSurface,
                        contentColor = if (showAddPartyForm) OnDarkInk else InkPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GroupAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (showAddPartyForm) "Close" else "Add",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        if (showAddPartyForm) {
            item {
                AddPartyFormCard(
                    existingParties = workspace.parties,
                    isBusy = isBusy,
                    heading = "Add work source / client / payer",
                    submitLabel = "Save Party",
                    onSubmit = { name, type, roles, phone, email, note, regNum, roleTitle, cycle, startedOn ->
                        onAddParty(name, type, roles, phone, email, note, regNum, roleTitle, cycle, startedOn)
                        showAddPartyForm = false
                    },
                    onUseExistingParty = { existing ->
                        showAddPartyForm = false
                        onOpenPartyDetail(existing.id)
                    },
                    onCancel = { showAddPartyForm = false },
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        }

        if (workspace.summary.partySummaries.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No parties added yet",
                    body = "Add the platforms, businesses, individuals, or payment providers that give you work or pay you.",
                    icon = Icons.Outlined.Groups,
                    modifier = Modifier.widthIn(max = 640.dp),
                    action = {
                        Button(
                            onClick = { showAddPartyForm = true },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = OnDarkInk)
                        ) {
                            Text("Add work source / client / payer")
                        }
                    }
                )
            }
        } else {
            items(workspace.summary.partySummaries, key = { it.party.id }) { ps ->
                val partyRecords = PartyRules.recordsForParty(ps.party, workspace.incomeRecords)
                val eligibleForConfirm = partyRecords.firstOrNull {
                    it.id !in requestedRecordIds &&
                        it.resolvedEvidence() != EvidenceStatus.SOURCE_VERIFIED_SIMULATED &&
                        it.resolvedEvidence() != EvidenceStatus.DISPUTED
                }
                val contactStatus = ps.party.resolvedContactStatus()
                val relationshipStatus = ps.relationship?.resolvedRelationshipStatus()
                    ?: PartyRelationshipStatus.WORKER_DECLARED

                PassportSectionCard(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .clickable { onOpenPartyDetail(ps.party.id) }
                        .testTag("party_card_${ps.party.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = ps.party.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkPrimary
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = "Open ${ps.party.name} details",
                                    tint = InkSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = listOfNotNull(
                                    ps.party.resolvedType().label,
                                    ps.relationship?.roleTitle?.takeIf { it.isNotBlank() },
                                    ps.relationship?.let { PayoutCycle.fromCode(it.payoutCycle).label }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                            val contactLine = listOf(ps.party.phone, ps.party.email, ps.party.contactHint)
                                .firstOrNull { it.isNotBlank() }
                            if (!contactLine.isNullOrBlank()) {
                                Text(
                                    text = contactLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSecondary
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = RupeeFormatter.format(ps.totalRupees),
                            style = RupeeAmountCardStyle,
                            color = InkPrimary
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    PartyRolePillsRow(roles = ps.party.resolvedRoles())

                    Spacer(Modifier.height(6.dp))
                    PartyStatusChipsRow(
                        contactStatus = contactStatus,
                        relationshipStatus = relationshipStatus
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EvidenceBadge(status = ps.highestEvidence)
                        Text(
                            text = "${ps.recordCount} ${if (ps.recordCount == 1) "entry" else "entries"} · Details",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }

                    if (eligibleForConfirm != null) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = HairlineBorder)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { onSendConfirmationRequest(eligibleForConfirm) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("request_confirm_${ps.party.id}"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, InkPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = CardSurface, contentColor = InkPrimary)
                        ) {
                            Text("Request Confirmation (${RupeeFormatter.format(eligibleForConfirm.amountRupees)})")
                        }
                    }
                }
            }
        }

        // Confirmation Requests (Public 7-day Links + Demo Simulation)
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Client Confirmation Requests",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary
                )
                Text(
                    text = "Share a 7-day public link (DEMO OTP protected) or review client responses. Positive confirmation updates relationship & payment status without changing the evidence label.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }
        }

        if (workspace.confirmationRequests.isEmpty()) {
            item {
                PassportSectionCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Text(
                        text = "No confirmation requests yet. Tap \"Request Confirmation\" on any party above to test the simulated party flow.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }
            }
        } else {
            items(workspace.confirmationRequests, key = { it.id }) { req ->
                val status = ConfirmationStatus.fromCode(req.status)
                val linkedRec = workspace.incomeRecords.firstOrNull { it.id == req.incomeRecordId }
                val outcome = CounterpartyConfirmationRules.evaluateRequest(req)

                PassportSectionCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(req.partyName, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                            Text(
                                text = "Requested: ${RupeeFormatter.format(req.requestedAmountRupees)} · Claim v${req.claimVersion}",
                                style = RupeeAmountCompactStyle.copy(fontWeight = FontWeight.Medium),
                                color = InkPrimary
                            )
                        }
                        Surface(
                            color = StoneSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, HairlineBorder)
                        ) {
                            Text(
                                text = status.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = InkSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (outcome.summaryLine.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(outcome.summaryLine, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                    } else if (req.responseNote.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(req.responseNote, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                    }

                    when (status) {
                        ConfirmationStatus.PENDING -> {
                            Spacer(Modifier.height(12.dp))
                            if (req.isPublicTokenRequest()) {
                                val link = CounterpartyConfirmationRules.buildConfirmationLink(req.id)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { clipboard.setText(AnnotatedString(link)) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("parties_copy_link_${req.id}"),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, InkPrimary),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = CardSurface,
                                            contentColor = InkPrimary
                                        )
                                    ) {
                                        Text("Copy link", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Button(
                                        onClick = { onOpenPublicConfirmation(req.id) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("parties_open_public_${req.id}"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = InkPrimary,
                                            contentColor = OnDarkInk
                                        )
                                    ) {
                                        Text("Open public page", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onSimulatePartyDecision(req, true) },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("simulate_confirm_${req.id}"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = CardSurface, contentColor = InkPrimary)
                                ) {
                                    Text("Simulate Confirm", style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { onSimulatePartyDecision(req, false) },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("simulate_dispute_${req.id}"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = CardSurface, contentColor = InkSecondary)
                                ) {
                                    Text("Simulate Dispute", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        ConfirmationStatus.RESPONDED, ConfirmationStatus.DECLINED -> {
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (outcome.contactVerified) NeutralSmallChip(label = "contact_verified")
                                if (outcome.workConfirmed) NeutralSmallChip(label = "counterparty_acknowledged")
                                if (outcome.paymentConfirmed) NeutralSmallChip(label = "payer_confirmed")
                                if (outcome.triggersDispute) {
                                    EvidenceBadge(status = EvidenceStatus.DISPUTED)
                                }
                            }
                            val correctedAmt = outcome.correctedAmountRupees
                            if (linkedRec != null &&
                                correctedAmt != null &&
                                outcome.workerResolution != WorkerConfirmationResolution.CORRECTION_ACCEPTED
                            ) {
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = { onAcceptClientCorrection(req, linkedRec, correctedAmt) },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("parties_accept_correction_${req.id}"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = OnDarkInk)
                                ) {
                                    Text("Accept correction (${RupeeFormatter.format(correctedAmt)})")
                                }
                            }
                            if (linkedRec != null &&
                                outcome.workerResolution != WorkerConfirmationResolution.DISPUTED_BY_WORKER
                            ) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        onDisputeClientResponse(
                                            req,
                                            linkedRec,
                                            "Worker disputed ${req.partyName}'s confirmation response."
                                        )
                                    },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("parties_dispute_response_${req.id}"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = CardSurface, contentColor = InkSecondary)
                                ) {
                                    Text("Dispute response")
                                }
                            }
                        }
                        ConfirmationStatus.CONFIRMED_SIMULATED -> {
                            Spacer(Modifier.height(8.dp))
                            NeutralSmallChip(label = "Relationship & payment updated (evidence unchanged)")
                        }
                        ConfirmationStatus.DISPUTED -> {
                            Spacer(Modifier.height(8.dp))
                            EvidenceBadge(status = EvidenceStatus.DISPUTED)
                        }
                    }
                }
            }
        }
    }
}
