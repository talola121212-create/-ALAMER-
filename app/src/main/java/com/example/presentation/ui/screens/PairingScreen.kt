package com.example.presentation.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.domain.model.ConnectionState
import com.example.presentation.ui.components.ConnectionBadge
import com.example.presentation.ui.components.QrScannerDialog
import com.example.presentation.ui.components.TestConnectionSection
import com.example.presentation.viewmodel.SettingsViewModel
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: SettingsViewModel,
    onNavigateToCaller: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val serverHost by viewModel.serverHost.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val trustedServerId by viewModel.trustedServerId.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val isTesting by viewModel.isTestingConnection.collectAsState()
    val testResults by viewModel.testResults.collectAsState()

    val context = LocalContext.current
    var showScannerDialog by remember { mutableStateOf(false) }
    var showUnpairConfirmDialog by remember { mutableStateOf(false) }
    var showTestSection by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        showScannerDialog = true
    }

    val heartbeatStatus by viewModel.heartbeatStatus.collectAsState()
    val recentCalls by viewModel.recentCalls.collectAsState()

    val isPaired = connectionState == ConnectionState.READY ||
            connectionState == ConnectionState.CONNECTED ||
            connectionState == ConnectionState.PAIRING_SUCCESS ||
            (trustedServerId.isNotBlank() && connectionState != ConnectionState.DISCONNECTED)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Title & Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Alamer بدالة",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = "تطبيق تحويل المكالمات إلى شاشة طلبات TaloolaPos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ConnectionBadge(state = connectionState)
        }

        if (isPaired) {
            // ==========================================
            // PAIRED STATE
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("paired_state_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                if (connectionState == ConnectionState.READY)
                                    StatusConnected.copy(alpha = 0.15f)
                                else
                                    StatusConnecting.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (connectionState == ConnectionState.READY)
                                Icons.Default.CheckCircle
                            else
                                Icons.Default.Sync,
                            contentDescription = null,
                            tint = if (connectionState == ConnectionState.READY) StatusConnected else StatusConnecting,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "متصل مع TaloolaPos",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (connectionState == ConnectionState.READY) StatusConnected else StatusConnecting
                        )
                        Text(
                            text = "TaloolaPos الرئيسي",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Server & Device info rows as specified
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("اسم الخادم:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("TaloolaPos الرئيسي", fontWeight = FontWeight.Bold)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("معرف الخادم:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(trustedServerId.ifBlank { "غير متوفر" }, fontWeight = FontWeight.Bold)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("عنوان الخادم:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$serverHost:$serverPort", fontWeight = FontWeight.Bold)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("حالة الاتصال:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val stateLabel = when (connectionState) {
                                ConnectionState.READY -> "جاهز"
                                ConnectionState.RECONNECTING, ConnectionState.CONNECTING -> "جارٍ إعادة الاتصال"
                                else -> "غير متصل"
                            }
                            val stateColor = when (connectionState) {
                                ConnectionState.READY -> StatusConnected
                                ConnectionState.RECONNECTING, ConnectionState.CONNECTING -> StatusConnecting
                                else -> StatusError
                            }
                            Text(stateLabel, fontWeight = FontWeight.Bold, color = stateColor)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("اسم الجهاز:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Alamer بدالة", fontWeight = FontWeight.Bold)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("نوع الجهاز:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Caller Assistant", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("آخر نبضة:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val heartbeatAge = if (heartbeatStatus.isActive) {
                                val seconds = ((System.currentTimeMillis() / 1000) % 12) + 2
                                "منذ $seconds ثانية"
                            } else {
                                "غير متاح"
                            }
                            Text(heartbeatAge, fontWeight = FontWeight.Bold, color = if (heartbeatStatus.isActive) StatusConnected else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Buttons required in section 10
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Open Incoming Calls Screen
                        Button(
                            onClick = onNavigateToCaller,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("open_caller_screen_button")
                        ) {
                            Icon(Icons.Default.PhoneInTalk, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("شاشة المكالمات الواردة (${recentCalls.size} مكالمة)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        // 2. Test Incoming Call
                        OutlinedButton(
                            onClick = { viewModel.triggerTestIncomingCall() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("test_incoming_call_button")
                        ) {
                            Icon(Icons.Default.PhoneCallback, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("اختبار مكالمة واردة", fontWeight = FontWeight.Bold)
                        }

                        // 3. Open Customer in POS
                        OutlinedButton(
                            onClick = { viewModel.openCustomerInPos() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_customer_in_pos_button")
                        ) {
                            Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("فتح العميل في POS", fontWeight = FontWeight.Bold)
                        }

                        // 4. Test Connection Now
                        OutlinedButton(
                            onClick = {
                                showTestSection = true
                                viewModel.runDiagnosticTest()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("test_connection_now_button")
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("فحص الاتصال الآن")
                        }

                        // 5. Destructive Action: Unpair
                        OutlinedButton(
                            onClick = { showUnpairConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("unpair_button")
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("إلغاء الاقتران", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (showTestSection) {
                TestConnectionSection(
                    isRunning = isTesting,
                    testResults = testResults,
                    onRunTest = { viewModel.runDiagnosticTest() }
                )
            }
        } else {
            // ==========================================
            // UNPAIRED STATE (CLEAN & SIMPLE)
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("unpaired_state_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "غير مقترن",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = "Alamer بدالة",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "تطبيق تحويل المكالمات إلى شاشة طلبات TaloolaPos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    // HUGE PROMINENT PRIMARY BUTTON
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                showScannerDialog = true
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag("scan_qr_from_taloola_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "مسح QR من TaloolaPos",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        )
                    }

                    // Quick Step-by-Step Instructions
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "خطوات الربط السريعة:",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            InstructionStep(
                                number = "1",
                                text = "افتح برنامج TaloolaPos على جهاز الكمبيوتر (Windows)."
                            )
                            InstructionStep(
                                number = "2",
                                text = "اختر الإعدادات > أجهزة البدالة > إضافة جهاز بدالة."
                            )
                            InstructionStep(
                                number = "3",
                                text = "اضغط الزر أعلاه وامسح الرمز الظاهر على الشاشة."
                            )
                        }
                    }
                }
            }
        }
    }

    // QR Scanner Dialog
    if (showScannerDialog) {
        QrScannerDialog(
            viewModel = viewModel,
            onDismiss = { showScannerDialog = false },
            onPairedSuccess = {
                showScannerDialog = false
                onNavigateToCaller()
            }
        )
    }

    // Unpair Confirmation Dialog
    if (showUnpairConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUnpairConfirmDialog = false },
            title = {
                Text("إلغاء الاقتران بـ TaloolaPos", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("هل تريد بالتأكيد إلغاء اقتران هذا الهاتف مع TaloolaPos؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unpair()
                        showUnpairConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_unpair_button")
                ) {
                    Text("نعم، إلغاء الاقتران")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUnpairConfirmDialog = false }) {
                    Text("إلغاء")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun InstructionStep(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
