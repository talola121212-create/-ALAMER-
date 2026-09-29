package com.example.presentation.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.PhoneNormalizer
import com.example.domain.model.CustomerContext
import com.example.presentation.ui.components.*
import com.example.presentation.viewmodel.CallerViewModel
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnecting

@Composable
fun CallerScreen(
    viewModel: CallerViewModel,
    modifier: Modifier = Modifier
) {
    val activeCall by viewModel.activeCall.collectAsState()
    val isLookupLoading by viewModel.isLookupLoading.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val recentCalls by viewModel.recentCalls.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showCustomerDetailsDialog by remember { mutableStateOf(false) }
    var showLastOrderDialog by remember { mutableStateOf(false) }
    var showTestCallDialog by remember { mutableStateOf(false) }
    var testCallPhoneInput by remember { mutableStateOf("07701234567") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top status row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConnectionBadge(state = connectionState)

            if (pendingCount > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    onClick = { viewModel.syncPendingEvents() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SyncProblem,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "$pendingCount مكالمة معلقة (مزامنة)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // Active Call Card or Idle Banner
        AnimatedContent(
            targetState = activeCall,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ActiveCallTransition"
        ) { call ->
            if (call != null) {
                CallerCard(
                    customer = call,
                    onSendToCashier = { viewModel.sendToCashier() },
                    onOpenCustomer = { showCustomerDetailsDialog = true },
                    onOpenLastOrder = { showLastOrderDialog = true },
                    onCreateCustomer = { showCreateDialog = true },
                    onDismiss = { viewModel.dismissCall() }
                )
            } else {
                // Standby Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneInTalk,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "البدالة جاهزة لاستقبال المكالمات",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "عند ورود أي اتصال سيتم استخراج الرقم وعرض بيانات العميل من TaloolaPos فوراً.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Test Call & Quick Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showTestCallDialog = true },
                modifier = Modifier.weight(1.2f).testTag("simulate_incoming_call_button")
            ) {
                Icon(Icons.Default.PhoneCallback, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("اختبار مكالمة واردة", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { viewModel.sendToCashier() },
                modifier = Modifier.weight(1f).testTag("open_in_pos_button")
            ) {
                Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("فتح العميل في POS")
            }
        }

        // Recent Calls List
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "سجل المكالمات الواردة",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${recentCalls.size} مكالمة",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (recentCalls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CallReceived,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "لا توجد مكالمات مسجلة بعد",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recentCalls, key = { it.callId }) { event ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        onClick = {
                            viewModel.simulateCall(event.phone)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CallReceived,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = PhoneNormalizer.formatForDisplay(event.phone),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = event.startedAtUtc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Sync badge
                            if (event.isSynced) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = "تمت المزامنة",
                                        tint = StatusConnected,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "متزامن",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusConnected
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudQueue,
                                        contentDescription = "قيد الانتظار",
                                        tint = StatusConnecting,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "معلق",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusConnecting
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showCreateDialog && activeCall != null) {
        CreateCustomerDialog(
            phone = activeCall!!.phone,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, area, address ->
                viewModel.createCustomer(name, area, address)
                showCreateDialog = false
            }
        )
    }

    if (showCustomerDetailsDialog && activeCall != null) {
        CustomerDetailsDialog(
            customer = activeCall!!,
            onDismiss = { showCustomerDetailsDialog = false }
        )
    }

    if (showLastOrderDialog && activeCall?.lastOrder != null) {
        LastOrderDialog(
            order = activeCall!!.lastOrder!!,
            onDismiss = { showLastOrderDialog = false }
        )
    }

    if (showTestCallDialog) {
        AlertDialog(
            onDismissRequest = { showTestCallDialog = false },
            title = {
                Text("اختبار إرسال مكالمة إلى TaloolaPos", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "اختر رقماً للمحاكاة أو أدخل رقماً مخصصاً لفحص استخراج بيانات العميل وإرسال المكالمة لـ TaloolaPos:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = testCallPhoneInput,
                        onValueChange = { testCallPhoneInput = it },
                        label = { Text("رقم الهاتف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("test_call_phone_input")
                    )

                    Text(
                        text = "أرقام تجريبية سريعة:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            onClick = { testCallPhoneInput = "07701234567" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("عميل معروف", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("07701234567", fontSize = 10.sp)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            onClick = { testCallPhoneInput = "07809876543" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("عميل جديد", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text("07809876543", fontSize = 10.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.simulateCall(testCallPhoneInput)
                        showTestCallDialog = false
                    },
                    modifier = Modifier.testTag("confirm_simulate_call_button")
                ) {
                    Text("إجراء المكالمة الآن")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTestCallDialog = false }) {
                    Text("إلغاء")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}
