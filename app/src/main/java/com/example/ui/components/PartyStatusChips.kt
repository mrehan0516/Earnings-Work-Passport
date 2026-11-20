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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.rules.SimilarPartyMatch
import com.example.model.Party
import com.example.model.PartyContactStatus
import com.example.model.PartyRelationshipStatus
import com.example.model.PartyRole
import com.example.ui.theme.CardSurface
import com.example.ui.theme.EvidenceDocExtractedBg
import com.example.ui.theme.EvidenceDocExtractedInk
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.StoneSurface

/**
 * Renders Contact status and Relationship status as two separate small neutral stone chips,
 * keeping color strictly reserved for the 5 evidence labels and never labeling a party "verified".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartyStatusChipsRow(
    contactStatus: PartyContactStatus,
    relationshipStatus: PartyRelationshipStatus,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        NeutralSmallChip(
            label = "Contact: ${contactStatus.label}",
            modifier = Modifier.testTag("chip_contact_status")
        )
        NeutralSmallChip(
            label = "Relationship: ${relationshipStatus.label}",
            modifier = Modifier.testTag("chip_relationship_status")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartyRolePillsRow(
    roles: List<PartyRole>,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        roles.forEach { role ->
            NeutralSmallChip(label = role.shortLabel)
        }
    }
}

@Composable
fun NeutralSmallChip(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = StoneSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = InkSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Calm warning card shown when a typed party name looks similar to one or more existing parties,
 * letting the worker link the existing party in one tap to avoid duplicates.
 */
@Composable
fun SimilarPartyWarningCard(
    matches: List<SimilarPartyMatch>,
    onUseExisting: (Party) -> Unit,
    modifier: Modifier = Modifier
) {
    if (matches.isEmpty()) return

    Surface(
        color = EvidenceDocExtractedBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, EvidenceDocExtractedInk.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("similar_party_warning_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = EvidenceDocExtractedInk,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Looks similar to an existing party",
                    style = MaterialTheme.typography.labelLarge,
                    color = EvidenceDocExtractedInk
                )
            }
            matches.forEach { match ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = match.party.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = InkPrimary
                        )
                        Text(
                            text = "${match.party.resolvedType().label} · ${match.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = EvidenceDocExtractedInk
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { onUseExisting(match.party) },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("use_existing_party_${match.party.id}"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, EvidenceDocExtractedInk),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardSurface,
                            contentColor = InkPrimary
                        )
                    ) {
                        Text("Use existing", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
