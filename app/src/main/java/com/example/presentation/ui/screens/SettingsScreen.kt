package com.example.presentation.ui.screens

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.ConnectionState
import com.example.presentation.ui.components.ConnectionBadge
import com.example.presentation.ui.components.TestConnectionSection
import com.example.presentation.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val serverHost by viewModel.serverHost.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val trustedServerId by viewModel.trustedServerId.collectAsState()
    val operatorAccount by viewModel.operatorAccount.collectAsState()
    val isMockMode by viewModel.isMockMode.collectAsState()
    val autoOpenDetails by viewModel.autoOpenDetails.collectAsState()
    val pendingCount by viewModel.pendingSyncCount.collectAsState()
    val discoveredServers by viewModel.discoveredServers.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val isTesting by viewModel.isTestingConnection.collectAsState()
    val testResults by viewModel.testResults.collectAsState()

    var showTestDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showServerCenterDialog by remember { mutableStateOf(false) }
    var showDetailsToggles by remember { mutableStateOf(true) }

    // RoleManager launcher for Call Screening
    val callScreeningRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Screen Header
        Text(
            text = "إعدادات Alamer بدالة",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )

        // القسم الأول: الاتصال
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "القسم الأول: الاتصال",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    ConnectionBadge(state = connectionState)
                }

                Text(text = "عنوان الخادم: $serverHost", style = MaterialTheme.typography.bodyMedium)
                Text(text = "المنفذ: $serverPort", style = MaterialTheme.typography.bodyMedium)
                Text(text = "إصدار البروتوكول: 1.0 (TALOOLA_POS_V3)", style = MaterialTheme.typography.bodySmall)

                OutlinedButton(
                    onClick = { viewModel.discoverServers() },
                    enabled = !isDiscovering,
                    modifier = Modifier.fillMaxWidth().testTag("discover_servers_button")
                ) {
                    if (isDiscovering) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("جاري فحص شبكة LAN...")
                    } else {
                        Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("اكتشاف خوادم TaloolaPos تلقائياً (UDP:5051)")
                    }
                }

                if (discoveredServers.isNotEmpty()) {
                    Text(
                        text = "الخوادم المكتشفة:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    discoveredServers.forEach { server ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                viewModel.updateServerConfig(server.host, server.port)
                                viewModel.connect()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = server.serviceName, fontWeight = FontWeight.Bold)
                                    Text(text = "${server.host}:${server.port}", fontSize = 12.sp)
                                }
                                Text("اتصال", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // القسم الثاني: الجهاز
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "القسم الثاني: الجهاز",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Text(text = "Device ID: $deviceId", style = MaterialTheme.typography.bodySmall)
                Text(text = "اسم الجهاز: هاتف البدالة (Android Client)", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "حالة الاقتران: " + if (trustedServerId.isNotBlank()) "مقترن ✓" else "غير مقترن",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(text = "Server ID الموثوق: ${trustedServerId.ifBlank { "لا يوجد" }}", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "صلاحية الجهاز: CallerAssistant (مساعد البدالة فقط)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // القسم الثالث: المكالمات
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "القسم الثالث: المكالمات",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("عرض بطاقة المتصل فوراً", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = true, onCheckedChange = {})
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("فتح تفاصيل المتصل تلقائياً", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = autoOpenDetails,
                        onCheckedChange = { viewModel.toggleAutoOpenDetails(it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("إظهار العنوان والمنطقة في البطاقة", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = true, onCheckedChange = {})
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("إظهار آخر طلب مسجل", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = true, onCheckedChange = {})
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("إظهار موقع العميل المسجل في TaloolaPos", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = true, onCheckedChange = {})
                }
            }
        }

        // القسم الرابع: المزامنة
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "القسم الرابع: المزامنة والعمل دون اتصال",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "حالة المزامنة: " + if (pendingCount == 0) "كافة الأحداث متزامنة مع TaloolaPos ✓" else "يوجد أحداث معلقة",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(text = "عدد المكالمات المعلقة محلياً: $pendingCount", style = MaterialTheme.typography.bodyMedium)
                Text(text = "إعادة المحاولة التلقائية: مفعلة عند توفر الشبكة", style = MaterialTheme.typography.bodySmall)

                Button(
                    onClick = { viewModel.syncPending() },
                    modifier = Modifier.fillMaxWidth().testTag("sync_now_button")
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("مزامنة الأحداث المعلقة الآن")
                }
            }
        }

        // القسم الخامس: الصلاحيات
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "القسم الخامس: الصلاحيات وTelecom",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "يتطلب اكتشاف المكالمات تفعيل خدمة فحص المكالمات (Call Screening Service). التطبيق لا يطلب ولا يشغل صلاحية المتصل الافتراضي (Default Dialer).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                                callScreeningRoleLauncher.launch(intent)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("request_call_screening_button")
                ) {
                    Icon(Icons.Default.PhoneCallback, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("تفعيل دور فحص المكالمات (Call Screening)")
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("إعدادات إشعارات المكالمات")
                }
            }
        }

        // القسم السادس: التشخيص واختبار الاتصال المباشر
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "القسم السادس: التشخيص واختبار الاتصال المباشر",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "أدوات متقدمة لفحص حالة الاتصال التسلسلي (9 مراحل) واستعراض سجلات الأحداث المشفرة ومحاكاة شاشة السيرفر في Windows.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { showTestDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("open_test_dialog_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("اختبار الاتصال الشامل (9 مراحل)")
                }

                OutlinedButton(
                    onClick = { showLogsDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("open_logs_dialog_button")
                ) {
                    Icon(Icons.Default.Article, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("سجل تشخيص الاتصال الآمن (Safe Logs)")
                }

                OutlinedButton(
                    onClick = { showServerCenterDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("open_server_center_button")
                ) {
                    Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("لوحة خادم TaloolaPos (Windows Server Center)")
                }
            }
        }
    }

    // Dialog for 9-Step Test
    if (showTestDialog) {
        Dialog(
            onDismissRequest = { showTestDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "فحص تسلسلي لاتصال البدالة",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showTestDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    TestConnectionSection(
                        isRunning = isTesting,
                        testResults = testResults,
                        onRunTest = { viewModel.runDiagnosticTest() }
                    )
                }
            }
        }
    }

    // Dialog for Diagnostic Logs
    if (showLogsDialog) {
        Dialog(
            onDismissRequest = { showLogsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "سجل التشخيص الآمن",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showLogsDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق")
                        }
                    }

                    DiagnosticLogsScreen(viewModel = viewModel)
                }
            }
        }
    }

    // Dialog for Windows Server Center
    if (showServerCenterDialog) {
        Dialog(
            onDismissRequest = { showServerCenterDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .fillMaxHeight(0.92f)
                    .padding(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "لوحة تحكم خادم TaloolaPos",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showServerCenterDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق")
                        }
                    }

                    WindowsServerCenterScreen(viewModel = viewModel)
                }
            }
        }
    }
}
