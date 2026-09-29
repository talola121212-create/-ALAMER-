package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.ui.components.ConnectionBadge
import com.example.presentation.ui.screens.*
import com.example.presentation.viewmodel.CallerViewModel
import com.example.presentation.viewmodel.SettingsViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val callerViewModel: CallerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingCallIntent(intent)

        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainScreen(
                        callerViewModel = callerViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingCallIntent(intent)
    }

    private fun handleIncomingCallIntent(intent: Intent?) {
        val phone = intent?.getStringExtra("EXTRA_INCOMING_PHONE")
        if (!phone.isNullOrBlank()) {
            callerViewModel.simulateCall(phone)
        }
    }
}

enum class NavigationTab(val title: String) {
    CALLS("المكالمات"),
    PAIRING("ربط الجهاز"),
    DIAGNOSTICS("التشخيص"),
    SETTINGS("الإعدادات")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    callerViewModel: CallerViewModel,
    settingsViewModel: SettingsViewModel
) {
    var selectedTab by remember { mutableStateOf(NavigationTab.CALLS) }
    val snackbarHostState = remember { SnackbarHostState() }
    val connectionState by settingsViewModel.connectionState.collectAsState()

    // Observe user messages
    LaunchedEffect(Unit) {
        callerViewModel.userMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    LaunchedEffect(Unit) {
        settingsViewModel.message.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Alamer بدالة",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = if (connectionState == com.example.domain.model.ConnectionState.READY)
                                "متصل مع TaloolaPos"
                            else
                                "مساعد البدالة لـ TaloolaPos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    ConnectionBadge(
                        state = connectionState,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.CALLS,
                    onClick = { selectedTab = NavigationTab.CALLS },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == NavigationTab.CALLS) Icons.Filled.PhoneInTalk else Icons.Outlined.PhoneInTalk,
                            contentDescription = NavigationTab.CALLS.title
                        )
                    },
                    label = { Text(NavigationTab.CALLS.title, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_calls")
                )

                NavigationBarItem(
                    selected = selectedTab == NavigationTab.PAIRING,
                    onClick = { selectedTab = NavigationTab.PAIRING },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == NavigationTab.PAIRING) Icons.Filled.QrCodeScanner else Icons.Outlined.QrCodeScanner,
                            contentDescription = NavigationTab.PAIRING.title
                        )
                    },
                    label = { Text(NavigationTab.PAIRING.title, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_pairing")
                )

                NavigationBarItem(
                    selected = selectedTab == NavigationTab.DIAGNOSTICS,
                    onClick = { selectedTab = NavigationTab.DIAGNOSTICS },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == NavigationTab.DIAGNOSTICS) Icons.Filled.Speed else Icons.Outlined.Speed,
                            contentDescription = NavigationTab.DIAGNOSTICS.title
                        )
                    },
                    label = { Text(NavigationTab.DIAGNOSTICS.title, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_diagnostics")
                )

                NavigationBarItem(
                    selected = selectedTab == NavigationTab.SETTINGS,
                    onClick = { selectedTab = NavigationTab.SETTINGS },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == NavigationTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = NavigationTab.SETTINGS.title
                        )
                    },
                    label = { Text(NavigationTab.SETTINGS.title, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                NavigationTab.CALLS -> {
                    CallerScreen(viewModel = callerViewModel)
                }
                NavigationTab.PAIRING -> {
                    PairingScreen(
                        viewModel = settingsViewModel,
                        onNavigateToCaller = { selectedTab = NavigationTab.CALLS }
                    )
                }
                NavigationTab.DIAGNOSTICS -> {
                    DiagnosticsScreen(viewModel = settingsViewModel)
                }
                NavigationTab.SETTINGS -> {
                    SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }
}
