package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.GeminiDocumentReader
import com.example.data.PassportRepository
import com.example.data.authStateFlow
import com.example.data.signOutGoogle
import com.example.reconciliation.BankCreditClassification
import com.example.ui.components.ConfirmationLinkCreatedDialog
import com.example.ui.components.TrustFooterBanner
import com.example.ui.screens.AddSourceScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.IssuesScreen
import com.example.ui.screens.MoreScreen
import com.example.ui.screens.PartiesScreen
import com.example.ui.screens.PartyDetailScreen
import com.example.ui.screens.PassportScreen
import com.example.ui.screens.PublicConfirmationScreen
import com.example.ui.screens.ReviewConfirmScreen
import com.example.ui.screens.ReviewerViewScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ShareScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.PaperCanvas
import com.example.ui.theme.StoneVariant
import com.example.viewmodel.PassportViewModel
import com.example.viewmodel.PassportWorkspaceData
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_PASSPORT = "passport"
const val ROUTE_ADD = "add_source"
const val ROUTE_ISSUES = "issues"
const val ROUTE_SHARE = "share"
const val ROUTE_MORE = "more"
const val ROUTE_REVIEW_CONFIRM = "review_confirm"
const val ROUTE_PARTIES = "parties"
const val ROUTE_PARTY_DETAIL = "party_detail/{partyId}"
const val ROUTE_REVIEWER_VIEW = "reviewer_view"
const val ROUTE_SETTINGS = "settings"

fun partyDetailRoute(partyId: String): String = "party_detail/$partyId"

private data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val PrimaryTabs = listOf(
    BottomNavTab(ROUTE_PASSPORT, "Passport", Icons.Filled.Badge, Icons.Outlined.Badge),
    BottomNavTab(ROUTE_ADD, "Add", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline),
    BottomNavTab(ROUTE_ISSUES, "Issues", Icons.Filled.FactCheck, Icons.Outlined.FactCheck),
    BottomNavTab(ROUTE_SHARE, "Share", Icons.Filled.IosShare, Icons.Outlined.IosShare),
    BottomNavTab(ROUTE_MORE, "More", Icons.Filled.Menu, Icons.Outlined.Menu)
)

@Composable
fun PassportAppRoot(
    auth: FirebaseAuth = Firebase.auth,
    incomingConfirmationToken: String? = null,
    onConsumeIncomingToken: () -> Unit = {}
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    // Public counterparty confirmation route lives OUTSIDE the auth gate (no sign-in required)
    var activePublicConfirmationToken by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(incomingConfirmationToken) {
        if (!incomingConfirmationToken.isNullOrBlank()) {
            activePublicConfirmationToken = incomingConfirmationToken
            onConsumeIncomingToken()
        }
    }

    val openPublicToken = activePublicConfirmationToken
    if (!openPublicToken.isNullOrBlank()) {
        PublicConfirmationScreen(
            token = openPublicToken,
            onClose = { activePublicConfirmationToken = null }
        )
        return
    }

    if (user == null) {
        AuthScreen(
            onAuthSuccess = {},
            onOpenConfirmationLink = { token -> activePublicConfirmationToken = token }
        )
    } else {
        key(user.uid) {
            AuthenticatedPassportShell(
                currentUserId = user.uid,
                userEmail = user.email,
                onOpenPublicConfirmation = { token -> activePublicConfirmationToken = token }
            )
        }
    }
}

