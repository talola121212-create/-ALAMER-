package com.example.presentation.ui.components

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.model.PairingSession
import com.example.presentation.viewmodel.SettingsViewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

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
    var isCodeDetected by remember { mutableStateOf(false) }
    var cameraResetTrigger by remember { mutableIntStateOf(0) }

    // Camera permission request launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            scanErrorMessage = "يلزم منح إذن الكاميرا لمسح رمز QR الخاص بـ TaloolaPos"
        } else {
            scanErrorMessage = null
            cameraResetTrigger++
        }
    }

    // Check permissions on enter
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
        initialValue = 10f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserOffset"
    )

    fun handleScannedCode(rawCode: String) {
        val trimmed = rawCode.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'")
        val parsed = PairingSession.parseFromUri(trimmed)

        if (parsed == null) {
            scanErrorMessage = "رمز QR غير صالح أو ليس رمز ربط بدالة TaloolaPos."
            isCodeDetected = false
            return
        }

        triggerHapticFeedback(context)
        isCodeDetected = true
        scanErrorMessage = null

        viewModel.pairWithQr(trimmed) { success ->
            if (success) {
                onPairedSuccess()
            } else {
                isCodeDetected = false
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
                .fillMaxWidth(0.96f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
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
                                text = "وجّه الكاميرا نحو شاشة الكمبيوتر (POS)",
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

                // Camera Viewfinder Box with Live Preview and Barcode Scanner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0F172A))
                        .border(
                            1.5.dp,
                            if (isCodeDetected) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(18.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        key(cameraResetTrigger) {
                            CameraQrScanner(
                                modifier = Modifier.fillMaxSize(),
                                isScanningActive = !isPairingLoading && !isCodeDetected,
                                onQrDetected = { code ->
                                    handleScannedCode(code)
                                },
                                onError = { errMsg ->
                                    scanErrorMessage = errMsg
                                }
                            )
                        }
                    } else {
                        // Permission request state
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "يلزم منح إذن الكاميرا لمسح رمز QR",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "اضغط الزر أدناه لفتح الكاميرا ومسح شاشة TaloolaPos",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier.testTag("grant_camera_permission_button")
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("السماح بالكاميرا الآن")
                            }
                        }
                    }

                    // Reticle overlay
                    Box(
                        modifier = Modifier
                            .size(210.dp)
                            .border(
                                width = if (isCodeDetected) 3.dp else 2.dp,
                                color = if (isCodeDetected) Color(0xFF10B981) else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        // Animated Scanning Laser (visible when scanning)
                        if (!isCodeDetected && !isPairingLoading && hasCameraPermission) {
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
                    }

                    // Scanner hint at bottom of viewfinder
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = if (isCodeDetected) "تم التقاط الرمز بنجاح ✓" else "ضع رمز QR داخل المربع",
                            color = if (isCodeDetected) Color(0xFF34D399) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    // Loading indicator when processing pairing
                    if (isPairingLoading) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(38.dp),
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
                    }
                }

                // Error message banner
                AnimatedVisibility(visible = scanErrorMessage != null) {
                    scanErrorMessage?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("qr_scan_error_banner")
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
                    // 1. Instant One-Tap Simulation Button (Great for testing without live POS screen)
                    Button(
                        onClick = {
                            val sid = viewModel.trustedServerId.value.ifBlank { "POS-SRV-" + java.util.UUID.randomUUID().toString().take(6).uppercase() }
                            val session = activeSession ?: viewModel.pairingSessionManager.createPairingSession(
                                serverId = sid,
                                host = viewModel.serverHost.value,
                                port = viewModel.serverPort.value,
                                tls = false,
                                durationSeconds = 300
                            )
                            handleScannedCode(session.toPairingUri())
                        },
                        enabled = !isPairingLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("simulate_taloola_qr_scan_button")
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
                            modifier = Modifier
                                .weight(1f)
                                .testTag("paste_qr_from_clipboard_button")
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("لصق من الحافظة", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showPasteField = !showPasteField },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("toggle_paste_input_button")
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_qr_text_field")
                            )

                            Button(
                                onClick = { handleScannedCode(manualInputText) },
                                enabled = manualInputText.isNotBlank() && !isPairingLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("submit_manual_qr_button")
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

/**
 * High-performance CameraX preview with Dual-Engine QR Scanner (ML Kit + ZXing fallback),
 * with Flashlight Torch toggle, Zoom toggle, and Camera lens switch.
 */
@Composable
fun CameraQrScanner(
    modifier: Modifier = Modifier,
    isScanningActive: Boolean = true,
    onQrDetected: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var cameraInitError by remember { mutableStateOf<String?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                camera?.cameraControl?.enableTorch(false)
                cameraExecutor.shutdown()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier) {
        if (cameraInitError != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = cameraInitError ?: "تعذر فتح الكاميرا",
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { cameraInitError = null }) {
                    Text("إعادة محاولة فتح الكاميرا")
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val scannerOptions = BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build()
                            val barcodeScanner = BarcodeScanning.getClient(scannerOptions)
                            val zxingReader = MultiFormatReader().apply {
                                setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                            }

                            var detected = false

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                if (!isScanningActive || detected) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }

                                @androidx.annotation.OptIn(ExperimentalGetImage::class)
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val inputImage = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )

                                    barcodeScanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            if (detected) return@addOnSuccessListener
                                            for (barcode in barcodes) {
                                                val raw = barcode.rawValue ?: barcode.displayValue
                                                if (!raw.isNullOrBlank()) {
                                                    detected = true
                                                    onQrDetected(raw)
                                                    return@addOnSuccessListener
                                                }
                                            }

                                            // Fallback to ZXing if ML Kit did not find barcode in this frame
                                            try {
                                                val buffer = imageProxy.planes[0].buffer
                                                val bytes = ByteArray(buffer.remaining())
                                                buffer.get(bytes)
                                                val width = imageProxy.width
                                                val height = imageProxy.height
                                                val source = PlanarYUVLuminanceSource(
                                                    bytes, width, height, 0, 0, width, height, false
                                                )
                                                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                                                val res = zxingReader.decodeWithState(binaryBitmap)
                                                if (!res.text.isNullOrBlank() && !detected) {
                                                    detected = true
                                                    onQrDetected(res.text)
                                                }
                                            } catch (_: Exception) {
                                            } finally {
                                                zxingReader.reset()
                                            }
                                        }
                                        .addOnFailureListener {
                                            // Ignore single frame failures
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            val cameraSelector = CameraSelector.Builder()
                                .requireLensFacing(lensFacing)
                                .build()

                            cameraProvider.unbindAll()
                            val boundCamera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                            camera = boundCamera
                        } catch (e: Exception) {
                            cameraInitError = "تعذر تشغيل الكاميرا: ${e.localizedMessage ?: "تأكد من عدم استخدامها من تطبيق آخر"}"
                            onError(e.localizedMessage ?: "Camera error")
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Camera Controls Overlay (Flashlight, Zoom, Lens Switch)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Torch toggle
                IconButton(
                    onClick = {
                        val newState = !isTorchOn
                        isTorchOn = newState
                        camera?.cameraControl?.enableTorch(newState)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .testTag("qr_scanner_torch_toggle")
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "الفلاش",
                        tint = if (isTorchOn) Color.Yellow else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Zoom toggle (1x / 2x)
                IconButton(
                    onClick = {
                        val newZoom = !isZoomed
                        isZoomed = newZoom
                        camera?.cameraControl?.setZoomRatio(if (newZoom) 2.0f else 1.0f)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .testTag("qr_scanner_zoom_toggle")
                ) {
                    Text(
                        text = if (isZoomed) "2x" else "1x",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Lens switch
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .testTag("qr_scanner_lens_switch")
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "تبديل الكاميرا",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Triggers short haptic vibration upon successful QR code detection.
 */
private fun triggerHapticFeedback(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        }
    } catch (_: Exception) {}
}
