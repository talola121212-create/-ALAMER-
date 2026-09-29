package com.example.presentation.ui.components

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.domain.model.PairingSession
import com.example.presentation.viewmodel.SettingsViewModel
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusError

@Composable
fun QrScannerDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onPairedSuccess: () -> Unit
) {
    val context = LocalContext.current
    val isPairingLoading by viewModel.isPairingLoading.collectAsState()
    val activeSession by viewModel.activePairingSession.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var localNetworkNotice by remember { mutableStateOf("") }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var manualInputText by remember { mutableStateOf("") }
    var showPasteField by remember { mutableStateOf(false) }

    // Camera permission request launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            scanErrorMessage = "يلزم منح إذن الكاميرا لمسح رمز QR الخاص بـ TaloolaPos"
        }
    }

    // Check Local Network Permission on Android 17 / API 37+ if needed
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= 37) {
            localNetworkNotice = "يلزم السماح بالوصول إلى الشبكة المحلية للاتصال بـTaloolaPos."
        }
    }

    // Laser scanning animation
    val infiniteTransition = rememberInfiniteTransition(label = "scannerLaser")
    val laserOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserOffset"
    )

    fun handleScannedCode(rawCode: String) {
        val trimmed = rawCode.trim()
        if (!trimmed.startsWith("taloola-caller://pair", ignoreCase = true)) {
            scanErrorMessage = "رمز QR غير صالح أو ليس رمز ربط Alamer."
            return
        }

        scanErrorMessage = null
        viewModel.pairWithQr(trimmed) { success ->
            if (success) {
                onPairedSuccess()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text(
                                text = "مسح QR من TaloolaPos",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "وجّه الكاميرا نحو شاشة الكمبيوتر",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_qr_scanner")) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                if (localNetworkNotice.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = localNetworkNotice,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Camera Viewfinder Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Reticle overlay
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                    ) {
                        // Animated Scanning Laser
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .offset(y = laserOffsetY.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.primary,
                                            Color.White,
                                            MaterialTheme.colorScheme.primary,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }

                    // Loading indicator when processing
                    if (isPairingLoading) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = "جاري التحقق والاقتران مع TaloolaPos...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else if (!hasCameraPermission) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "يلزم منح إذن الكاميرا للمتابعة",
                                color = Color.White,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier.testTag("grant_camera_permission_button")
                            ) {
                                Text("السماح بالكاميرا")
                            }
                        }
                    }
                }

                // Error message banner
                AnimatedVisibility(visible = scanErrorMessage != null) {
                    scanErrorMessage?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().testTag("qr_scan_error_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // Quick actions for Simulator / Testing / Direct paste
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Instant One-Tap Simulation Button
                    Button(
                        onClick = {
                            val session = activeSession ?: viewModel.pairingSessionManager.createPairingSession(
                                serverId = "TALOOLA-SRV-BAGHDAD-01",
                                host = viewModel.serverHost.value,
                                port = viewModel.serverPort.value,
                                tls = false,
                                durationSeconds = 300
                            )
                            handleScannedCode(session.toPairingUri())
                        },
                        enabled = !isPairingLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().testTag("simulate_taloola_qr_scan_button")
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("مسح رمز تجريبي من TaloolaPos (محاكاة سريعة)")
                    }

                    // 2. Paste from clipboard / manual paste toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    handleScannedCode(clip)
                                } else {
                                    scanErrorMessage = "الحافظة فارغة، انسخ رمز taloola-caller://pair أولاً"
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("paste_qr_from_clipboard_button")
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("لصق من الحافظة", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showPasteField = !showPasteField },
                            modifier = Modifier.weight(1f).testTag("toggle_paste_input_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إدخال نص الرمز", fontSize = 12.sp)
                        }
                    }

                    AnimatedVisibility(visible = showPasteField) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualInputText,
                                onValueChange = { manualInputText = it },
                                label = { Text("نص رمز taloola-caller://pair") },
                                placeholder = { Text("taloola-caller://pair?v=1&...") },
                                singleLine = false,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth().testTag("manual_qr_text_field")
                            )

                            Button(
                                onClick = { handleScannedCode(manualInputText) },
                                enabled = manualInputText.isNotBlank() && !isPairingLoading,
                                modifier = Modifier.fillMaxWidth().testTag("submit_manual_qr_button")
                            ) {
                                Text("تأكيد ومسح الرمز")
                            }
                        }
                    }
                }
            }
        }
    }
}
