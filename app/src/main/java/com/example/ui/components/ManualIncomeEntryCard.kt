package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.rules.DocumentExtractionValidator
import com.example.domain.rules.RupeeFormatter
import com.example.model.EvidenceStatus
import com.example.model.Party
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.OnDarkInk
import com.example.ui.theme.StoneSurface

/**
 * Inline manual income entry card (no document attached), saved strictly as Self-declared.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualIncomeEntryCard(
    parties: List<Party>,
    isBusy: Boolean,
    onSaveSelfDeclared: (
        partyId: String,
        partyName: String,
        amountRupees: Long,
        earnedDate: String,
        periodLabel: String,
        referenceCode: String,
        notes: String
    ) -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedPartyId by remember { mutableStateOf("") }
    var partyName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var earnedDate by remember { mutableStateOf("2026-11-16") }
    var periodLabel by remember { mutableStateOf("") }
    var referenceCode by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val parsedAmount = amountText.toLongOrNull() ?: 0L
    val isDateValid = DocumentExtractionValidator.isValidStrictIsoDate(earnedDate)

    PassportSectionCard(modifier = modifier.testTag("manual_income_entry_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.EditNote,
                    contentDescription = null,
                    tint = InkPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Add Income Manually",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary
                )
            }
            EvidenceBadge(status = EvidenceStatus.SELF_DECLARED)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "No document attached. This entry is saved with the Self-declared grey label until matched to a bank credit or confirmed by a source.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary
        )

        if (parties.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Quick pick a saved source",
                style = MaterialTheme.typography.labelMedium,
                color = InkSecondary
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                parties.forEach { party ->
                    val selected = selectedPartyId == party.id
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedPartyId = party.id
                            partyName = party.name
                        },
                        label = { Text(party.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = InkPrimary,
                            selectedLabelColor = OnDarkInk,
                            containerColor = StoneSurface,
                            labelColor = InkPrimary
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = partyName,
            onValueChange = {
                partyName = it
                selectedPartyId = parties.firstOrNull { p -> p.name.equals(it.trim(), ignoreCase = true) }?.id ?: ""
            },
            label = { Text("Platform, payer, or client name") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("manual_party_input")
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Amount in Rupees (Rs)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("manual_amount_input")
        )
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = earnedDate,
                onValueChange = { earnedDate = it },
                label = { Text("Date (YYYY-MM-DD)") },
                isError = earnedDate.isNotBlank() && !isDateValid,
                supportingText = {
                    if (earnedDate.isNotBlank() && !isDateValid) {
                        Text("Use YYYY-MM-DD")
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("manual_date_input")
            )
            OutlinedTextField(
                value = periodLabel,
                onValueChange = { periodLabel = it },
                label = { Text("Period (optional)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = referenceCode,
            onValueChange = { referenceCode = it },
            label = { Text("Reference / Cash note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                onSaveSelfDeclared(
                    selectedPartyId,
                    partyName.trim(),
                    parsedAmount,
                    earnedDate.trim(),
                    periodLabel.trim(),
                    referenceCode.trim(),
                    notes.trim().ifBlank { "Added manually without document." }
                )
            },
            enabled = !isBusy && partyName.isNotBlank() && parsedAmount > 0L && isDateValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("review_save_manual_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = OnDarkInk)
        ) {
            Text(
                text = if (parsedAmount > 0L) {
                    "Save as Self-declared (${RupeeFormatter.format(parsedAmount)})"
                } else {
                    "Save as Self-declared"
                },
                style = MaterialTheme.typography.labelLarge
            )
        }

        if (onCancel != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCancel,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CardSurface,
                    contentColor = InkSecondary
                )
            ) {
                Spacer(Modifier.width(4.dp))
                Text("Back to Document Review")
            }
        }
    }
}
