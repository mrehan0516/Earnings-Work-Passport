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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GroupAdd
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
import com.example.domain.rules.PartyRules
import com.example.model.Party
import com.example.model.PartyContactStatus
import com.example.model.PartyRelationshipStatus
import com.example.model.PartyRole
import com.example.model.PartyType
import com.example.model.PayoutCycle
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.OnDarkInk
import com.example.ui.theme.StoneSurface

/**
 * Reusable "Add work source / client / payer" form used in PartiesScreen, AddSourceScreen,
 * and inline quick-add inside ReviewConfirmScreen.
 *
 * Guardrails:
 * - Types: Individual, Business, Platform, Payment provider, Other
 * - Roles: Who gives me work, Who pays me (allow either or both)
 * - Optional registration/GSTIN shown ONLY for Business, Platform, Payment provider (never Individual)
 * - Live similar-name warning with one-tap "Use existing"
 * - New parties always start as added_by_worker + worker_declared (never "verified")
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddPartyFormCard(
    existingParties: List<Party>,
    isBusy: Boolean,
    initialName: String = "",
    initialRoles: Set<PartyRole> = setOf(PartyRole.WORK_GIVER, PartyRole.PAYER),
    compactMode: Boolean = false,
    heading: String = "Add Work Source / Client / Payer",
    submitLabel: String = "Save Work Source",
    onSubmit: (
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
    onUseExistingParty: ((Party) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var displayName by remember(initialName) { mutableStateOf(initialName) }
    var selectedType by remember { mutableStateOf(PartyType.PLATFORM) }
    var givesWork by remember(initialRoles) { mutableStateOf(PartyRole.WORK_GIVER in initialRoles) }
    var paysMe by remember(initialRoles) { mutableStateOf(PartyRole.PAYER in initialRoles) }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var registrationNumber by remember { mutableStateOf("") }
    var roleTitle by remember { mutableStateOf("") }
    var selectedCycle by remember { mutableStateOf(PayoutCycle.WEEKLY) }
    var startedOn by remember { mutableStateOf("2026-11-01") }

    val selectedRoles = buildList {
        if (givesWork) add(PartyRole.WORK_GIVER)
        if (paysMe) add(PartyRole.PAYER)
    }
    val similarMatches = remember(displayName, existingParties) {
        PartyRules.findSimilarParties(displayName, existingParties)
    }

    PassportSectionCard(modifier = modifier.testTag("add_party_form_card")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.GroupAdd, contentDescription = null, tint = InkPrimary)
            Text(
                text = heading,
                style = MaterialTheme.typography.titleMedium,
                color = InkPrimary
            )
        }
        Spacer(Modifier.height(8.dp))

        PartyStatusChipsRow(
            contactStatus = PartyContactStatus.ADDED_BY_WORKER,
            relationshipStatus = PartyRelationshipStatus.WORKER_DECLARED
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display name") },
            placeholder = { Text("e.g. Zomato, Sharma Family, Razorpay") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_party_name_input")
        )

        if (similarMatches.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SimilarPartyWarningCard(
                matches = similarMatches,
                onUseExisting = { existing ->
                    onUseExistingParty?.invoke(existing)
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("Party type", style = MaterialTheme.typography.labelLarge, color = InkPrimary)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PartyType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = {
                        selectedType = type
                        if (!type.supportsRegistration) {
                            registrationNumber = ""
                        }
                    },
                    label = { Text(type.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = InkPrimary,
                        selectedLabelColor = OnDarkInk,
                        containerColor = StoneSurface,
                        labelColor = InkPrimary
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("party_type_${type.code.lowercase()}")
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Roles (choose one or both)",
            style = MaterialTheme.typography.labelLarge,
            color = InkPrimary
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = givesWork,
                onClick = { givesWork = !givesWork },
                label = { Text(PartyRole.WORK_GIVER.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = InkPrimary,
                    selectedLabelColor = OnDarkInk,
                    containerColor = StoneSurface,
                    labelColor = InkPrimary
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("party_role_work_giver")
            )
            FilterChip(
                selected = paysMe,
                onClick = { paysMe = !paysMe },
                label = { Text(PartyRole.PAYER.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = InkPrimary,
                    selectedLabelColor = OnDarkInk,
                    containerColor = StoneSurface,
                    labelColor = InkPrimary
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("party_role_payer")
            )
        }
        if (selectedRoles.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Select at least one role (Who gives me work or Who pays me).",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .weight(1f)
                    .testTag("add_party_phone_input")
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .weight(1f)
                    .testTag("add_party_email_input")
            )
        }

        // Individuals do not need a registration number; show optional Registration/GSTIN only for non-individuals
        Spacer(Modifier.height(10.dp))
        if (selectedType.supportsRegistration) {
            OutlinedTextField(
                value = registrationNumber,
                onValueChange = { registrationNumber = it },
                label = { Text("Registration / GSTIN (optional)") },
                placeholder = { Text("Optional for ${selectedType.label.lowercase()}") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_party_registration_input")
            )
        } else {
            Text(
                text = "Individuals do not need a registration number.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
                modifier = Modifier.testTag("individual_no_reg_note")
            )
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (optional)") },
            placeholder = { Text("e.g. Pays weekly via UPI or settles invoices") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_party_note_input")
        )

        if (!compactMode) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = roleTitle,
                onValueChange = { roleTitle = it },
                label = { Text("Your work title (optional)") },
                placeholder = { Text("e.g. Delivery Partner, Math Tutor, Designer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Text("Payout schedule", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PayoutCycle.entries.forEach { cycle ->
                    FilterChip(
                        selected = selectedCycle == cycle,
                        onClick = { selectedCycle = cycle },
                        label = { Text(cycle.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = InkPrimary,
                            selectedLabelColor = OnDarkInk,
                            containerColor = StoneSurface,
                            labelColor = InkPrimary
                        )
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = startedOn,
                onValueChange = { startedOn = it },
                label = { Text("Started on (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSubmit(
                        displayName.trim(),
                        selectedType,
                        selectedRoles,
                        phone.trim(),
                        email.trim(),
                        note.trim(),
                        PartyRules.sanitizeRegistrationNumber(selectedType, registrationNumber),
                        roleTitle.trim(),
                        selectedCycle,
                        startedOn.trim()
                    )
                    if (!compactMode) {
                        displayName = ""
                        phone = ""
                        email = ""
                        note = ""
                        registrationNumber = ""
                        roleTitle = ""
                    }
                },
                enabled = !isBusy && displayName.isNotBlank() && selectedRoles.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_party_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = OnDarkInk)
            ) {
                Text(submitLabel, style = MaterialTheme.typography.labelLarge)
            }

            if (onCancel != null) {
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
                    Text("Cancel")
                }
            }
        }
    }
}