@Composable
private fun AuthenticatedPassportShell(
    currentUserId: String,
    userEmail: String?,
    onOpenPublicConfirmation: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: PassportViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                PassportViewModel(
                    repository = PassportRepository(db),
                    documentReader = GeminiDocumentReader(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val workspaceUiState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val extractionState by viewModel.extractionState.collectAsStateWithLifecycle()
    val selectedShareLinkId by viewModel.selectedShareLinkId.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val createdConfirmationLink by viewModel.createdConfirmationLink.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    createdConfirmationLink?.let { info ->
        ConfirmationLinkCreatedDialog(
            info = info,
            onDismiss = { viewModel.dismissCreatedConfirmationLink() },
            onOpenPublicPreview = { token -> onOpenPublicConfirmation(token) }
        )
    }

    when (val state = workspaceUiState) {
        is UiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PaperCanvas),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = InkPrimary)
                    Text(
                        text = "Loading your Earnings & Work Passport…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }
            }
        }
        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PaperCanvas)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Could not load your passport",
                        style = MaterialTheme.typography.titleLarge,
                        color = InkPrimary
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }
            }
        }
        is UiState.Success -> {
            val workspace = state.data
            if (workspace.activeWorker == null) {
                WelcomeScreen(
                    existingWorkers = workspace.allWorkers,
                    isBusy = isBusy,
                    onChooseAshaDemo = { viewModel.openAshaDemoProfile(forceReset = false) },
                    onResetDemo = { viewModel.openAshaDemoProfile(forceReset = true) },
                    onCreateCustomWorker = { name, city, work ->
                        viewModel.createAndSelectCustomWorker(name, city, work)
                    },
                    onSelectExistingWorker = { viewModel.selectExistingWorker(it) }
                )
            } else {
                // Auto-sync any unapplied public counterparty responses into statuses & audit log
                LaunchedEffect(workspace.confirmationRequests) {
                    val unsynced = workspace.confirmationRequests.filter { req ->
                        (req.status == "RESPONDED" || req.status == "DECLINED") &&
                            req.appliedToLedgerAt == null
                    }
                    if (unsynced.isNotEmpty()) {
                        viewModel.syncPendingCounterpartyResponses(unsynced)
                    }
                }

                PassportMainNavShell(
                    workspace = workspace,
                    userEmail = userEmail,
                    extractionState = extractionState,
                    selectedShareLinkId = selectedShareLinkId,
                    isBusy = isBusy,
                    snackbarHostState = snackbarHostState,
                    viewModel = viewModel,
                    onOpenPublicConfirmation = onOpenPublicConfirmation,
                    onSignOut = {
                        viewModel.clearActiveWorker()
                        signOutGoogle(credentialManager, scope)
                    }
                )
            }
        }
    }
}

