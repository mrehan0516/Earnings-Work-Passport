package com.example.ui.screens

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
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.rules.PartyRules
import com.example.domain.rules.RupeeFormatter
import com.example.model.ConfirmationRequest
import com.example.model.EvidenceStatus
import com.example.model.IncomeRecord
import com.example.model.PartyRelationshipStatus
import com.example.model.PartyType
import com.example.model.PayoutCycle
import com.example.ui.components.CounterpartyConfirmationPanel
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
import com.example.ui.theme.RupeeAmountCardStyle
import com.example.ui.theme.RupeeAmountHeroStyle
import com.example.viewmodel.PassportWorkspaceData

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartyDetailScreen(
    partyId: String,
    workspace: PassportWorkspaceData,
    isBusy: Boolean,
    onSendConfirmationRequest: (IncomeRecord) -> Unit,
    onOpenPublicConfirmation: (String) -> Unit = {},
    onAcceptClientCorrection: (ConfirmationRequest, IncomeRecord, Long) -> Unit = { _, _, _ -> },
    onDisputeClientResponse: (ConfirmationRequest, IncomeRecord, String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit
) {
    val party = remember(partyId, workspace.parties) {
        workspace.parties.firstOrNull { it.id == partyId }
    }

    if (party == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("party_detail_missing"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("party_detail_back_button")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Go back",
                    tint = InkPrimary
                )
            }
            EmptyStateCard(
                title = "Party not found",
                body = "This work source, client, or payer is no longer in your active profile.",
                icon = Icons.Outlined.Badge
            )
        }
        return
    }

    val relationship = remember(party.id, workspace.relationships) {
        workspace.relationships.firstOrNull { it.partyId == party.id }
    }
    val linkedRecords = remember(party, workspace.incomeRecords) {
        PartyRules.recordsForParty(party, workspace.incomeRecords)
            .filter { !it.isSecondaryDuplicate() }
    }
    val historyEvents = remember(party, linkedRecords, workspace.confirmationRequests, workspace.auditLogs) {
        PartyRules.historyForParty(
            party = party,
            linkedRecords = linkedRecords,
            confirmations = workspace.confirmationRequests,
            auditLogs = workspace.auditLogs
        )
    }
    val requestedRecordIds = remember(workspace.confirmationRequests) {
        workspace.confirmationRequests.map { it.incomeRecordId }.toSet()
    }
    val nonDisputedTotal = remember(linkedRecords) {
        linkedRecords
            .filter { it.resolvedEvidence() != EvidenceStatus.DISPUTED }
            .sumOf { it.amountRupees }
    }
    val partyType = party.resolvedType()
    val contactStatus = party.resolvedContactStatus()
    val relationshipStatus = relationship?.resolvedRelationshipStatus() ?: PartyRelationshipStatus.WORKER_DECLARED

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("party_detail_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("party_detail_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Go back",
                        tint = InkPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = party.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = InkPrimary
                    )
                    Text(
                        text = partyType.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
            }
        }

        // Roles, Statuses & Earnings Summary Card
        item {
            PassportSectionCard(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .testTag("party_detail_overview_card")
            ) {
                Text(
                    text = "Linked Earnings (Non-Disputed)",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = RupeeFormatter.format(nonDisputedTotal),
                    style = RupeeAmountHeroStyle,
                    color = InkPrimary
                )
                Text(
                    text = "${linkedRecords.size} linked income ${if (linkedRecords.size == 1) "record" else "records"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = HairlineBorder)
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Statuses",
                    style = MaterialTheme.typography.labelLarge,
                    color = InkPrimary
                )
                Spacer(Modifier.height(6.dp))
                PartyStatusChipsRow(
                    contactStatus = contactStatus,
                    relationshipStatus = relationshipStatus
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Roles",
                    style = MaterialTheme.typography.labelLarge,
                    color = InkPrimary
                )
                Spacer(Modifier.height(6.dp))
                PartyRolePillsRow(roles = party.resolvedRoles())

                if (relationship != null) {
                    val relationMeta = listOfNotNull(
                        relationship.roleTitle.takeIf { it.isNotBlank() },
                        PayoutCycle.fromCode(relationship.payoutCycle).label,
                        relationship.startedOn.takeIf { it.isNotBlank() }?.let { "Since $it" }
                    )
                    if (relationMeta.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = relationMeta.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }
                }
            }
        }

        // Contact, Registration & Notes Card
        item {
            PassportSectionCard(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .testTag("party_detail_contact_card")
            ) {
                Text(
                    text = "Details & Contact",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary
                )
                Spacer(Modifier.height(10.dp))

                val hasAnyContact = party.phone.isNotBlank() ||
                    party.email.isNotBlank() ||
                    party.contactHint.isNotBlank()

                if (party.phone.isNotBlank()) {
                    DetailInfoRow(icon = Icons.Outlined.Phone, label = "Phone", value = party.phone)
                    Spacer(Modifier.height(8.dp))
                }
                if (party.email.isNotBlank()) {
                    DetailInfoRow(icon = Icons.Outlined.Email, label = "Email", value = party.email)
                    Spacer(Modifier.height(8.dp))
                }
                if (party.contactHint.isNotBlank() &&
                    party.contactHint != party.phone &&
                    party.contactHint != party.email
                ) {
                    DetailInfoRow(icon = Icons.Outlined.Badge, label = "Contact note", value = party.contactHint)
                    Spacer(Modifier.height(8.dp))
                }
                if (!hasAnyContact) {
                    Text(
                        text = "No phone or email saved yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (partyType.supportsRegistration && party.registrationNumber.isNotBlank()) {
                    DetailInfoRow(
                        icon = Icons.Outlined.Badge,
                        label = "Registration / GSTIN",
                        value = party.registrationNumber
                    )
                    Spacer(Modifier.height(8.dp))
                } else if (partyType == PartyType.INDIVIDUAL) {
                    Text(
                        text = "Individuals do not need a registration number.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                        modifier = Modifier.testTag("party_detail_individual_no_reg")
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (party.note.isNotBlank()) {
                    DetailInfoRow(icon = Icons.Outlined.Notes, label = "Note", value = party.note)
                }
            }
        }

        // Linked Income Records Section
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Linked Income Records (${linkedRecords.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary
                )
                Text(
                    text = "Records where this party gave you work, paid you, or both.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }
        }

        if (linkedRecords.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No income records linked yet",
                    body = "When you confirm a document or log earnings and choose ${party.name} as work giver or payer, those records will appear here.",
                    icon = Icons.Outlined.ReceiptLong,
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
        } else {
            items(linkedRecords, key = { it.id }) { rec ->
                val rolePlayed = PartyRules.roleLabelForRecord(party, rec)
                val showSplitParties = rec.workGiverPartyName.isNotBlank() &&
                    rec.payerPartyName.isNotBlank() &&
                    !rec.workGiverPartyName.equals(rec.payerPartyName, ignoreCase = true)
                val latestConfirmation = workspace.confirmationRequests.firstOrNull { it.incomeRecordId == rec.id }
                val showConfirmationPanel = latestConfirmation != null ||
                    rec.resolvedEvidence() != EvidenceStatus.SOURCE_VERIFIED_SIMULATED

                PassportSectionCard(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .testTag("party_detail_record_${rec.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = rec.earnedDate.ifBlank { "No date" },
                                style = MaterialTheme.typography.titleSmall,
                                color = InkPrimary
                            )
                            NeutralSmallChip(label = rolePlayed)
                            if (showSplitParties) {
                                Text(
                                    text = "Work giver: ${rec.workGiverPartyName} · Payer: ${rec.payerPartyName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSecondary
                                )
                            }
                            if (rec.referenceCode.isNotBlank()) {
                                Text(
                                    text = "Ref: ${rec.referenceCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSecondary
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = RupeeFormatter.format(rec.amountRupees),
                            style = RupeeAmountCardStyle,
                            color = InkPrimary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EvidenceBadge(status = rec.resolvedEvidence())
                        if (rec.simulatedApi) {
                            NeutralSmallChip(
                                label = "Simulated API",
                                modifier = Modifier.testTag("party_detail_simulated_api_${rec.id}")
                            )
                        }
                    }

                    if (showConfirmationPanel) {
                        Spacer(Modifier.height(10.dp))
                        CounterpartyConfirmationPanel(
                            record = rec,
                            latestRequest = latestConfirmation,
                            isBusy = isBusy,
                            onRequestConfirmation = onSendConfirmationRequest,
                            onOpenPublicConfirmation = onOpenPublicConfirmation,
                            onAcceptClientCorrection = onAcceptClientCorrection,
                            onDisputeClientResponse = onDisputeClientResponse
                        )
                    }
                }
            }
        }

        // History Section
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = InkPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkPrimary
                    )
                }
                Text(
                    text = "Audit trail of additions, linked records, and confirmation events for ${party.name}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }
        }

        if (historyEvents.isEmpty()) {
            item {
                PassportSectionCard(modifier = Modifier.widthIn(max = 640.dp)) {
                    Text(
                        text = "Added by worker as ${contactStatus.label} · ${relationshipStatus.label}. Future confirmations and linked records will be recorded here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
            }
        } else {
            items(historyEvents, key = { it.id }) { event ->
                PassportSectionCard(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .testTag("party_history_item_${event.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = InkPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        NeutralSmallChip(label = event.actorLabel)
                    }
                    if (event.detail.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = event.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = InkSecondary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = InkSecondary)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = InkPrimary)
        }
    }
}
