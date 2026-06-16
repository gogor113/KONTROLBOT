package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.BotClient
import com.example.data.api.GridRecommendation
import androidx.compose.foundation.border
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

// Design Palette - Elegant Dark Theme
val SlateDarkBg = Color(0xFF0A0B0D)     // Deep Obsidian Base Background
val CardDarkBg = Color(0xFF1A1C1E)      // Charcoal Card Background
val TealPrimary = Color(0xFFC0A438)     // Rich Dark Gold for primary CTAs
val TealLight = Color(0xFFE8C547)       // High-contrast Warm Gold highlights
val GoldDark = Color(0xFFC0A438)        // Primary Gold Dark
val GoldLight = Color(0xFFE8C547)       // Primary Gold Light
val AlertRed = Color(0xFFE11D48)        // Elegant Rose Red alert (rose-600)
val SuccessGreen = Color(0xFF10B981)    // Emerald Success Green (emerald-500)
val BorderGray = Color(0xFF2E3135)      // Midnight Border Slate
val TextLight = Color(0xFFF8FAFC)       // Slack Crisp White Text
val TextMuted = Color(0xFF94A3B8)       // Charcoal Muted Text

@Composable
fun BotManagerScreen(
    viewModel: BotManagerViewModel,
    modifier: Modifier = Modifier
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    Crossfade(targetState = authState, label = "auth_transition") { currentAuthState ->
        when (currentAuthState) {
            is AuthState.Unauthenticated -> {
                GoogleSignInScreen(
                    onSignIn = { email, name ->
                        viewModel.loginWithGoogle(email, name)
                    }
                )
            }
            is AuthState.Loading -> {
                AuthLoadingScreen()
            }
            is AuthState.Error -> {
                GoogleSignInBlockedScreen(
                    message = currentAuthState.message,
                    email = currentAuthState.attemptedEmail,
                    onBack = {
                        viewModel.resetToLogin()
                    }
                )
            }
            is AuthState.Authenticated -> {
                MainBotManagerContent(
                    viewModel = viewModel,
                    profile = currentAuthState.profile,
                    modifier = modifier
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainBotManagerContent(
    viewModel: BotManagerViewModel,
    profile: GoogleUserProfile,
    modifier: Modifier = Modifier
) {
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val geminiState by viewModel.geminiState.collectAsStateWithLifecycle()
    val isSimulating by viewModel.isSimulating.collectAsStateWithLifecycle()
    val expiringNotifications by viewModel.expiringNotifications.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var activeEditClient by remember { mutableStateOf<BotClient?>(null) }
    var activeAiClient by remember { mutableStateOf<BotClient?>(null) }
    var showLicenseDialog by remember { mutableStateOf<BotClient?>(null) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(0) }

    val df = DecimalFormat("#,##0.00")

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDarkBg),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "GOGOR Icon",
                            tint = TealLight,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "GOGOR BOT CONTROL",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            fontSize = 18.sp,
                            color = TextLight,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleSimulation() },
                        modifier = Modifier.testTag("simulation_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isSimulating) Icons.Default.PlayArrow else Icons.Default.Refresh,
                            contentDescription = "Simulate",
                            tint = if (isSimulating) TealLight else TextMuted
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showProfileMenu = !showProfileMenu },
                            modifier = Modifier.testTag("user_profile_avatar_btn")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(TealPrimary.copy(alpha = 0.2f))
                                    .border(1.dp, TealLight.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = profile.name.take(1).uppercase(),
                                    color = TealLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showProfileMenu,
                            onDismissRequest = { showProfileMenu = false },
                            modifier = Modifier
                                .background(CardDarkBg)
                                .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)) {
                                        Text(
                                            text = profile.name,
                                            fontWeight = FontWeight.Bold,
                                            color = TextLight,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = profile.email,
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                },
                                onClick = {},
                                enabled = false
                            )
                            HorizontalDivider(color = BorderGray, thickness = 1.dp)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.ExitToApp,
                                            contentDescription = null,
                                            tint = AlertRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Keluar", color = AlertRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                },
                                onClick = {
                                    showProfileMenu = false
                                    viewModel.signOut()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SlateDarkBg,
                    titleContentColor = TextLight
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = TealPrimary,
                contentColor = TextLight,
                modifier = Modifier.testTag("add_client_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Daftar Pengguna Baru")
            }
        },
        containerColor = SlateDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Premium design tab selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .background(CardDarkBg, RoundedCornerShape(22.dp))
                    .padding(4.dp)
            ) {
                TabButton(
                    text = "MONITOR",
                    icon = Icons.Default.PlayArrow,
                    isSelected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    modifier = Modifier.weight(1f).testTag("tab_monitor_btn")
                )
                TabButton(
                    text = "LANGGANAN",
                    icon = Icons.Default.AccountCircle,
                    isSelected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    modifier = Modifier.weight(1f).testTag("tab_subscriber_btn")
                )
                TabButton(
                    text = "CON. MT5",
                    icon = Icons.Default.Share,
                    isSelected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    modifier = Modifier.weight(1f).testTag("tab_mql5_btn")
                )
                TabButton(
                    text = "GOOGLE S.S",
                    icon = Icons.Default.Refresh,
                    isSelected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    modifier = Modifier.weight(1f).testTag("tab_sheets_btn")
                )
            }

            if (currentTab == 0) {
                // General Stats Metrics Banner
                ManagerStatsBanner(clients = clients)
                
                Spacer(modifier = Modifier.height(12.dp))

                if (expiringNotifications.isNotEmpty()) {
                    ExpiringSubscriptionBanner(notifications = expiringNotifications)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Clients List
                Text(
                    text = "MONITORING PENGGUNA (${clients.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = TextMuted,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxWidth()
                ) {
                    if (clients.isEmpty()) {
                        EmptyStatePlaceholder()
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(clients, key = { it.id }) { client ->
                                BotClientCard(
                                    client = client,
                                    onEditConfig = { activeEditClient = client },
                                    onRenewLicense = { showLicenseDialog = client },
                                    onTriggerAi = { activeAiClient = client },
                                    onDelete = { viewModel.deleteClient(client) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Realtime Terminal Logs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE BOT TERMINAL LOGS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = TealLight,
                        letterSpacing = 1.sp
                    )
                    if (isSimulating) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                               text = "SIMULATOR ACTIVE",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealLight,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                TerminalLogConsole(
                    logs = logs,
                    modifier = Modifier
                        .weight(0.7f)
                        .fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            } else if (currentTab == 1) {
                SubscriberManager(
                    clients = clients,
                    onRenewLicense = { showLicenseDialog = it },
                    onTriggerAi = { activeAiClient = it },
                    onEditConfig = { activeEditClient = it },
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else if (currentTab == 2) {
                Mql5SyncScreen(
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                GoogleSheetsSyncScreen(
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // --- Modals & Dialogs overlays ---

        // Dialog: Add Client
        if (showAddDialog) {
            AddClientDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { name, telegram, balance, mode ->
                    viewModel.addClient(name, telegram, balance, mode)
                    showAddDialog = false
                }
            )
        }

        // Dialog: Edit EA Bot Configuration
        activeEditClient?.let { client ->
            EditParametersDialog(
                client = client,
                onDismiss = { activeEditClient = null },
                onSave = { updatedClient ->
                    viewModel.updateClient(updatedClient)
                    activeEditClient = null
                }
            )
        }

        // Dialog: License renewal
        showLicenseDialog?.let { client ->
            LicenseRenewalDialog(
                client = client,
                onDismiss = { showLicenseDialog = null },
                onRenew = { days ->
                    viewModel.renewLicense(client, days)
                    showLicenseDialog = null
                }
            )
        }

        // Dialog: AI Gemini Strategy Optimization
        activeAiClient?.let { client ->
            GeminiOptimizationDialog(
                client = client,
                uiState = geminiState,
                onDismiss = {
                    viewModel.resetGeminiState()
                    activeAiClient = null
                },
                onRequestOptimization = { trend, risk ->
                    viewModel.getGeminiOptimizations(trend, risk, client)
                },
                onApplyOptimization = { rec ->
                    val updated = client.copy(
                        initialLot = rec.initialLot,
                        stepPoints = rec.stepPoints,
                        recoveryThreshold = rec.recoveryThreshold,
                        expMultiplier = rec.expMultiplier,
                        maxSpread = rec.maxSpread
                    )
                    viewModel.updateClient(updated)
                    viewModel.resetGeminiState()
                    activeAiClient = null
                }
            )
        }
    }
}

// Stats Header Banner Component
@Composable
fun ManagerStatsBanner(clients: List<BotClient>) {
    val totalCapital = clients.sumOf { it.balance }
    val totalEquity = clients.sumOf { it.equity }
    val averageDrawdown = if (clients.isNotEmpty()) clients.map { it.drawdown }.average() else 0.0
    val activeCount = clients.count { it.isLicenseActive }

    val formattedCapital = DecimalFormat("#,##0").format(totalCapital)
    val formattedEquity = DecimalFormat("#,##0").format(totalEquity)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .background(
                brush = Brush.horizontalGradient(listOf(CardDarkBg, SlateDarkBg)),
                shape = RoundedCornerShape(12.dp)
            )
            .border(BorderStroke(1.dp, BorderGray.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("TOTAL CAPITAL CONTROL", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            Text("$$formattedCapital USD", fontSize = 20.sp, fontWeight = FontWeight.Black, color = TealLight)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(SuccessGreen, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Live Equity: $$formattedEquity", fontSize = 10.sp, color = TextMuted)
            }
        }

        Divider(
            modifier = Modifier
                .width(1.dp)
                .height(45.dp)
                .align(Alignment.CenterVertically),
            color = BorderGray
        )

        Column(horizontalAlignment = Alignment.End) {
            Text("AVG DRAWDOWN", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            val ddColor = if (averageDrawdown > 8.0) AlertRed else if (averageDrawdown > 4.0) GoldLight else SuccessGreen
            Text(
                text = "${DecimalFormat("0.0").format(averageDrawdown)}%",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = ddColor
            )
            Text("Active Bots: $activeCount / ${clients.size}", fontSize = 10.sp, color = TextMuted)
        }
    }
}

// Client Card Item Component
@Composable
fun BotClientCard(
    client: BotClient,
    onEditConfig: () -> Unit,
    onRenewLicense: () -> Unit,
    onTriggerAi: () -> Unit,
    onDelete: () -> Unit
) {
    // Calculate if online (based on lastActiveTime within 30 seconds)
    val isOnline = (System.currentTimeMillis() - client.lastActiveTime) < 30000
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val expString = sdf.format(Date(client.expiryTimestamp))
    val isExpired = System.currentTimeMillis() > client.expiryTimestamp || !client.isLicenseActive

    val leftAccentColor = when {
        isExpired -> AlertRed
        client.drawdown > 10.0 -> AlertRed
        client.drawdown > 5.0 -> GoldLight
        else -> SuccessGreen
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("client_card_${client.id}"),
        colors = CardDefaults.cardColors(containerColor = CardDarkBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderGray)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Elegant Left Accent Indicator Bar mapping to .border-l-4 HTML style
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(leftAccentColor)
            )
            
            Column(modifier = Modifier.padding(14.dp).weight(1f)) {
                // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isOnline) SuccessGreen else GoldLight, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = client.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = client.telegram,
                            fontSize = 12.sp,
                            color = TealLight,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // License Badge
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isExpired) AlertRed.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isExpired) "EXPIRED" else "ACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isExpired) AlertRed else SuccessGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mode & Expiration line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Mode: ${client.activeMode.replace("MODE_", "")}",
                    fontSize = 11.sp,
                    color = TextLight,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Lisensi Exp: $expString",
                    fontSize = 11.sp,
                    color = if (isExpired) AlertRed else TextMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics Grid (Balance, Equity, Drawdown)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("BALANCE", fontSize = 9.sp, color = TextMuted)
                    Text("$${DecimalFormat("#,##0").format(client.balance)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextLight)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("EQUITY", fontSize = 9.sp, color = TextMuted)
                    Text("$${DecimalFormat("#,##0").format(client.equity)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealLight)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("DRAWDOWN", fontSize = 9.sp, color = TextMuted)
                    val ddColor = if (client.drawdown > 10.0) AlertRed else if (client.drawdown > 5.0) GoldLight else SuccessGreen
                    Text("${client.drawdown}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ddColor)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Drawdown Slider visualization
            val ddProgress = (client.drawdown / 100).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { ddProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (client.drawdown > 10.0) AlertRed else if (client.drawdown > 5.0) GoldLight else SuccessGreen,
                trackColor = BorderGray
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Parameters overview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateDarkBg.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Param: Lot ${client.initialLot} | Step ${client.stepPoints} | RecTh ${client.recoveryThreshold}", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Actions bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("delete_${client.id}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = AlertRed.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Renew license button
                    TextButton(
                        onClick = onRenewLicense,
                        colors = ButtonDefaults.textButtonColors(contentColor = GoldLight),
                        modifier = Modifier.testTag("renew_license_${client.id}"),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lisensi", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Gemini AI Optimizer button
                    FilledTonalButton(
                        onClick = onTriggerAi,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = TealLight.copy(alpha = 0.15f),
                            contentColor = TealLight
                        ),
                        modifier = Modifier.testTag("gemini_ai_${client.id}"),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI Optimasi", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Edit Manual Params
                    Button(
                        onClick = onEditConfig,
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        modifier = Modifier.testTag("edit_config_${client.id}"),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
}

// Live Logging Console Component
@Composable
fun TerminalLogConsole(
    logs: List<BotLogEntry>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderGray)
    ) {
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Terminal siap. Menunggu aktivitas bot GOGOR...",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log ->
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val timeStr = sdf.format(Date(log.timestamp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "[$timeStr]",
                            color = Color(0xFFA7F3D0), // Emerald 200
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = "${log.clientName}:",
                            color = TealLight,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(
                            text = log.message,
                            color = if (log.isAlert) AlertRed else TextLight,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// Empty State Placeholder Components
@Composable
fun EmptyStatePlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Belum Ada Pengguna Bot",
            fontWeight = FontWeight.Bold,
            color = TextLight,
            fontSize = 15.sp
        )
        Text(
            text = "Klik tombol + di pojok untuk menambah robot pengguna baru.",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

// Dialog: Add Client
@Composable
fun AddClientDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, telegram: String, balance: Double, mode: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var telegram by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf("MODE_BOTH") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Daftar Pengguna GOGOR Baru",
                fontSize = 16.sp,
                color = TextLight,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Lengkap") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealLight,
                        focusedLabelColor = TealLight,
                        unfocusedBorderColor = BorderGray,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_name_field")
                )

                OutlinedTextField(
                    value = telegram,
                    onValueChange = { telegram = it },
                    label = { Text("Telegram Username") },
                    placeholder = { Text("@andrianto13") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealLight,
                        focusedLabelColor = TealLight,
                        unfocusedBorderColor = BorderGray,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = { Text("Initial Balance (USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealLight,
                        focusedLabelColor = TealLight,
                        unfocusedBorderColor = BorderGray,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Trading Mode GOGOR:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val modes = listOf("MODE_BOTH", "MODE_BUY_ONLY", "MODE_SELL_ONLY")
                    modes.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.replace("MODE_", ""), fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TealPrimary,
                                selectedLabelColor = TextLight,
                                containerColor = CardDarkBg,
                                labelColor = TextMuted
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val balNum = balance.toDoubleOrNull() ?: 1000.0
                    if (name.isNotBlank()) {
                        onAdd(name, telegram, balNum, selectedMode)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                modifier = Modifier.testTag("dialog_confirm_btn")
            ) {
                Text("Daftarkan")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
            ) {
                Text("Batal")
            }
        },
        containerColor = CardDarkBg
    )
}

// Dialog: Edit Bot Constants/Params
@Composable
fun EditParametersDialog(
    client: BotClient,
    onDismiss: () -> Unit,
    onSave: (BotClient) -> Unit
) {
    var initialLot by remember { mutableStateOf(client.initialLot.toString()) }
    var lotStep by remember { mutableStateOf(client.lotStep.toString()) }
    var stepPoints by remember { mutableStateOf(client.stepPoints.toString()) }
    var maxSpread by remember { mutableStateOf(client.maxSpread.toString()) }
    var recoveryThreshold by remember { mutableStateOf(client.recoveryThreshold.toString()) }
    var expMultiplier by remember { mutableStateOf(client.expMultiplier.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "GOGOR Param Config: ${client.name}",
                fontSize = 16.sp,
                color = TextLight,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Silakan atur konstanta numerik grid hybrid layered:", fontSize = 11.sp, color = TextMuted)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = initialLot,
                        onValueChange = { initialLot = it },
                        label = { Text("Initial Lot") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight, unfocusedTextColor = TextLight,
                            focusedBorderColor = TealLight, unfocusedBorderColor = BorderGray
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lotStep,
                        onValueChange = { lotStep = it },
                        label = { Text("Lot Step") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight, unfocusedTextColor = TextLight,
                            focusedBorderColor = TealLight, unfocusedBorderColor = BorderGray
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stepPoints,
                        onValueChange = { stepPoints = it },
                        label = { Text("Step Points") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight, unfocusedTextColor = TextLight,
                            focusedBorderColor = TealLight, unfocusedBorderColor = BorderGray
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxSpread,
                        onValueChange = { maxSpread = it },
                        label = { Text("Max Spread") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight, unfocusedTextColor = TextLight,
                            focusedBorderColor = TealLight, unfocusedBorderColor = BorderGray
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = recoveryThreshold,
                        onValueChange = { recoveryThreshold = it },
                        label = { Text("Recovery Thresh") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight, unfocusedTextColor = TextLight,
                            focusedBorderColor = TealLight, unfocusedBorderColor = BorderGray
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = expMultiplier,
                        onValueChange = { expMultiplier = it },
                        label = { Text("Exp Multiplier") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight, unfocusedTextColor = TextLight,
                            focusedBorderColor = TealLight, unfocusedBorderColor = BorderGray
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = client.copy(
                        initialLot = initialLot.toDoubleOrNull() ?: client.initialLot,
                        lotStep = lotStep.toDoubleOrNull() ?: client.lotStep,
                        stepPoints = stepPoints.toIntOrNull() ?: client.stepPoints,
                        maxSpread = maxSpread.toIntOrNull() ?: client.maxSpread,
                        recoveryThreshold = recoveryThreshold.toIntOrNull() ?: client.recoveryThreshold,
                        expMultiplier = expMultiplier.toDoubleOrNull() ?: client.expMultiplier
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) {
                Text("Simpan Konfig")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
            ) {
                Text("Batal")
            }
        },
        containerColor = CardDarkBg
    )
}

// Dialog: Extend Subscription License
@Composable
fun LicenseRenewalDialog(
    client: BotClient,
    onDismiss: () -> Unit,
    onRenew: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Perpanjangan Lisensi GOGOR", color = TextLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pilih masa penambahan lisensi premium untuk ${client.name}:", color = TextMuted, fontSize = 13.sp)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        onClick = { onRenew(30) },
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                    ) {
                        Text("+30 Hari")
                    }
                    Button(
                        onClick = { onRenew(90) },
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                    ) {
                        Text("+90 Hari")
                    }
                    Button(
                        onClick = { onRenew(365) },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldDark)
                    ) {
                        Text("+1 Tahun")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)) {
                Text("Tutup")
            }
        },
        containerColor = CardDarkBg
    )
}

// Dialog: Gemini AI Strategy Optimization
@Composable
fun GeminiOptimizationDialog(
    client: BotClient,
    uiState: GeminiUiState,
    onDismiss: () -> Unit,
    onRequestOptimization: (trend: String, riskStyle: String) -> Unit,
    onApplyOptimization: (GridRecommendation) -> Unit
) {
    var selectedTrend by remember { mutableStateOf("BULLISH (Up-Trend)") }
    var selectedRisk by remember { mutableStateOf("Conservative") }

    val trends = listOf("BULLISH (Up-Trend)", "BEARISH (Down-Trend)", "VOLATILE (Sangat Fluktuatif)", "RANGE-BOUND (Sideways)")
    val styles = listOf("Conservative", "Moderate", "Aggressive (High-Risk)")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = GoldLight)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gemini AI Grid Advisor", color = TextLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (uiState) {
                    is GeminiUiState.Idle -> {
                        Text("Optimalkan parameter trading grid hybrid GOGOR V12.24 untuk klien ${client.name} menggunakan analisis model kecerdasan buatan Gemini.", fontSize = 12.sp, color = TextMuted)
                        
                        Text("1. Kondisi Tren Pasar Saat Ini:", fontSize = 11.sp, color = TealLight, fontWeight = FontWeight.Bold)
                        trends.forEach { trend ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTrend = trend }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedTrend == trend,
                                    onClick = { selectedTrend = trend },
                                    colors = RadioButtonDefaults.colors(selectedColor = TealLight)
                                )
                                Text(trend, color = TextLight, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text("2. Profil Manajemen Resiko:", fontSize = 11.sp, color = TealLight, fontWeight = FontWeight.Bold)
                        styles.forEach { style ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedRisk = style }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedRisk == style,
                                    onClick = { selectedRisk = style },
                                    colors = RadioButtonDefaults.colors(selectedColor = TealLight)
                                )
                                Text(style, color = TextLight, fontSize = 12.sp)
                            }
                        }
                    }

                    is GeminiUiState.Loading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = TealLight)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Gemini sedang menganalisis kode GOGOR V12.24 dan menghitung parameter ideal...", color = TextLight, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                    }

                    is GeminiUiState.Success -> {
                        val rec = uiState.recommendation
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SlateDarkBg),
                                border = BorderStroke(1.dp, TealLight.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("HASIL REKOMENDASI AI:", color = TealLight, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("• Initial Lot Suggested: ${rec.initialLot}", color = TextLight, fontSize = 12.sp)
                                    Text("• Grid Step suggested: ${rec.stepPoints} points", color = TextLight, fontSize = 12.sp)
                                    Text("• Recovery Threshold: ${rec.recoveryThreshold} layers", color = TextLight, fontSize = 12.sp)
                                    Text("• Martingale Multiplier: x${rec.expMultiplier}", color = TextLight, fontSize = 12.sp)
                                    Text("• Max Allowed Spread: ${rec.maxSpread} pt", color = TextLight, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text("RATIONALE (Logika Resiko):", color = GoldLight, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(rec.rationale, color = TextLight, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }

                    is GeminiUiState.Error -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AlertRed, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Gagal Menghubungi Gemini AI", color = TextLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(uiState.message, color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (uiState) {
                is GeminiUiState.Idle -> {
                    Button(
                        onClick = {
                            val trendCleanStr = selectedTrend.substringBefore(" (")
                            val styleCleanStr = selectedRisk.substringBefore(" (")
                            onRequestOptimization(trendCleanStr, styleCleanStr)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                    ) {
                        Text("Mulai Analisis AI")
                    }
                }

                is GeminiUiState.Success -> {
                    Button(
                        onClick = { onApplyOptimization(uiState.recommendation) },
                        colors = ButtonDefaults.buttonColors(containerColor = TealLight),
                        modifier = Modifier.testTag("apply_ai_optimized_btn")
                    ) {
                        Text("Gunakan Hasil Optimasi", color = SlateDarkBg, fontWeight = FontWeight.Bold)
                    }
                }

                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)) {
                Text("Tutup")
            }
        },
        containerColor = CardDarkBg
    )
}

// Google Sign-In Gating Screen Component
@Composable
fun GoogleSignInScreen(
    onSignIn: (email: String, name: String) -> Unit
) {
    var showAccountPicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDarkBg),
        contentAlignment = Alignment.Center
    ) {
        // Aesthetic backdrop subtle glowing golden light radial gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(TealPrimary.copy(alpha = 0.12f), Color.Transparent),
                        radius = 1200f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Elegant gold padlock / shield icon representing security key components
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(CardDarkBg)
                    .border(1.dp, TealLight.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = TealLight,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sub-header
            Text(
                text = "GOGOR BOT CONTROL V12.24",
                color = TealLight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Sistem Kontrol & AI Gateway",
                color = TextLight,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Masuk menggunakan Akun Google Anda untuk mengakses panel control center secara aman dan mengonfigurasi bot.",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Beautiful custom Google Sign-In Button complying with premium dark design
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clickable { showAccountPicker = true }
                    .testTag("google_signin_btn"),
                shape = RoundedCornerShape(27.dp),
                border = BorderStroke(1.dp, BorderGray),
                colors = CardDefaults.cardColors(containerColor = CardDarkBg)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Google Brand Multi-Color bar representation
                    Row(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF4285F4))) // Blue
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF34A853))) // Green
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFFBBC05))) // Yellow
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFEA4335))) // Red
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = "Masuk dengan Google",
                        color = TextLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom trust label
            Text(
                text = "SECURE GOOGLE AUTHSYSTEM ENABLED",
                color = TextMuted.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }

    // Intercept account picker simulated dialog
    if (showAccountPicker) {
        AlertDialog(
            onDismissRequest = { showAccountPicker = false },
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pilih Akun",
                        color = TextLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Serif
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "untuk melanjutkan ke GOGOR Bot Control",
                        color = TextMuted,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pre-detected correct active Google Account from prompt metadata information
                    GoogleAccountOption(
                        name = "Andrianto Wijaya",
                        email = "andrytambak13@gmail.com",
                        onClick = {
                            showAccountPicker = false
                            onSignIn("andrytambak13@gmail.com", "Andrianto Wijaya")
                        }
                    )

                    // Optional demo fallbacks to guarantee rich options feel
                    GoogleAccountOption(
                        name = "Satria Trader",
                        email = "satria.trade@gmail.com",
                        onClick = {
                            showAccountPicker = false
                            onSignIn("satria.trade@gmail.com", "Satria Trader")
                        }
                    )

                    HorizontalDivider(color = BorderGray, thickness = 1.dp)

                    // Option to add/switch account
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = TealLight,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Gunakan akun lain",
                            color = TealLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showAccountPicker = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
                ) {
                    Text("Batal")
                }
            },
            containerColor = CardDarkBg,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun GoogleAccountOption(
    name: String,
    email: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SlateDarkBg),
        border = BorderStroke(1.dp, BorderGray)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(TealPrimary.copy(alpha = 0.2f))
                    .border(1.dp, TealLight.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    color = TealLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = TextLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = email,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = TextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// Interactive secure loader splash
@Composable
fun AuthLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDarkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = TealLight,
                strokeWidth = 3.dp,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Menghubungkan ke Layanan Google...",
                color = TextLight,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Mengautentikasi kunci lisensi digital",
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

// Security rejection/block screen for unauthorized Google accounts
@Composable
fun GoogleSignInBlockedScreen(
    message: String,
    email: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDarkBg),
        contentAlignment = Alignment.Center
    ) {
        // Aesthetic red danger aura
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(AlertRed.copy(alpha = 0.15f), Color.Transparent),
                        radius = 1200f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Elegant red shield/lock symbol of security restriction
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(CardDarkBg)
                    .border(2.dp, AlertRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AlertRed,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "AKSES DITOLAK KERAS",
                color = AlertRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Otorisasi Diperlukan",
                color = TextLight,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = message,
                color = TextMuted,
                fontSize = 12.5.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Back to Sign In button
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("dismiss_block_btn"),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                border = BorderStroke(1.dp, TextLight.copy(alpha = 0.2f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        tint = TextLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Kembali ke Login",
                        color = TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// Dynamic license warnings notification card for sub expired limits
@Composable
fun ExpiringSubscriptionBanner(
    notifications: List<String>,
    modifier: Modifier = Modifier
) {
    if (notifications.isEmpty()) return
    
    val alertText = "Lisensi Hampir Habis: " + notifications.joinToString(", ")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("expiring_subscription_banner"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = AlertRed.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Peringatan",
                tint = AlertRed,
                modifier = Modifier.size(14.dp)
            )
            
            Spacer(modifier = Modifier.width(6.dp))
            
            Text(
                text = alertText,
                color = TextLight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// Custom Premium Segmented Tab Button
@Composable
fun TabButton(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isSelected) TealPrimary else Color.Transparent
    val tc = if (isSelected) TextLight else TextMuted
    val ic = if (isSelected) TextLight else TextMuted
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ic,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = tc,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// New SubscriberManager Screen Section View
@Composable
fun SubscriberManager(
    clients: List<BotClient>,
    onRenewLicense: (BotClient) -> Unit,
    onTriggerAi: (BotClient) -> Unit,
    onEditConfig: (BotClient) -> Unit,
    viewModel: BotManagerViewModel,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(0) } // 0: Semua, 1: Aktif, 2: Hampir Habis, 3: Kadaluwarsa
    
    // Calculate stats
    val totalSubs = clients.size
    val activeSubs = clients.count { 
        val diff = it.expiryTimestamp - System.currentTimeMillis()
        val days = diff / (24L * 60 * 60 * 1000)
        it.isLicenseActive && days > 15 
    }
    val expiringSubs = clients.count { 
        val diff = it.expiryTimestamp - System.currentTimeMillis()
        val days = diff / (24L * 60 * 60 * 1000)
        it.isLicenseActive && days in 0L..15L 
    }
    val expiredSubs = clients.count { 
        val diff = it.expiryTimestamp - System.currentTimeMillis()
        val days = diff / (24L * 60 * 60 * 1000)
        !it.isLicenseActive || days <= 0 
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("subscriber_manager_container")
    ) {
        // High fidelity subscription analytics cards row
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MiniStatCard(title = "Total Klien", value = "$totalSubs", color = TextLight, modifier = Modifier.weight(1.3f))
            MiniStatCard(title = "Aktif", value = "$activeSubs", color = SuccessGreen, modifier = Modifier.weight(1.0f))
            MiniStatCard(title = "Hampir Habis", value = "$expiringSubs", color = TealLight, modifier = Modifier.weight(1.2f))
            MiniStatCard(title = "Kadaluwarsa", value = "$expiredSubs", color = AlertRed, modifier = Modifier.weight(1.2f))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search text field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari subscriber bot berdasarkan nama atau Telegram...", color = TextMuted, fontSize = 11.5.sp) },
            leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null, tint = TextMuted, modifier = Modifier.size(15.dp)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("subscriber_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardDarkBg,
                unfocusedContainerColor = CardDarkBg,
                focusedBorderColor = TealPrimary,
                unfocusedBorderColor = BorderGray,
                focusedTextColor = TextLight,
                unfocusedTextColor = TextLight
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Custom chip horizontal row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Semua", "Aktif", "Hampir Habis", "Kadaluwarsa").forEachIndexed { index, label ->
                val isSelected = selectedFilter == index
                val bg = if (isSelected) TealPrimary.copy(alpha = 0.15f) else CardDarkBg
                val bc = if (isSelected) TealPrimary else BorderGray
                val tc = if (isSelected) TealLight else TextMuted
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(bg)
                        .border(1.dp, bc, RoundedCornerShape(18.dp))
                        .clickable { selectedFilter = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = tc,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter and Search clients
        val filteredClients = clients.filter { client ->
            val matchQuery = client.name.contains(searchQuery, ignoreCase = true) || 
                             client.telegram.contains(searchQuery, ignoreCase = true)
                             
            val diff = client.expiryTimestamp - System.currentTimeMillis()
            val days = diff / (24L * 60 * 60 * 1000)
            
            val matchFilter = when (selectedFilter) {
                0 -> true
                1 -> client.isLicenseActive && days > 15
                2 -> client.isLicenseActive && days in 0..15
                3 -> !client.isLicenseActive || days <= 0
                else -> true
            }
            
            matchQuery && matchFilter
        }

        Text(
            text = "DAFTAR SUBSCRIBER AKTIF GOGOR MT5 (${filteredClients.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = TextMuted,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (filteredClients.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tidak ada subscriber yang cocok dengan filter", color = TextMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredClients, key = { it.id }) { client ->
                        SubscriberCard(
                            client = client,
                            onRenewLicense = { onRenewLicense(client) },
                            onTriggerAi = { onTriggerAi(client) },
                            onEditConfig = { onEditConfig(client) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniStatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardDarkBg),
        border = BorderStroke(1.dp, BorderGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun SubscriberCard(
    client: BotClient,
    onRenewLicense: () -> Unit,
    onTriggerAi: () -> Unit,
    onEditConfig: () -> Unit
) {
    val df = DecimalFormat("#,##0.00")
    val diff = client.expiryTimestamp - System.currentTimeMillis()
    val daysRemaining = (diff / (24L * 60 * 60 * 1000)).coerceAtLeast(0)
    
    val isExpired = !client.isLicenseActive || diff <= 0
    val isExpiringSoon = client.isLicenseActive && diff > 0 && daysRemaining <= 15
    
    val statusLabel = when {
        isExpired -> "EXPIRED"
        isExpiringSoon -> "EXPIRING"
        else -> "ACTIVE"
    }
    
    val statusColor = when {
        isExpired -> AlertRed
        isExpiringSoon -> TealLight
        else -> SuccessGreen
    }
    
    val validityProgress = if (isExpired) 0.0f else {
        (daysRemaining.toFloat() / 30.0f).coerceIn(0.0f, 1.0f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("subscriber_card_${client.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDarkBg),
        border = BorderStroke(1.dp, BorderGray)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = client.name,
                        color = TextLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = client.telegram,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(TextMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MT5 ID: ${108000 + client.id * 17}",
                            color = TealLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                
                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 8.5.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics specs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("TOTAL BALANCE", color = TextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    Text("$${df.format(client.balance)}", color = TextLight, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ACTIVE MODE", color = TextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    Text(client.activeMode.replace("MODE_", ""), color = TealLight, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("DRAWDOWN STATUS", color = TextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    Text("${df.format(client.drawdown)}%", color = if (client.drawdown > 5.0) AlertRed else SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // License Indicator Progress Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val expDateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(client.expiryTimestamp))
                    Text(
                        text = "Kadaluwarsa: $expDateStr",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                    Text(
                        text = if (isExpired) "Lisensi Habis" else "$daysRemaining Hari Tersisa",
                        color = if (isExpiringSoon || isExpired) AlertRed else TealLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { validityProgress },
                    color = statusColor,
                    trackColor = BorderGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subscriber action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Secondary Edit Config Button
                OutlinedButton(
                    onClick = onEditConfig,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    shape = RoundedCornerShape(17.dp),
                    border = BorderStroke(1.dp, BorderGray),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Parameter", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // AI Optimizations Button
                OutlinedButton(
                    onClick = onTriggerAi,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    shape = RoundedCornerShape(17.dp),
                    border = BorderStroke(1.dp, BorderGray),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TealLight),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = TealLight, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AI Optimasi", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TealLight)
                }

                // Primary Renew button
                Button(
                    onClick = onRenewLicense,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(34.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isExpired || isExpiringSoon) AlertRed else TealPrimary),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Simpan Lisensi", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun Mql5SyncScreen(
    viewModel: BotManagerViewModel,
    modifier: Modifier = Modifier
) {
    val isMqlServerRunning by viewModel.isMqlServerRunning.collectAsStateWithLifecycle()
    val mqlServerUrl by viewModel.mqlServerUrl.collectAsStateWithLifecycle()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val mqlCode = """
//+------------------------------------------------------------------+
//|                                              GogorSyncClient.mq5 |
//|                                  Copyright 2026, Gogor Bot Owner |
//|                                             https://gogor-bot.id |
//+------------------------------------------------------------------+
#property copyright "Copyright 2026, Gogor Bot Owner"
#property link      "https://gogor-bot.id"
#property version   "1.24"

//--- Inputs
input string   TelegramID       = "@andrian13";      // Telegram ID Anda
input string   Name             = "Klien GOGOR";     // Nama Anda
input string   SyncServerURL    = "$mqlServerUrl";  // URL Server Sinkronisasi Anda
input int      SyncIntervalSec  = 10;                // Sinkronisasi setiap (detik)

//+------------------------------------------------------------------+
//| Expert initialization function                                   |
//+------------------------------------------------------------------+
int OnInit()
{
   Print("GOGOR Sync Client diinisialisasi untuk: ", TelegramID);
   EventSetTimer(SyncIntervalSec);
   PerformSync();
   return(INIT_SUCCEEDED);
}

//+------------------------------------------------------------------+
//| Expert deinitialization function                                 |
//+------------------------------------------------------------------+
void OnDeinit(const int reason)
{
   EventKillTimer();
}

//+------------------------------------------------------------------+
//| Timer function                                                   |
//+------------------------------------------------------------------+
void OnTimer()
{
   PerformSync();
}

//+------------------------------------------------------------------+
//| Sinkronisasi MQL5 dengan Aplikasi                               |
//+------------------------------------------------------------------+
void PerformSync()
{
   double balance  = AccountInfoDouble(ACCOUNT_BALANCE);
   double equity   = AccountInfoDouble(ACCOUNT_EQUITY);
   double drawdown = 0.0;
   
   if(balance > 0.0) {
      drawdown = ((balance - equity) / balance) * 100.0;
      if(drawdown < 0.0) drawdown = 0.0;
   }
   
   string request_url = SyncServerURL + "?telegram=" + TelegramID +
                        "&name=" + Name +
                        "&balance=" + DoubleToString(balance, 2) +
                        "&equity=" + DoubleToString(equity, 2) +
                        "&drawdown=" + DoubleToString(drawdown, 2);
   
   char data[];
   char result[];
   string result_headers;
   
   int res = WebRequest("GET", request_url, NULL, 5000, data, result, result_headers);
   
   if(res == -1) {
      int error_code = GetLastError();
      Print("Gagal sinkronisasi GOGOR Control. Error code: ", error_code);
      if(error_code == 4014) {
         Print("SILAKAN TAMBAHKAN URL '", SyncServerURL, "' ke dalam tab Allowed WebRequest di MetaTrader Opsi!");
      }
   } else {
      string response_body = CharArrayToString(result, 0, WHOLE_ARRAY, CP_UTF8);
      Print("Sinkronisasi Sukses! Konfigurasi: ", response_body);
   }
}
""".trimIndent()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("mql5_sync_container"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Server Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                border = BorderStroke(1.dp, BorderGray)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "SERVER SINKRONISASI MQL5",
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isMqlServerRunning) SuccessGreen else AlertRed)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (isMqlServerRunning) "SERVER AKTIF (SIAP KONEK)" else "SERVER MATIKAN",
                                    color = if (isMqlServerRunning) SuccessGreen else AlertRed,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        
                        Button(
                            onClick = { viewModel.toggleMqlServer() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMqlServerRunning) AlertRed.copy(alpha = 0.2f) else TealPrimary,
                                contentColor = if (isMqlServerRunning) AlertRed else TextLight
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = if (isMqlServerRunning) BorderStroke(1.dp, AlertRed) else null,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                if (isMqlServerRunning) "Hentikan Server" else "Aktifkan Server",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (isMqlServerRunning) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = BorderGray, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Text(
                            "ALAMAT WEB REQUEST (MASUKKAN PADA METATRADER 5)",
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SlateDarkBg, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                mqlServerUrl,
                                color = TealLight,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(mqlServerUrl))
                                    android.widget.Toast.makeText(context, "URL disalin!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Copy URL",
                                    tint = TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Integration tutorial step-by-step
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                border = BorderStroke(1.dp, BorderGray)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "PANDUAN SINKRONISASI",
                        fontWeight = FontWeight.Bold,
                        color = TextLight,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    val steps = listOf(
                        "Buka Platform PC/VPS MetaTrader 5 Anda.",
                        "Masuk Menu: Tools -> Options -> Expert Advisors.",
                        "Centang opsi: 'Allow WebRequest for listed URL'.",
                        "Tambahkan alamat URL server di atas ke dalam daftar tersebut.",
                        "Di MetaEditor, buat script Expert Advisor baru, paste kode MQL5 di bawah, dan klik Compile."
                    )
                    steps.forEachIndexed { index, text ->
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(TealPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${index + 1}",
                                    color = TealLight,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text,
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. MQL5 Script Copier Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                border = BorderStroke(1.dp, BorderGray)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "KODE INTEGRASI MQL5 (SINKRONISASI)",
                            fontWeight = FontWeight.Bold,
                            color = TextLight,
                            fontSize = 11.sp
                        )
                        
                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(mqlCode))
                                android.widget.Toast.makeText(context, "Kode MQL5 disalin ke papan klip!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Salin Kode", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(SlateDarkBg, RoundedCornerShape(8.dp))
                            .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        Text(
                            text = mqlCode,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            color = TextLight.copy(alpha = 0.85f),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleSheetsSyncScreen(
    viewModel: BotManagerViewModel,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val googleSheetUrl by viewModel.googleSheetUrl.collectAsStateWithLifecycle()
    val isSheetAutoSyncEnabled by viewModel.isSheetAutoSyncEnabled.collectAsStateWithLifecycle()
    val isSyncingSheet by viewModel.isSyncingSheet.collectAsStateWithLifecycle()
    val syncStatusText by viewModel.syncStatusText.collectAsStateWithLifecycle()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    val sheetCode = """/*
  ============================================================
   GOGOR Bot Database Sync Script (Google Sheets Web App)
  ============================================================
   Panduan Pemasangan:
   1. Buka Google Sheet baru atau yang sudah ada.
   2. Klik Ekstensi -> Apps Script.
   3. Hapus kode default, paste seluruh kode ini.
   4. Klik "Terapkan" -> "Terapkan Baru".
   5. Di kolom 'Jenis', pilih "Aplikasi Web".
   6. Di kolom 'Siapa yang memiliki akses', pilih "Siapa saja".
   7. Klik Terapkan, berikan izin akses, lalu salin URL Web App yang dihasilkan.
   8. Tempel URL tersebut ke kolom "Google Sheet URL" di Aplikasi GOGOR Android.
*/

function doGet(e) {
  var action = e.parameter.action;
  if (action == "read") return getClientsJson();
  if (action == "update_client") return handleSingleClientUpdate(e.parameter);
  return ContentService.createTextOutput(JSON.stringify({status: "error", message: "Aksi tidak dikenal"}))
    .setMimeType(ContentService.MimeType.JSON);
}

function doPost(e) {
  var action = e.parameter.action;
  if (action == "sync" || !action) {
    try {
      var postData = JSON.parse(e.postData.contents);
      var clients = Array.isArray(postData) ? postData : (postData.clients || []);
      return syncAllClients(clients);
    } catch(err) {
      return ContentService.createTextOutput(JSON.stringify({status: "error", message: err.message}))
        .setMimeType(ContentService.MimeType.JSON);
    }
  }
  return ContentService.createTextOutput(JSON.stringify({status: "error", message: "Aksi posting tidak dikenal"}))
    .setMimeType(ContentService.MimeType.JSON);
}

function getClientsJson() {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
  var data = sheet.getDataRange().getValues();
  if (data.length <= 1) return ContentService.createTextOutput(JSON.stringify([])).setMimeType(ContentService.MimeType.JSON);
  var headers = data[0];
  var clientsList = [];
  for (var i = 1; i < data.length; i++) {
    var row = data[i];
    var client = {};
    for (var j = 0; j < headers.length; j++) {
      var headerName = headers[j].toString().trim();
      var val = row[j];
      if (headerName == "id") val = Number(val);
      else if (headerName == "balance") val = Number(val);
      else if (headerName == "equity") val = Number(val);
      else if (headerName == "drawdown") val = Number(val);
      else if (headerName == "isLicenseActive" || headerName == "license_active") val = (val === true || val === "true" || val == 1);
      else if (headerName == "expiryTimestamp") val = Number(val);
      else if (headerName == "lastActiveTime") val = Number(val);
      else if (headerName == "initialLot") val = Number(val);
      else if (headerName == "lotStep") val = Number(val);
      else if (headerName == "stepPoints") val = Number(val);
      else if (headerName == "maxSpread") val = Number(val);
      else if (headerName == "recoveryThreshold") val = Number(val);
      else if (headerName == "expMultiplier") val = Number(val);
      client[headerName] = val;
    }
    if (client.telegram) clientsList.push(client);
  }
  return ContentService.createTextOutput(JSON.stringify(clientsList)).setMimeType(ContentService.MimeType.JSON);
}

function handleSingleClientUpdate(params) {
  var sheet = getOrCreateSheet();
  var headers = getHeaders(sheet);
  var telegram = params.telegram;
  if (!telegram) return createJsonResponse({status: "error", message: "Telegram ID required"});
  var data = sheet.getDataRange().getValues();
  var telegramColIndex = headers.indexOf("telegram");
  var rowIndex = -1;
  if (telegramColIndex != -1) {
    for (var i = 1; i < data.length; i++) {
      if (data[i][telegramColIndex].toString().toLowerCase() == telegram.toLowerCase()) {
        rowIndex = i + 1;
        break;
      }
    }
  }
  var rowValues = [];
  for (var j = 0; j < headers.length; j++) {
    var h = headers[j];
    var val = params[h] !== undefined ? params[h] : "";
    if (h == "isLicenseActive") val = (val === "true" || val === true || val == 1);
    else if (["id", "balance", "equity", "drawdown", "expiryTimestamp", "lastActiveTime", "initialLot", "lotStep", "stepPoints", "maxSpread", "recoveryThreshold", "expMultiplier"].indexOf(h) != -1) {
      if (val !== "") val = Number(val);
    }
    rowValues.push(val);
  }
  if (rowIndex != -1) {
    var existingRow = data[rowIndex - 1];
    for (var col = 0; col < headers.length; col++) {
      var hName = headers[col];
      var newVal = params[hName];
      if (newVal === undefined) rowValues[col] = existingRow[col];
    }
    sheet.getRange(rowIndex, 1, 1, headers.length).setValues([rowValues]);
  } else {
    sheet.appendRow(rowValues);
  }
  return createJsonResponse({status: "success", message: "Client updated on sheet."});
}

function syncAllClients(clients) {
  var sheet = getOrCreateSheet();
  var headers = getHeaders(sheet);
  sheet.clearContents();
  sheet.appendRow(headers);
  if (clients.length > 0) {
    var rows = [];
    for (var i = 0; i < clients.length; i++) {
      var client = clients[i];
      var row = [];
      for (var j = 0; j < headers.length; j++) {
        var h = headers[j];
        var val = client[h];
        if (val === undefined) val = "";
        row.push(val);
      }
      rows.push(row);
    }
    sheet.getRange(2, 1, rows.length, headers.length).setValues(rows);
  }
  return createJsonResponse({status: "success", message: "Synchronized successfully."});
}

function getOrCreateSheet() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getActiveSheet();
  if(!sheet) sheet = ss.insertSheet("Clients");
  return sheet;
}

function getHeaders(sheet) {
  var data = sheet.getDataRange().getValues();
  if (data.length > 0 && data[0].length > 1) {
    var headers = data[0].map(function(h) { return h.toString().trim(); });
    if (headers.indexOf("telegram") != -1) return headers;
  }
  return [
    "id", "name", "telegram", "balance", "equity", "drawdown",
    "isLicenseActive", "expiryTimestamp", "lastActiveTime", "activeMode",
    "initialLot", "lotStep", "stepPoints", "maxSpread", "recoveryThreshold", "expMultiplier"
  ];
}

function createJsonResponse(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}"""

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("google_sheets_sync_scroll"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(TealPrimary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = TealLight,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Sinkronisasi Google Sheet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextLight
                        )
                        Text(
                            text = "Gunakan Google Sheet sebagai remote database cloud Anda.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "KONFIGURASI STRUKTUR CLOUD",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealLight,
                        letterSpacing = 1.sp
                    )

                    OutlinedTextField(
                        value = googleSheetUrl,
                        onValueChange = { viewModel.updateGoogleSheetUrl(it) },
                        label = { Text("Google Sheet Web App URL", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            unfocusedBorderColor = BorderGray,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_sheet_url")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Penyelarasan Otomatis (Real-time)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextLight
                            )
                            Text(
                                text = "Kirim data baru otomatis saat MQL5/lokal terupdate",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = isSheetAutoSyncEnabled,
                            onCheckedChange = { viewModel.toggleSheetAutoSync() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TealLight,
                                checkedTrackColor = TealPrimary
                            ),
                            modifier = Modifier.testTag("switch_auto_sync")
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "PANEL PENYELARASAN MANUAL (SYNC)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealLight,
                        letterSpacing = 1.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateDarkBg, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (isSyncingSheet) {
                                CircularProgressIndicator(
                                    color = TealLight,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(if (syncStatusText.contains("Berhasil") || syncStatusText.contains("Selesai")) SuccessGreen else Color.Yellow, CircleShape)
                                )
                            }
                            Text(
                                text = syncStatusText,
                                fontSize = 12.sp,
                                color = TextLight,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.pushAllToGoogleSheet() },
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSyncingSheet,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_push_sheet")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Push Ke Sheet", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.pullFromGoogleSheet() },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateDarkBg),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, TealPrimary),
                            enabled = !isSyncingSheet,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_pull_sheet")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = TealLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pull Dari Sheet", color = TealLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDarkBg),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "KODE GOOGLE APPS SCRIPT",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealLight,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Tempel kode berikut di lembar Apps Script Anda.",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        
                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(sheetCode))
                                android.widget.Toast.makeText(context, "Script disalin ke papan klip!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Salin Script", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(SlateDarkBg, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        Text(
                            text = sheetCode,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            color = TextLight.copy(alpha = 0.85f),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        )
                    }
                }
            }
        }
    }
}