@Composable
private fun PassportMainNavShell(
    workspace: PassportWorkspaceData,
    userEmail: String?,
    extractionState: com.example.viewmodel.ExtractionUiState,
    selectedShareLinkId: String?,
    isBusy: Boolean,
    snackbarHostState: SnackbarHostState,
    viewModel: PassportViewModel,
    onOpenPublicConfirmation: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_PASSPORT

    fun navigateTopTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = PaperCanvas,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TrustFooterBanner()
                HorizontalDivider(color = HairlineBorder, thickness = 1.dp)
                NavigationBar(
                    containerColor = CardSurface,
                    contentColor = InkPrimary,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    PrimaryTabs.forEach { tab ->
                        val isSelected = currentRoute == tab.route ||
                            (tab.route == ROUTE_MORE && currentRoute in setOf(
                                ROUTE_PARTIES,
                                ROUTE_PARTY_DETAIL,
                                ROUTE_REVIEW_CONFIRM,
                                ROUTE_REVIEWER_VIEW,
                                ROUTE_SETTINGS
                            ))

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { navigateTopTab(tab.route) },
                            icon = {
                                val icon = if (isSelected) tab.selectedIcon else tab.unselectedIcon
                                if (tab.route == ROUTE_ISSUES && workspace.issues.isNotEmpty()) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = InkPrimary,
                                                contentColor = CardSurface
                                            ) {
                                                Text("${workspace.issues.size}")
                                            }
                                        }
                                    ) {
                                        Icon(icon, contentDescription = tab.label)
                                    }
                                } else {
                                    Icon(icon, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = InkPrimary,
                                selectedTextColor = InkPrimary,
                                indicatorColor = StoneVariant,
                                unselectedIconColor = InkSecondary,
                                unselectedTextColor = InkSecondary
                            ),
                            modifier = Modifier.testTag("bottom_nav_${tab.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_PASSPORT,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(ROUTE_PASSPORT) {
                PassportScreen(
                    workspace = workspace,
                    isBusy = isBusy,
                    onNavigateToAdd = { navigateTopTab(ROUTE_ADD) },
                    onNavigateToIssues = { navigateTopTab(ROUTE_ISSUES) },
                    onNavigateToReview = { navController.navigate(ROUTE_REVIEW_CONFIRM) },
                    onNavigateToParties = { navController.navigate(ROUTE_PARTIES) },
                    onNavigateToReviewerView = { navController.navigate(ROUTE_REVIEWER_VIEW) },
                    onRequestConfirmation = { rec -> viewModel.sendConfirmationRequest(rec) },
                    onOpenPublicConfirmation = onOpenPublicConfirmation,
                    onAcceptClientCorrection = { req, rec, amt ->
                        viewModel.acceptClientCorrection(req, rec, amt)
                    },
                    onDisputeClientResponse = { req, rec, note ->
                        viewModel.disputeClientResponse(req, rec, note)
                    },
                    onLoadAshaDemo = if (workspace.activeWorker?.demoProfile != true) {
                        { viewModel.openAshaDemoProfile(forceReset = false) }
                    } else null
                )
            }

            composable(ROUTE_ADD) {
                AddSourceScreen(
                    workspace = workspace,
                    extractionState = extractionState,
                    isBusy = isBusy,
                    onUploadFile = { uri, sourceType ->
                        viewModel.ingestUploadedFile(context, uri, sourceType) {
                            navController.navigate(ROUTE_REVIEW_CONFIRM)
                        }
                    },
                    onReadSampleWithAi = { sample ->
                        viewModel.readSampleDocumentWithAi(sample) {
                            navController.navigate(ROUTE_REVIEW_CONFIRM)
                        }
                    },
                    onImportSampleBankCsv = { sampleCsv ->
                        viewModel.importSampleBankCsv(sampleCsv)
                    },
                    onRetryIngestion = {
                        viewModel.retryLastIngestion(context) {
                            navController.navigate(ROUTE_REVIEW_CONFIRM)
                        }
                    },
                    onDismissExtractionState = { viewModel.dismissExtractionState() },
                    onNavigateToIssues = { navigateTopTab(ROUTE_ISSUES) },
                    onOpenPartyDetail = { partyId -> navController.navigate(partyDetailRoute(partyId)) },
                    onAddParty = { name, type, roles, phone, email, note, regNum, roleTitle, cycle, startedOn ->
                        viewModel.addParty(
                            name = name,
                            partyType = type,
                            roles = roles,
                            phone = phone,
                            email = email,
                            note = note,
                            registrationNumber = regNum,
                            roleTitle = roleTitle,
                            payoutCycle = cycle,
                            startedOn = startedOn
                        )
                    },
                    onAddSelfDeclared = { partyId, partyName, amount, date, period, ref, notes ->
                        viewModel.addSelfDeclaredIncome(partyId, partyName, amount, date, period, ref, notes)
                    },
                    onAddBankCredit = { bank, date, amount, narration, utr ->
                        viewModel.addBankCredit(bank, date, amount, narration, utr)
                    }
                )
            }

            composable(ROUTE_ISSUES) {
                IssuesScreen(
                    workspace = workspace,
                    isBusy = isBusy,
                    onOpenDocumentReview = { doc ->
                        viewModel.openPendingDocumentForReview(doc)
                        navController.navigate(ROUTE_REVIEW_CONFIRM)
                    },
                    onConfirmMatch = { candidate -> viewModel.confirmMatch(candidate) },
                    onRejectMatch = { candidate -> viewModel.rejectMatch(candidate) },
                    onMarkDisputed = { record, note ->
                        viewModel.markRecordDisputed(record, note)
                    },
                    onClearDispute = { record, hasBankMatch ->
                        viewModel.clearRecordDispute(record, hasBankMatch)
                    },
                    onIgnoreDuplicate = { record -> viewModel.ignoreDuplicateRecord(record) },
                    onRequestConfirmation = { record ->
                        viewModel.sendConfirmationRequest(record)
                    },
                    onOpenPublicConfirmation = onOpenPublicConfirmation,
                    onAcceptClientCorrection = { req, rec, amt ->
                        viewModel.acceptClientCorrection(req, rec, amt)
                    },
                    onDisputeClientResponse = { req, rec, note ->
                        viewModel.disputeClientResponse(req, rec, note)
                    },
                    onLinkCreditToIncome = { credit, income ->
                        viewModel.linkBankCreditToExistingIncome(credit, income)
                    },
                    onCreateIncomeForCredit = { credit, partyName, earnedDate, periodLabel, notes ->
                        viewModel.createIncomeAndLinkBankCredit(
                            credit = credit,
                            partyName = partyName,
                            earnedDate = earnedDate,
                            periodLabel = periodLabel,
                            notes = notes
                        )
                    },
                    onMarkCreditNotIncome = { credit ->
                        viewModel.classifyBankCredit(credit, BankCreditClassification.PERSONAL_TRANSFER)
                    },
                    onNavigateToAddBankCredit = { navigateTopTab(ROUTE_ADD) }
                )
            }

            composable(ROUTE_SHARE) {
                val worker = workspace.activeWorker
                ShareScreen(
                    workspace = workspace,
                    isBusy = isBusy,
                    onCreateShareLink = { recipient, range, parties, evidence, mask, expires ->
                        if (worker != null) {
                            viewModel.createShareLink(
                                worker = worker,
                                recipientLabel = recipient,
                                dateRangeLabel = range,
                                includedPartyIds = parties,
                                includedEvidenceLevels = evidence,
                                maskBankRefs = mask,
                                expiresOn = expires,
                                incomeRecords = workspace.incomeRecords,
                                onCreated = {
                                    navController.navigate(ROUTE_REVIEWER_VIEW)
                                }
                            )
                        }
                    },
                    onRevokeShareLink = { link -> viewModel.revokeShareLink(link) },
                    onOpenReviewerView = { shareId ->
                        viewModel.selectShareLinkForPreview(shareId)
                        navController.navigate(ROUTE_REVIEWER_VIEW)
                    }
                )
            }

            composable(ROUTE_MORE) {
                MoreScreen(
                    workspace = workspace,
                    onNavigateToParties = { navController.navigate(ROUTE_PARTIES) },
                    onNavigateToReviewConfirm = { navController.navigate(ROUTE_REVIEW_CONFIRM) },
                    onNavigateToReviewerView = { navController.navigate(ROUTE_REVIEWER_VIEW) },
                    onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onSwitchWorkerProfile = { viewModel.clearActiveWorker() }
                )
            }

            composable(ROUTE_REVIEW_CONFIRM) {
                ReviewConfirmScreen(
                    workspace = workspace,
                    extractionState = extractionState,
                    isBusy = isBusy,
                    onSelectPendingDoc = { doc -> viewModel.openPendingDocumentForReview(doc) },
                    onQuickAddParty = { name, type, roles, phone, email, note, regNum, onCreated ->
                        viewModel.addParty(
                            name = name,
                            partyType = type,
                            roles = roles,
                            phone = phone,
                            email = email,
                            note = note,
                            registrationNumber = regNum,
                            onCreated = onCreated
                        )
                    },
                    onConfirmSave = { docId, draft, form, workGiverId, workGiverName, payerId, payerName, confirmed ->
                        viewModel.confirmReviewedDocument(
                            existingDocumentId = docId,
                            draft = draft,
                            form = form,
                            workGiverPartyId = workGiverId,
                            workGiverPartyName = workGiverName,
                            payerPartyId = payerId,
                            payerPartyName = payerName,
                            workerConfirmed = confirmed,
                            onSaved = { navigateTopTab(ROUTE_PASSPORT) }
                        )
                    },
                    onDiscardDraft = { docId -> viewModel.discardDraft(docId) },
                    onAddManualSelfDeclared = { partyId, partyName, amount, date, period, ref, notes ->
                        viewModel.addSelfDeclaredIncome(
                            partyId = partyId,
                            partyName = partyName,
                            amountRupees = amount,
                            earnedDate = date,
                            periodLabel = period,
                            referenceCode = ref,
                            notes = notes,
                            onSaved = { navigateTopTab(ROUTE_PASSPORT) }
                        )
                    },
                    onBack = { navController.popBackStack() },
                    onNavigateToAddDoc = { navigateTopTab(ROUTE_ADD) }
                )
            }

            composable(ROUTE_PARTIES) {
                PartiesScreen(
                    workspace = workspace,
                    isBusy = isBusy,
                    onAddParty = { name, type, roles, phone, email, note, regNum, roleTitle, cycle, startedOn ->
                        viewModel.addParty(
                            name = name,
                            partyType = type,
                            roles = roles,
                            phone = phone,
                            email = email,
                            note = note,
                            registrationNumber = regNum,
                            roleTitle = roleTitle,
                            payoutCycle = cycle,
                            startedOn = startedOn
                        )
                    },
                    onOpenPartyDetail = { partyId -> navController.navigate(partyDetailRoute(partyId)) },
                    onSendConfirmationRequest = { rec -> viewModel.sendConfirmationRequest(rec) },
                    onOpenPublicConfirmation = onOpenPublicConfirmation,
                    onAcceptClientCorrection = { req, rec, amt ->
                        viewModel.acceptClientCorrection(req, rec, amt)
                    },
                    onDisputeClientResponse = { req, rec, note ->
                        viewModel.disputeClientResponse(req, rec, note)
                    },
                    onSimulatePartyDecision = { req, confirm -> viewModel.simulatePartyDecision(req, confirm) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_PARTY_DETAIL) { entry ->
                val partyId = entry.arguments?.getString("partyId").orEmpty()
                PartyDetailScreen(
                    partyId = partyId,
                    workspace = workspace,
                    isBusy = isBusy,
                    onSendConfirmationRequest = { rec -> viewModel.sendConfirmationRequest(rec) },
                    onOpenPublicConfirmation = onOpenPublicConfirmation,
                    onAcceptClientCorrection = { req, rec, amt ->
                        viewModel.acceptClientCorrection(req, rec, amt)
                    },
                    onDisputeClientResponse = { req, rec, note ->
                        viewModel.disputeClientResponse(req, rec, note)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_REVIEWER_VIEW) {
                ReviewerViewScreen(
                    workspace = workspace,
                    selectedShareLinkId = selectedShareLinkId,
                    onSelectShareLink = { viewModel.selectShareLinkForPreview(it) },
                    onBack = { navController.popBackStack() },
                    onNavigateToShare = { navigateTopTab(ROUTE_SHARE) }
                )
            }

            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    workspace = workspace,
                    userEmail = userEmail,
                    isBusy = isBusy,
                    onSwitchProfile = { viewModel.clearActiveWorker() },
                    onLoadAshaDemo = {
                        viewModel.openAshaDemoProfile(forceReset = false) {
                            navigateTopTab(ROUTE_PASSPORT)
                        }
                    },
                    onResetAshaDemo = {
                        viewModel.openAshaDemoProfile(forceReset = true) {
                            navigateTopTab(ROUTE_PASSPORT)
                        }
                    },
                    onSignOut = onSignOut,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
