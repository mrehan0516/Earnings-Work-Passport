package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PublicClaimLoadResult
import com.example.data.PublicConfirmationClaim
import com.example.data.PublicConfirmationRepository
import com.example.domain.rules.CounterpartyAnswerChoice
import com.example.domain.rules.CounterpartyConfirmationRules
import com.example.domain.rules.DemoOtpState
import com.example.domain.rules.OtpVerifyOutcome
import com.example.domain.rules.ParsedCounterpartyResponses
import com.example.domain.rules.RupeeFormatter
import com.example.ui.components.NeutralSmallChip
import com.example.ui.components.PassportSectionCard
import com.example.ui.components.TrustFooterBanner
import com.example.ui.theme.CardSurface
import com.example.ui.theme.EvidenceDisputedBg
import com.example.ui.theme.EvidenceDisputedInk
import com.example.ui.theme.EvidenceSourceMatchedBg
import com.example.ui.theme.EvidenceSourceMatchedInk
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.OnDarkInk
import com.example.ui.theme.PaperCanvas
import com.example.ui.theme.RupeeAmountHeroStyle
import com.example.ui.theme.StoneSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface PublicConfirmationUiState {
    data object Loading : PublicConfirmationUiState
    data class Loaded(val claim: PublicConfirmationClaim) : PublicConfirmationUiState
    data class Unavailable(val message: String) : PublicConfirmationUiState
    data class Error(val message: String) : PublicConfirmationUiState
    data class Submitted(
        val claim: PublicConfirmationClaim,
        val submittedResponses: ParsedCounterpartyResponses,
        val submittedAtEpochMs: Long
    ) : PublicConfirmationUiState
}

