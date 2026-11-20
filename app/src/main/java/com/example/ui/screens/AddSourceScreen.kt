package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.rules.DemoDataFactory
import com.example.domain.rules.RupeeFormatter
import com.example.domain.rules.SourceUploadType
import com.example.domain.rules.SyntheticSampleBankCsv
import com.example.domain.rules.SyntheticSampleDocument
import com.example.model.EvidenceStatus
import com.example.model.PartyRole
import com.example.model.PartyType
import com.example.model.PayoutCycle
import com.example.ui.components.AddPartyFormCard
import com.example.ui.components.EvidenceBadge
import com.example.ui.components.PassportSectionCard
import com.example.ui.theme.CardSurface
import com.example.ui.theme.EvidenceDisputedBg
import com.example.ui.theme.EvidenceDisputedInk
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.OnDarkInk
import com.example.ui.theme.RupeeAmountCompactStyle
import com.example.ui.theme.StoneSurface
import com.example.viewmodel.ExtractionUiState
import com.example.viewmodel.PassportWorkspaceData

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSourceScreen(
    workspace: PassportWorkspaceData,
    extractionState: ExtractionUiState,
    isBusy: Boolean,
    onUploadFile: (Uri, SourceUploadType) -> Unit,
    onReadSampleWithAi: (SyntheticSampleDocument) -> Unit,
    onImportSampleBankCsv: (SyntheticSampleBankCsv) -> Unit,
    onRetryIngestion: () -> Unit,
    onDismissExtractionState: () -> Unit,
    onNavigateToIssues: () -> Unit,
    onOpenPartyDetail: (String) -> Unit,
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
    onAddSelfDeclared: (partyId: String, partyName: String, amount: Long, date: String, period: String, ref: String, notes: String) -> Unit,
    onAddBankCredit: (bankLabel: String, date: String, amount: Long, narration: String, utr: String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedSourceTypeCode by rememberSaveable { mutableStateOf(SourceUploadType.PLATFORM_STATEMENT.code) }
    val selectedSourceType = SourceUploadType.fromCode(selectedSourceTypeCode)

    val tabs = listOf("Upload File", "Work Source", "Manual / Bank")

    // Zero-permission Storage Access Framework picker supporting PDF, CSV, and images
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onUploadFile(uri, selectedSourceType)
        }
    }

    // Zero-permission Android Photo Picker for quick image upload (Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onUploadFile(uri, selectedSourceType)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("add_source_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Add Source",
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkPrimary
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    tabs.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                            onClick = { selectedTab = index },
                            selected = selectedTab == index,
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = InkPrimary,
                                activeContentColor = OnDarkInk,
                                inactiveContainerColor = CardSurface,
                                inactiveContentColor = InkPrimary
                            ),
                            modifier = Modifier.testTag("add_tab_$index")
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> item {
                DocumentAndCsvUploadSection(
                    selectedSourceType = selectedSourceType,
                    onSelectSourceType = { type ->
                        selectedSourceTypeCode = type.code
                        if (extractionState is ExtractionUiState.Error || extractionState is ExtractionUiState.CsvImportSuccess) {
                            onDismissExtractionState()
                        }
                    },
                    extractionState = extractionState,
                    onPickFile = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    onPickPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPickSampleDoc = onReadSampleWithAi,
                    onPickSampleCsv = onImportSampleBankCsv,
                    onRetry = onRetryIngestion,
                    onDismissState = onDismissExtractionState,
                    onNavigateToIssues = onNavigateToIssues,
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
            1 -> item {
                AddPartyFormCard(
                    existingParties = workspace.parties,
                    isBusy = isBusy,
                    heading = "Add work source / client / payer",
                    submitLabel = "Save Work Source",
                    onSubmit = onAddParty,
                    onUseExistingParty = { existing -> onOpenPartyDetail(existing.id) },
                    modifier = Modifier.widthIn(max = 640.dp)
                )
            }
            2 -> {
                item {
                    AddSelfDeclaredFormCard(
                        workspace = workspace,
                        isBusy = isBusy,
                        onSubmit = onAddSelfDeclared,
                        modifier = Modifier.widthIn(max = 640.dp)
                    )
                }
                item {
                    AddBankCreditFormCard(
                        isBusy = isBusy,
                        onSubmit = onAddBankCredit,
                        modifier = Modifier.widthIn(max = 640.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentAndCsvUploadSection(
    selectedSourceType: SourceUploadType,
    onSelectSourceType: (SourceUploadType) -> Unit,
    extractionState: ExtractionUiState,
    onPickFile: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickSampleDoc: (SyntheticSampleDocument) -> Unit,
    onPickSampleCsv: (SyntheticSampleBankCsv) -> Unit,
    onRetry: () -> Unit,
    onDismissState: () -> Unit,
    onNavigateToIssues: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLoading = extractionState is ExtractionUiState.Reading
    val isBankCsvMode = selectedSourceType == SourceUploadType.BANK_STATEMENT_CSV

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PassportSectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isBankCsvMode) Icons.Outlined.TableChart else Icons.Outlined.UploadFile,
                    contentDescription = null,
                    tint = InkPrimary
                )
                Text(
                    text = "1. Choose Source Type",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary
                )
            }
            Spacer(Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SourceUploadType.entries.forEach { sourceType ->
                    FilterChip(
                        selected = selectedSourceType == sourceType,
                        onClick = { onSelectSourceType(sourceType) },
                        enabled = !isLoading,
                        label = {
                            Text(
                                text = sourceType.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = InkPrimary,
                            selectedLabelColor = OnDarkInk,
                            containerColor = StoneSurface,
                            labelColor = InkPrimary
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("source_type_${sourceType.code.lowercase()}")
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = selectedSourceType.helperText,
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )
            Spacer(Modifier.height(8.dp))

            if (isBankCsvMode) {
                Surface(
                    color = StoneSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Text(
                        text = "Parsed in code (no AI) into bank_credits · Max 10 MB · Duplicate files blocked by hash",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EvidenceBadge(status = EvidenceStatus.DOCUMENT_EXTRACTED)
                    Text(
                        text = "PDF or image · Max 10 MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSecondary
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Loading state
            if (extractionState is ExtractionUiState.Reading) {
                Surface(
                    color = StoneSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upload_loading_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = InkPrimary,
                            strokeWidth = 2.dp
                        )
                        Column {
                            Text(
                                text = extractionState.stageLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = InkPrimary
                            )
                            Text(
                                text = extractionState.sourceTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Error state with Retry + Dismiss
            if (extractionState is ExtractionUiState.Error) {
                Surface(
                    color = EvidenceDisputedBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EvidenceDisputedInk.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("upload_error_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = EvidenceDisputedInk,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (extractionState.isDuplicateFile) "Duplicate File Detected" else "Could Not Process File",
                                style = MaterialTheme.typography.titleSmall,
                                color = EvidenceDisputedInk
                            )
                        }
                        Text(
                            text = extractionState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = EvidenceDisputedInk
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (extractionState.canRetry) {
                                Button(
                                    onClick = onRetry,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("upload_retry_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = InkPrimary,
                                        contentColor = OnDarkInk
                                    )
                                ) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Retry", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            OutlinedButton(
                                onClick = onDismissState,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("upload_dismiss_error_button"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, EvidenceDisputedInk)
                            ) {
                                Text("Dismiss", color = EvidenceDisputedInk, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // Bank CSV Import Success state
            if (extractionState is ExtractionUiState.CsvImportSuccess) {
                Surface(
                    color = StoneSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("csv_import_success_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = InkPrimary)
                            Text(
                                text = "Imported ${extractionState.importedCount} Bank Credit(s)",
                                style = MaterialTheme.typography.titleSmall,
                                color = InkPrimary
                            )
                        }
                        Text(
                            text = "${extractionState.fileName} · Total ${RupeeFormatter.format(extractionState.totalRupees)} parsed in code (no AI). Skipped ${extractionState.skippedCount} debit/empty row(s).",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary
                        )
                        if (extractionState.rowErrorCount > 0) {
                            Text(
                                text = "${extractionState.rowErrorCount} row(s) had invalid dates or amounts and were skipped without guessing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EvidenceDisputedInk
                            )
                        }
                        HorizontalDivider(color = HairlineBorder)
                        extractionState.previewCredits.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = row.descriptor,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = row.date,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkSecondary
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = RupeeFormatter.format(row.amountRupees),
                                    style = RupeeAmountCompactStyle,
                                    color = InkPrimary
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onNavigateToIssues,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("csv_open_issues_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = OnDarkInk)
                            ) {
                                Text("Check Rule Matches", style = MaterialTheme.typography.labelLarge)
                            }
                            OutlinedButton(
                                onClick = onDismissState,
                                modifier = Modifier.height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, HairlineBorder)
                            ) {
                                Text("Done", color = InkPrimary)
                            }
                        }
                    }
                }
            }

            // Primary Upload File CTA (PDF, CSV, or Image)
            Button(
                onClick = onPickFile,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("upload_source_file_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = InkPrimary,
                    contentColor = OnDarkInk
                )
            ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isBankCsvMode) "Upload Bank Statement CSV" else "Upload PDF, CSV, or Image",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (!isBankCsvMode) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onPickPhoto,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pick_document_photo_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, InkPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardSurface,
                        contentColor = InkPrimary
                    )
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Or Choose Photo from Gallery", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Synthetic Bank Statement CSV one-tap test card
        PassportSectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.TableChart, contentDescription = null, tint = InkPrimary)
                Text(
                    text = "Try Synthetic Bank Statement (CSV)",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Parses date, amount, and descriptor purely in code (no AI) into bank_credits. Tapping twice demonstrates the duplicate file hash check.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                color = StoneSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = DemoDataFactory.sampleBankCsv.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = InkPrimary
                    )
                    Text(
                        text = DemoDataFactory.sampleBankCsv.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onPickSampleCsv(DemoDataFactory.sampleBankCsv) },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("import_sample_bank_csv_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, InkPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardSurface,
                            contentColor = InkPrimary
                        )
                    ) {
                        Text("Import Synthetic CSV (No AI)", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Built-in Synthetic Income Documents for Gemini AI + Code Validation
        PassportSectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.Description, contentDescription = null, tint = InkPrimary)
                Text(
                    text = "Or Try Synthetic Income Documents (AI)",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tap any synthetic sample to run Gemini JSON extraction, code validation (including math check), and duplicate hash protection.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary
            )
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DemoDataFactory.sampleDocuments.forEach { sample ->
                    Surface(
                        color = StoneSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = sample.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = InkPrimary
                            )
                            Text(
                                text = sample.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { onPickSampleDoc(sample) },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("read_sample_${sample.id}"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, InkPrimary),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = CardSurface,
                                    contentColor = InkPrimary
                                )
                            ) {
                                Text("Read with Gemini AI", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddSelfDeclaredFormCard(
    workspace: PassportWorkspaceData,
    isBusy: Boolean,
    onSubmit: (partyId: String, partyName: String, amount: Long, date: String, period: String, ref: String, notes: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPartyId by rememberSaveable { mutableStateOf("") }
    var partyName by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var earnedDate by rememberSaveable { mutableStateOf("2026-11-15") }
    var periodLabel by rememberSaveable { mutableStateOf("Nov 2026") }
    var referenceCode by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

    PassportSectionCard(modifier = modifier) {
        Text(
            text = "Log Self-Declared Earnings",
            style = MaterialTheme.typography.titleMedium,
            color = InkPrimary
        )
        Spacer(Modifier.height(6.dp))
        EvidenceBadge(status = EvidenceStatus.SELF_DECLARED)
        Spacer(Modifier.height(10.dp))

        if (workspace.parties.isNotEmpty()) {
            Text("Pick an existing source (optional)", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                workspace.parties.forEach { party ->
                    FilterChip(
                        selected = selectedPartyId == party.id,
                        onClick = {
                            selectedPartyId = party.id
                            partyName = party.name
                        },
                        label = { Text(party.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = InkPrimary,
                            selectedLabelColor = OnDarkInk
                        )
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = partyName,
            onValueChange = {
                partyName = it
                if (workspace.parties.none { p -> p.id == selectedPartyId && p.name == it }) {
                    selectedPartyId = ""
                }
            },
            label = { Text("Who paid you") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("self_declared_party_input")
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Amount (Rs)") },
            placeholder = { Text("e.g. 5400") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("self_declared_amount_input")
        )
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = earnedDate,
                onValueChange = { earnedDate = it },
                label = { Text("Date (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = periodLabel,
                onValueChange = { periodLabel = it },
                label = { Text("Period") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = referenceCode,
            onValueChange = { referenceCode = it },
            label = { Text("Payment ref / UPI ID (optional)") },
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

        val parsedAmount = amountText.toLongOrNull() ?: 0L
        Button(
            onClick = {
                onSubmit(selectedPartyId, partyName, parsedAmount, earnedDate, periodLabel, referenceCode, notes)
                amountText = ""
                referenceCode = ""
                notes = ""
            },
            enabled = !isBusy && partyName.isNotBlank() && parsedAmount > 0L,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_self_declared_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = OnDarkInk)
        ) {
            Text("Save as Self-declared", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AddBankCreditFormCard(
    isBusy: Boolean,
    onSubmit: (bankLabel: String, date: String, amount: Long, narration: String, utr: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var bankLabel by rememberSaveable { mutableStateOf("SBI Savings ••4821") }
    var creditDate by rememberSaveable { mutableStateOf("2026-11-16") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var narration by rememberSaveable { mutableStateOf("") }
    var utrReference by rememberSaveable { mutableStateOf("") }

    PassportSectionCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.AccountBalance, contentDescription = null, tint = InkPrimary)
            Text(
                text = "Add Synthetic Bank Credit (Manual)",
                style = MaterialTheme.typography.titleMedium,
                color = InkPrimary
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Rules compare bank credits against your confirmed income records by amount, date window, and reference code.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = bankLabel,
            onValueChange = { bankLabel = it },
            label = { Text("Bank account label (masked)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Credit (Rs)") },
                placeholder = { Text("14250") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .testTag("bank_credit_amount_input")
            )
            OutlinedTextField(
                value = creditDate,
                onValueChange = { creditDate = it },
                label = { Text("Credit date") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = narration,
            onValueChange = { narration = it },
            label = { Text("Bank descriptor / narration") },
            placeholder = { Text("e.g. IMPS/SWIGGY PAYOUT/SWG2611164821") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = utrReference,
            onValueChange = { utrReference = it },
            label = { Text("UTR / IMPS Reference") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))

        val parsedAmount = amountText.toLongOrNull() ?: 0L
        OutlinedButton(
            onClick = {
                onSubmit(bankLabel, creditDate, parsedAmount, narration, utrReference)
                amountText = ""
                narration = ""
                utrReference = ""
            },
            enabled = !isBusy && parsedAmount > 0L,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_bank_credit_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, InkPrimary),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = CardSurface, contentColor = InkPrimary)
        ) {
            Text("Add Bank Credit", style = MaterialTheme.typography.labelLarge)
        }
    }
}
