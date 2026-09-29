package com.example.presentation.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.presentation.ui.components.TestConnectionSection
import com.example.presentation.viewmodel.CallerViewModel
import com.example.presentation.viewmodel.SettingsViewModel

@Composable
fun DevScreen(
    callerViewModel: CallerViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateToCaller: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMockMode by settingsViewModel.isMockMode.collectAsState()
    val isTesting by settingsViewModel.isTestingConnection.collectAsState()
    val testResults by settingsViewModel.testResults.collectAsState()
    var customNumberInput by remember { mutableStateOf("") }

    var showServerCenterDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Warning Banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "وضع المطور والمحاكاة (Mock Mode) مخصص لأغراض الفحص والتشغيل التجريبي دون الحاجة لوجود سيرفر TaloolaPos الحقيقي على الشبكة.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Mock Mode Switch Card
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "وضع المحاكاة (Mock Mode)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isMockMode) "مفعل (استخدام بيانات تجريبية)" else "معطل (اتصال حقيقي مع TaloolaPos)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isMockMode,
                        onCheckedChange = { settingsViewModel.toggleMockMode(it) },
                        modifier = Modifier.testTag("mock_mode_switch")
                    )
                }
            }
        }

        // Call Simulation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "محاكاة المكالمات الواردة (Simulate Incoming Call)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "اضغط على أي رقم لاختبار تدفق فحص المكالمة واسترجاع بطاقة المتصل بسرعة:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Test Number 1: Mohammed Ahmed (Known regular customer)
                ElevatedButton(
                    onClick = {
                        callerViewModel.simulateCall("07751234567")
                        onNavigateToCaller()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("simulate_caller_1")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("0775 123 4567", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("محمد أحمد • عميل معروف (المنصور - طلبات دلفري)", fontSize = 12.sp)
                        }
                        Icon(Icons.Default.PhoneCallback, contentDescription = null)
                    }
                }

                // Test Number 2: Haidar Ali (Known customer)
                ElevatedButton(
                    onClick = {
                        callerViewModel.simulateCall("07751230000")
                        onNavigateToCaller()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("simulate_caller_2")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("0775 123 0000", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("حيدر علي • عميل معروف (الكرادة - طلبات سفري)", fontSize = 12.sp)
                        }
                        Icon(Icons.Default.PhoneCallback, contentDescription = null)
                    }
                }

                // Test Number 3: Unknown New Customer
                ElevatedButton(
                    onClick = {
                        callerViewModel.simulateCall("07801234567")
                        onNavigateToCaller()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("simulate_caller_3"),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("0780 123 4567", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("عميل جديد • لا توجد بيانات سابقة في TaloolaPos", fontSize = 12.sp)
                        }
                        Icon(Icons.Default.PhoneForwarded, contentDescription = null)
                    }
                }

                HorizontalDivider()

                // Custom phone test
                Text(
                    text = "تجربة رقم مخصص:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customNumberInput,
                        onValueChange = { customNumberInput = it },
                        label = { Text("رقم الهاتف (عراقي أو دولي)") },
                        placeholder = { Text("مثال: 0770xxxxxxx") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("custom_phone_input")
                    )
                    Button(
                        onClick = {
                            if (customNumberInput.isNotBlank()) {
                                callerViewModel.simulateCall(customNumberInput)
                                onNavigateToCaller()
                            }
                        },
                        enabled = customNumberInput.isNotBlank(),
                        modifier = Modifier.testTag("simulate_custom_phone_button")
                    ) {
                        Text("اتصال")
                    }
                }
            }
        }

        // Windows Server Simulation & Diagnostic Tools
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
                    text = "محاكاة خادم TaloolaPos والأدوات المتقدمة",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "استعراض لوحة تحكم سيرفر Windows (البطاقات الـ 5: الحالة، الشبكة، الأجهزة، البدالة، والاقتران) ومراقبة السجلات المشفرة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { showServerCenterDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("dev_open_server_center_button")
                ) {
                    Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("فتح لوحة خادم TaloolaPos (Windows)")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showTestDialog = true },
                        modifier = Modifier.weight(1f).testTag("dev_open_test_dialog_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("فحص الـ 9 مراحل", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showLogsDialog = true },
                        modifier = Modifier.weight(1f).testTag("dev_open_logs_dialog_button")
                    ) {
                        Icon(Icons.Default.Article, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("سجل التشخيص", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Windows Server Center Dialog
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
                            text = "شاشة سيرفر TaloolaPos - Windows",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showServerCenterDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق")
                        }
                    }

                    WindowsServerCenterScreen(viewModel = settingsViewModel)
                }
            }
        }
    }

    // Diagnostic Logs Dialog
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

                    DiagnosticLogsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }

    // 9-Step Test Dialog
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
                            text = "اختبار الاتصال الشامل (9 مراحل)",
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
                        onRunTest = { settingsViewModel.runDiagnosticTest() }
                    )
                }
            }
        }
    }
}