/**
 * Public Counterparty Confirmation page (NO sign-in required).
 *
 * Security & privacy scope:
 * - Displays ONLY who is asking, the work description, the amount, and dates for THIS single record.
 * - Never fetches or exposes any of the worker's other data.
 * - Gates answering behind an on-screen DEMO OTP step ("DEMO OTP, not a real SMS",
 *   5-minute expiry, 3 wrong attempts -> 5-minute lockout) with explicit disclaimer.
 * - Lets the counterparty answer each of the 3 questions independently (allowing partial answers,
 *   corrected amount, or declining) and records timestamp + claimVersionShown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublicConfirmationScreen(
    token: String,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { PublicConfirmationRepository(context) }

    var uiState by remember(token) { mutableStateOf<PublicConfirmationUiState>(PublicConfirmationUiState.Loading) }
    var loadTrigger by remember(token) { mutableStateOf(0) }

    // DEMO OTP state
    var otpState by remember(token) { mutableStateOf<DemoOtpState?>(null) }
    var otpInput by remember(token) { mutableStateOf("") }
    var otpFeedback by remember(token) { mutableStateOf<String?>(null) }
    var nowEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Independent answers for Q1, Q2, Q3
    var q1WorkDelivered by remember(token) { mutableStateOf<CounterpartyAnswerChoice?>(null) }
    var q2AmountCorrect by remember(token) { mutableStateOf<CounterpartyAnswerChoice?>(null) }
    var q2CorrectedAmountInput by remember(token) { mutableStateOf("") }
    var q3PaidStatus by remember(token) { mutableStateOf<CounterpartyAnswerChoice?>(null) }
    var q3PaidDateInput by remember(token) { mutableStateOf("") }
    var submitError by remember(token) { mutableStateOf<String?>(null) }
    var isSubmitting by remember(token) { mutableStateOf(false) }

    LaunchedEffect(token, loadTrigger) {
        uiState = PublicConfirmationUiState.Loading
        when (val res = repository.fetchPendingClaim(token)) {
            is PublicClaimLoadResult.Loaded -> {
                val now = System.currentTimeMillis()
                nowEpochMs = now
                otpState = DemoOtpState(
                    activeCode = res.claim.demoOtpCode,
                    issuedAtEpochMs = now,
                    failedAttemptsInWindow = 0,
                    totalFailedAttempts = res.claim.existingOtpAttempts,
                    lockedUntilEpochMs = res.claim.existingOtpLockedUntilEpochMs,
                    verifiedAtEpochMs = null
                )
                uiState = PublicConfirmationUiState.Loaded(res.claim)
            }
            is PublicClaimLoadResult.InvalidOrUnavailable -> {
                uiState = PublicConfirmationUiState.Unavailable(res.message)
            }
            is PublicClaimLoadResult.Error -> {
                uiState = PublicConfirmationUiState.Error(res.message)
            }
        }
    }

    // 1-second live clock while on DEMO OTP step
    LaunchedEffect(uiState, otpState?.verifiedAtEpochMs) {
        if (uiState is PublicConfirmationUiState.Loaded && otpState?.isVerified() != true) {
            while (true) {
                nowEpochMs = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperCanvas)
            .safeDrawingPadding()
            .testTag("public_confirmation_screen")
    ) {
        // Top header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("public_confirmation_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Close public confirmation page",
                        tint = InkPrimary
                    )
                }
                Column {
                    Text(
                        text = "Record Confirmation",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkPrimary
                    )
                    Text(
                        text = "Public link · No sign-in required",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
            }
            NeutralSmallChip(label = "Single Record")
        }

        HorizontalDivider(color = HairlineBorder)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val state = uiState) {
                is PublicConfirmationUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = InkPrimary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Opening confirmation request…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSecondary
                        )
                    }
                }

                is PublicConfirmationUiState.Unavailable -> {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 580.dp)
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PassportSectionCard(modifier = Modifier.testTag("public_confirmation_unavailable_card")) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = InkPrimary
                                )
                                Text(
                                    text = "Confirmation link unavailable",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkPrimary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSecondary
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = onClose,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("public_unavailable_close_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = InkPrimary,
                                    contentColor = OnDarkInk
                                )
                            ) {
                                Text("Close")
                            }
                        }
                    }
                }

                is PublicConfirmationUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 580.dp)
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PassportSectionCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = EvidenceDisputedInk
                                )
                                Text(
                                    text = "Could not load confirmation",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkPrimary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSecondary
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { loadTrigger += 1 },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("public_error_retry_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = InkPrimary,
                                        contentColor = OnDarkInk
                                    )
                                ) {
                                    Text("Retry")
                                }
                                OutlinedButton(
                                    onClick = onClose,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = CardSurface,
                                        contentColor = InkPrimary
                                    )
                                ) {
                                    Text("Close")
                                }
                            }
                        }
                    }
                }

                is PublicConfirmationUiState.Submitted -> {
                    SubmittedReceiptContent(
                        claim = state.claim,
                        responses = state.submittedResponses,
                        submittedAtEpochMs = state.submittedAtEpochMs,
                        onClose = onClose
                    )
                }

                is PublicConfirmationUiState.Loaded -> {
                    val claim = state.claim
                    val currentOtp = otpState
                    Column(
                        modifier = Modifier
                            .widthIn(max = 620.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Scoped Single-Record Claim Card (ONLY who is asking, work, amount, dates)
                        ScopedClaimDetailsCard(claim = claim)

                        // 2. Step 1 (DEMO OTP) OR Step 2 (3 Independent Questions)
                        if (currentOtp != null && !currentOtp.isVerified()) {
                            DemoOtpVerificationCard(
                                otpState = currentOtp,
                                nowEpochMs = nowEpochMs,
                                otpInput = otpInput,
                                feedback = otpFeedback,
                                onOtpInputChange = { raw ->
                                    otpInput = raw.filter { it.isDigit() }.take(6)
                                    otpFeedback = null
                                },
                                onVerifyClick = {
                                    val now = System.currentTimeMillis()
                                    nowEpochMs = now
                                    val (nextState, outcome) = CounterpartyConfirmationRules.verifyDemoOtp(
                                        state = currentOtp,
                                        enteredCode = otpInput,
                                        nowEpochMs = now
                                    )
                                    otpState = nextState
                                    when (outcome) {
                                        is OtpVerifyOutcome.Verified -> {
                                            otpFeedback = null
                                        }
                                        is OtpVerifyOutcome.WrongCode -> {
                                            otpInput = ""
                                            otpFeedback = "Incorrect DEMO OTP. ${outcome.attemptsRemaining} attempt(s) left before 5-minute lock."
                                            scope.launch {
                                                repository.recordOtpAttempt(
                                                    token = claim.token,
                                                    totalAttempts = nextState.totalFailedAttempts,
                                                    lockedUntilEpochMs = nextState.lockedUntilEpochMs
                                                )
                                            }
                                        }
                                        is OtpVerifyOutcome.Locked -> {
                                            otpInput = ""
                                            otpFeedback = "Too many wrong attempts (3/3). Locked for ${CounterpartyConfirmationRules.formatCountdown(outcome.remainingSeconds)}."
                                            scope.launch {
                                                repository.recordOtpAttempt(
                                                    token = claim.token,
                                                    totalAttempts = nextState.totalFailedAttempts,
                                                    lockedUntilEpochMs = nextState.lockedUntilEpochMs
                                                )
                                            }
                                        }
                                        is OtpVerifyOutcome.Expired -> {
                                            otpFeedback = "This DEMO OTP expired after 5 minutes. Generate a new code below."
                                        }
                                    }
                                },
                                onRegenerateClick = {
                                    val now = System.currentTimeMillis()
                                    nowEpochMs = now
                                    otpState = CounterpartyConfirmationRules.regenerateDemoOtp(
                                        state = currentOtp,
                                        newCode = CounterpartyConfirmationRules.generateDemoOtpCode(),
                                        nowEpochMs = now
                                    )
                                    otpInput = ""
                                    otpFeedback = "Generated a fresh 6-digit DEMO OTP (valid 5 minutes)."
                                }
                            )
                        } else if (currentOtp != null && currentOtp.isVerified()) {
                            // OTP Verified Banner
                            Surface(
                                color = EvidenceSourceMatchedBg,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, HairlineBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("public_otp_verified_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.VerifiedUser,
                                        contentDescription = null,
                                        tint = EvidenceSourceMatchedInk,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "DEMO OTP verified",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = EvidenceSourceMatchedInk
                                        )
                                        Text(
                                            text = CounterpartyConfirmationRules.OTP_DISCLAIMER_TEXT,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = InkPrimary
                                        )
                                    }
                                }
                            }

                            // 3 Independent Question Cards
                            QuestionChoiceCard(
                                questionNumber = 1,
                                questionTitle = "Was the work delivered?",
                                choices = listOf(
                                    CounterpartyAnswerChoice.YES,
                                    CounterpartyAnswerChoice.NO,
                                    CounterpartyAnswerChoice.UNSURE,
                                    CounterpartyAnswerChoice.DECLINED
                                ),
                                selectedChoice = q1WorkDelivered,
                                onSelectChoice = { choice ->
                                    q1WorkDelivered = if (q1WorkDelivered == choice) null else choice
                                    submitError = null
                                },
                                tagPrefix = "public_q1"
                            )

                            QuestionChoiceCard(
                                questionNumber = 2,
                                questionTitle = "Is the amount ${RupeeFormatter.format(claim.requestedAmountRupees)} correct?",
                                choices = listOf(
                                    CounterpartyAnswerChoice.YES,
                                    CounterpartyAnswerChoice.NO,
                                    CounterpartyAnswerChoice.CORRECTED_AMOUNT,
                                    CounterpartyAnswerChoice.UNSURE,
                                    CounterpartyAnswerChoice.DECLINED
                                ),
                                selectedChoice = q2AmountCorrect,
                                onSelectChoice = { choice ->
                                    q2AmountCorrect = if (q2AmountCorrect == choice) null else choice
                                    submitError = null
                                },
                                tagPrefix = "public_q2",
                                extraContent = if (q2AmountCorrect == CounterpartyAnswerChoice.CORRECTED_AMOUNT) {
                                    {
                                        Spacer(Modifier.height(10.dp))
                                        OutlinedTextField(
                                            value = q2CorrectedAmountInput,
                                            onValueChange = { raw ->
                                                q2CorrectedAmountInput = raw.filter { it.isDigit() }.take(9)
                                                submitError = null
                                            },
                                            label = { Text("Corrected amount (Rs)") },
                                            placeholder = { Text("e.g. 10500") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("public_q2_corrected_amount_input")
                                        )
                                    }
                                } else null
                            )

                            QuestionChoiceCard(
                                questionNumber = 3,
                                questionTitle = "Has it been paid, and when?",
                                choices = listOf(
                                    CounterpartyAnswerChoice.YES,
                                    CounterpartyAnswerChoice.NO,
                                    CounterpartyAnswerChoice.UNSURE,
                                    CounterpartyAnswerChoice.DECLINED
                                ),
                                selectedChoice = q3PaidStatus,
                                onSelectChoice = { choice ->
                                    q3PaidStatus = if (q3PaidStatus == choice) null else choice
                                    submitError = null
                                },
                                tagPrefix = "public_q3",
                                extraContent = if (q3PaidStatus == CounterpartyAnswerChoice.YES) {
                                    {
                                        Spacer(Modifier.height(10.dp))
                                        OutlinedTextField(
                                            value = q3PaidDateInput,
                                            onValueChange = { q3PaidDateInput = it.take(30) },
                                            label = { Text("Payment date (optional, YYYY-MM-DD)") },
                                            placeholder = { Text("e.g. 2026-11-18") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("public_q3_paid_date_input")
                                        )
                                    }
                                } else null
                            )

                            // Submit & Partial/Decline Controls
                            PassportSectionCard {
                                Text(
                                    text = "Partial answers and declining are allowed. Any \"No\" or corrected amount flags this record as Disputed for the worker to review. Your answer is saved with a timestamp and Claim v${claim.claimVersion}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSecondary
                                )

                                if (!submitError.isNullOrBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    Surface(
                                        color = EvidenceDisputedBg,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = submitError.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EvidenceDisputedInk,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val correctedAmt = if (q2AmountCorrect == CounterpartyAnswerChoice.CORRECTED_AMOUNT) {
                                            q2CorrectedAmountInput.toLongOrNull()
                                        } else null

                                        if (q1WorkDelivered == null && q2AmountCorrect == null && q3PaidStatus == null) {
                                            submitError = "Select an answer for at least one question, or tap Decline all."
                                            return@Button
                                        }
                                        if (q2AmountCorrect == CounterpartyAnswerChoice.CORRECTED_AMOUNT &&
                                            (correctedAmt == null || correctedAmt <= 0L)
                                        ) {
                                            submitError = "Enter a valid positive rupee amount for your correction."
                                            return@Button
                                        }

                                        val payload = ParsedCounterpartyResponses(
                                            workDelivered = q1WorkDelivered,
                                            amountCorrect = q2AmountCorrect,
                                            correctedAmountRupees = correctedAmt,
                                            paidStatus = q3PaidStatus,
                                            paidDate = if (q3PaidStatus == CounterpartyAnswerChoice.YES) q3PaidDateInput.trim() else "",
                                            claimVersionShown = claim.claimVersion,
                                            otpVerifiedAtEpochMs = currentOtp.verifiedAtEpochMs
                                        )

                                        isSubmitting = true
                                        submitError = null
                                        scope.launch {
                                            val result = repository.submitCounterpartyResponse(
                                                token = claim.token,
                                                responses = payload,
                                                totalOtpAttempts = currentOtp.totalFailedAttempts
                                            )
                                            isSubmitting = false
                                            result.onSuccess {
                                                uiState = PublicConfirmationUiState.Submitted(
                                                    claim = claim,
                                                    submittedResponses = payload,
                                                    submittedAtEpochMs = System.currentTimeMillis()
                                                )
                                            }.onFailure { err ->
                                                submitError = err.message
                                                    ?: "Could not submit your response. The link may have expired or already been answered."
                                            }
                                        }
                                    },
                                    enabled = !isSubmitting,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("public_submit_response_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = InkPrimary,
                                        contentColor = OnDarkInk
                                    )
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = OnDarkInk,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Submit response", style = MaterialTheme.typography.labelLarge)
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        val payload = ParsedCounterpartyResponses(
                                            workDelivered = CounterpartyAnswerChoice.DECLINED,
                                            amountCorrect = CounterpartyAnswerChoice.DECLINED,
                                            paidStatus = CounterpartyAnswerChoice.DECLINED,
                                            claimVersionShown = claim.claimVersion,
                                            otpVerifiedAtEpochMs = currentOtp.verifiedAtEpochMs
                                        )
                                        isSubmitting = true
                                        submitError = null
                                        scope.launch {
                                            val result = repository.submitCounterpartyResponse(
                                                token = claim.token,
                                                responses = payload,
                                                totalOtpAttempts = currentOtp.totalFailedAttempts
                                            )
                                            isSubmitting = false
                                            result.onSuccess {
                                                uiState = PublicConfirmationUiState.Submitted(
                                                    claim = claim,
                                                    submittedResponses = payload,
                                                    submittedAtEpochMs = System.currentTimeMillis()
                                                )
                                            }.onFailure { err ->
                                                submitError = err.message ?: "Could not record decline."
                                            }
                                        }
                                    },
                                    enabled = !isSubmitting,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("public_decline_all_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = CardSurface,
                                        contentColor = InkSecondary
                                    )
                                ) {
                                    Text("Decline to answer", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }

        TrustFooterBanner()
    }
}

@Composable
private fun ScopedClaimDetailsCard(claim: PublicConfirmationClaim) {
    val expiryLabel = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(claim.expiresAtEpochMs))

    PassportSectionCard(modifier = Modifier.testTag("public_scoped_claim_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Confirmation Request",
                style = MaterialTheme.typography.labelMedium,
                color = InkSecondary
            )
            NeutralSmallChip(label = "Claim v${claim.claimVersion}")
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "${claim.workerName} is asking you to confirm one record",
            style = MaterialTheme.typography.titleLarge,
            color = InkPrimary
        )

        Spacer(Modifier.height(12.dp))
        Surface(
            color = StoneSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ClaimFactRow(label = "Who is asking", value = claim.workerName)
                HorizontalDivider(color = HairlineBorder)
                ClaimFactRow(label = "Work description", value = claim.workDescription)
                if (claim.periodLabel.isNotBlank() &&
                    !claim.workDescription.contains(claim.periodLabel, ignoreCase = true)
                ) {
                    HorizontalDivider(color = HairlineBorder)
                    ClaimFactRow(label = "Period", value = claim.periodLabel)
                }
                HorizontalDivider(color = HairlineBorder)
                ClaimFactRow(
                    label = "Date",
                    value = claim.earnedDate.ifBlank { "Not specified" }
                )
                HorizontalDivider(color = HairlineBorder)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Amount claimed",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSecondary
                    )
                    Text(
                        text = RupeeFormatter.format(claim.requestedAmountRupees),
                        style = RupeeAmountHeroStyle,
                        color = InkPrimary
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = InkSecondary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp)
            )
            Text(
                text = "Only this single record is shown. None of ${claim.workerName}'s other earnings, clients, or bank lines are visible here. Link expires $expiryLabel.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary
            )
        }
    }
}

@Composable
private fun DemoOtpVerificationCard(
    otpState: DemoOtpState,
    nowEpochMs: Long,
    otpInput: String,
    feedback: String?,
    onOtpInputChange: (String) -> Unit,
    onVerifyClick: () -> Unit,
    onRegenerateClick: () -> Unit
) {
    val isLocked = otpState.isLocked(nowEpochMs)
    val isExpired = otpState.isExpired(nowEpochMs)
    val remainingExpiry = otpState.remainingExpirySeconds(nowEpochMs)
    val remainingLockout = otpState.remainingLockoutSeconds(nowEpochMs)
    val formattedCode = if (otpState.activeCode.length == 6) {
        "${otpState.activeCode.substring(0, 3)} ${otpState.activeCode.substring(3, 6)}"
    } else {
        otpState.activeCode
    }

    PassportSectionCard(modifier = Modifier.testTag("public_demo_otp_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Step 1 · Verify DEMO OTP",
                style = MaterialTheme.typography.titleMedium,
                color = InkPrimary
            )
            NeutralSmallChip(
                label = if (isExpired) "Expired" else "Valid ${CounterpartyConfirmationRules.formatCountdown(remainingExpiry)}"
            )
        }

        Spacer(Modifier.height(10.dp))

        // On-screen DEMO OTP banner
        Surface(
            color = StoneSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, InkPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = CounterpartyConfirmationRules.DEMO_OTP_LABEL,
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSecondary,
                    modifier = Modifier.testTag("public_demo_otp_banner_label")
                )
                Text(
                    text = formattedCode,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = InkPrimary,
                    modifier = Modifier.testTag("public_demo_otp_code")
                )
                Text(
                    text = if (isExpired) {
                        "Code expired after 5 minutes"
                    } else {
                        "Expires in ${CounterpartyConfirmationRules.formatCountdown(remainingExpiry)} · Max 3 attempts"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isExpired) EvidenceDisputedInk else InkSecondary
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Explicit disclaimer required by spec
        Surface(
            color = CardSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = InkPrimary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp)
                )
                Text(
                    text = CounterpartyConfirmationRules.OTP_DISCLAIMER_TEXT,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkPrimary,
                    modifier = Modifier.testTag("public_otp_disclaimer")
                )
            }
        }

        if (isLocked) {
            Spacer(Modifier.height(10.dp))
            Surface(
                color = EvidenceDisputedBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("public_otp_locked_banner")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LockClock,
                        contentDescription = null,
                        tint = EvidenceDisputedInk,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Too many failed attempts (3/3). Locked for ${CounterpartyConfirmationRules.formatCountdown(remainingLockout)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EvidenceDisputedInk
                    )
                }
            }
        }

        if (!feedback.isNullOrBlank() && !isLocked) {
            Spacer(Modifier.height(10.dp))
            Surface(
                color = EvidenceDisputedBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = feedback,
                    style = MaterialTheme.typography.bodySmall,
                    color = EvidenceDisputedInk,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = otpInput,
            onValueChange = onOtpInputChange,
            enabled = !isLocked,
            label = { Text("Enter 6-digit DEMO OTP") },
            placeholder = { Text("000000") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("public_otp_input")
        )

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onVerifyClick,
            enabled = !isLocked && !isExpired && otpInput.length == 6,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("public_otp_verify_button"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = InkPrimary,
                contentColor = OnDarkInk
            )
        ) {
            Text("Verify DEMO OTP", style = MaterialTheme.typography.labelLarge)
        }

        if (isExpired && !isLocked) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRegenerateClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("public_otp_regenerate_button"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, InkPrimary),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CardSurface,
                    contentColor = InkPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Generate new DEMO OTP")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionChoiceCard(
    questionNumber: Int,
    questionTitle: String,
    choices: List<CounterpartyAnswerChoice>,
    selectedChoice: CounterpartyAnswerChoice?,
    onSelectChoice: (CounterpartyAnswerChoice) -> Unit,
    tagPrefix: String,
    extraContent: (@Composable () -> Unit)? = null
) {
    PassportSectionCard(modifier = Modifier.testTag("${tagPrefix}_card")) {
        Text(
            text = "$questionNumber. $questionTitle",
            style = MaterialTheme.typography.titleSmall,
            color = InkPrimary
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            choices.forEach { choice ->
                val selected = selectedChoice == choice
                FilterChip(
                    selected = selected,
                    onClick = { onSelectChoice(choice) },
                    label = { Text(choice.label, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("${tagPrefix}_${choice.code}"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = InkPrimary,
                        selectedLabelColor = OnDarkInk,
                        containerColor = CardSurface,
                        labelColor = InkPrimary
                    )
                )
            }
        }
        extraContent?.invoke()
    }
}

@Composable
private fun SubmittedReceiptContent(
    claim: PublicConfirmationClaim,
    responses: ParsedCounterpartyResponses,
    submittedAtEpochMs: Long,
    onClose: () -> Unit
) {
    val submittedTimeLabel = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)
        .format(Date(submittedAtEpochMs))

    Column(
        modifier = Modifier
            .widthIn(max = 580.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PassportSectionCard(modifier = Modifier.testTag("public_confirmation_receipt_card")) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = EvidenceSourceMatchedInk,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Response recorded",
                        style = MaterialTheme.typography.titleLarge,
                        color = InkPrimary
                    )
                    Text(
                        text = "Saved $submittedTimeLabel · Claim v${responses.claimVersionShown}",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Your answers for ${claim.workerName}'s record (${RupeeFormatter.format(claim.requestedAmountRupees)}) have been saved. This one-time confirmation link is now closed.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkPrimary
            )

            Spacer(Modifier.height(12.dp))
            Surface(
                color = StoneSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ClaimFactRow(
                        label = "1. Work delivered",
                        value = responses.workDelivered?.label ?: "Skipped"
                    )
                    HorizontalDivider(color = HairlineBorder)
                    ClaimFactRow(
                        label = "2. Amount",
                        value = if (responses.amountCorrect == CounterpartyAnswerChoice.CORRECTED_AMOUNT &&
                            responses.correctedAmountRupees != null
                        ) {
                            "Corrected to ${RupeeFormatter.format(responses.correctedAmountRupees)}"
                        } else {
                            responses.amountCorrect?.label ?: "Skipped"
                        }
                    )
                    HorizontalDivider(color = HairlineBorder)
                    ClaimFactRow(
                        label = "3. Paid",
                        value = when {
                            responses.paidStatus == CounterpartyAnswerChoice.YES && responses.paidDate.isNotBlank() ->
                                "Yes (${responses.paidDate})"
                            responses.paidStatus != null -> responses.paidStatus.label
                            else -> "Skipped"
                        }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("public_receipt_done_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = InkPrimary,
                    contentColor = OnDarkInk
                )
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun ClaimFactRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = InkSecondary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = InkPrimary)
    }
}
