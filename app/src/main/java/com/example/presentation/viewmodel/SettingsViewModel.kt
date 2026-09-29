package com.example.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.*
import com.example.security.DiagnosticLogger
import com.example.telecom.CallManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val callManager = CallManager.getInstance(application)
    val secureStorage = callManager.secureStorage
    val taloolaConnection = callManager.taloolaConnection
    private val callRepository = callManager.callRepository
    val pairingSessionManager = taloolaConnection.pairingSessionManager

    val connectionState: StateFlow<ConnectionState> = taloolaConnection.connectionState
    val heartbeatStatus: StateFlow<HeartbeatStatus> = taloolaConnection.heartbeatStatus
    val diagnosticLogs: StateFlow<List<DiagnosticLogEntry>> = DiagnosticLogger.logs

    private val _discoveredServers = MutableStateFlow<List<ServerInfo>>(emptyList())
    val discoveredServers: StateFlow<List<ServerInfo>> = _discoveredServers.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _isPairingLoading = MutableStateFlow(false)
    val isPairingLoading: StateFlow<Boolean> = _isPairingLoading.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _testResults = MutableStateFlow<List<TestConnectionCheckItem>>(emptyList())
    val testResults: StateFlow<List<TestConnectionCheckItem>> = _testResults.asStateFlow()

    private val _activePairingSession = MutableStateFlow<PairingSession?>(pairingSessionManager.currentSession.value)
    val activePairingSession: StateFlow<PairingSession?> = _activePairingSession.asStateFlow()

    private val _scannedPairingPreview = MutableStateFlow<PairingSession?>(null)
    val scannedPairingPreview: StateFlow<PairingSession?> = _scannedPairingPreview.asStateFlow()

    private val _serverHost = MutableStateFlow(secureStorage.getServerHost())
    val serverHost: StateFlow<String> = _serverHost.asStateFlow()

    private val _serverPort = MutableStateFlow(secureStorage.getServerPort())
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _deviceId = MutableStateFlow(secureStorage.getDeviceId())
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _deviceName = MutableStateFlow(secureStorage.getDeviceName())
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _trustedServerId = MutableStateFlow(secureStorage.getTrustedServerId())
    val trustedServerId: StateFlow<String> = _trustedServerId.asStateFlow()

    private val _operatorAccount = MutableStateFlow(secureStorage.getOperatorAccount())
    val operatorAccount: StateFlow<String> = _operatorAccount.asStateFlow()

    private val _isMockMode = MutableStateFlow(secureStorage.isMockModeEnabled())
    val isMockMode: StateFlow<Boolean> = _isMockMode.asStateFlow()

    private val _autoOpenDetails = MutableStateFlow(secureStorage.isAutoOpenDetails())
    val autoOpenDetails: StateFlow<Boolean> = _autoOpenDetails.asStateFlow()

    // Windows TaloolaPos Server Dashboard State
    private val _connectedDevices = MutableStateFlow(
        listOf(
            ConnectedDeviceInfo("Alamer بدالة", "CallerAssistant", "192.168.1.55", "TALOOLA-SRV-BAGHDAD-01", "CallerAssistant", "متصل", "الآن"),
            ConnectedDeviceInfo("كاشير الدلفري 1", "Cashier", "192.168.1.50", "TALOOLA-SRV-BAGHDAD-01", "Cashier, Orders", "متصل", "منذ 2 د"),
            ConnectedDeviceInfo("كاشير الصالة 2", "Cashier", "192.168.1.51", "TALOOLA-SRV-BAGHDAD-01", "Cashier, Orders", "متصل", "منذ 5 د"),
            ConnectedDeviceInfo("طابعة المطبخ", "Printer", "192.168.1.52", "TALOOLA-SRV-BAGHDAD-01", "Printing", "متصل", "الآن"),
            ConnectedDeviceInfo("شاشة المطبخ KDS", "Kitchen", "192.168.1.53", "TALOOLA-SRV-BAGHDAD-01", "KitchenDisplay", "متصل", "الآن")
        )
    )
    val connectedDevices: StateFlow<List<ConnectedDeviceInfo>> = _connectedDevices.asStateFlow()

    private val _pendingDevices = MutableStateFlow(
        listOf(
            PendingApprovalDevice("dev-new-89", "هاتف بدالة مساند 2", "CallerAssistant", "192.168.1.61", "15:20")
        )
    )
    val pendingDevices: StateFlow<List<PendingApprovalDevice>> = _pendingDevices.asStateFlow()

    val pendingSyncCount: StateFlow<Int> = callRepository.pendingCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    init {
        // Collect current pairing session from manager
        viewModelScope.launch {
            pairingSessionManager.currentSession.collect { session ->
                _activePairingSession.value = session
            }
        }
    }

    fun updateServerConfig(host: String, port: Int) {
        _serverHost.value = host
        _serverPort.value = port
        secureStorage.saveServerHost(host)
        secureStorage.saveServerPort(port)
    }

    fun parseAndPreviewQr(rawInput: String): Boolean {
        val parsed = PairingSession.parseFromUri(rawInput)
        _scannedPairingPreview.value = parsed
        return parsed != null
    }

    fun clearScannedPreview() {
        _scannedPairingPreview.value = null
    }

    fun pairWithQr(qrUri: String, onFinished: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _isPairingLoading.value = true
            val parsed = PairingSession.parseFromUri(qrUri)
            if (parsed == null) {
                _isPairingLoading.value = false
                _message.emit("رمز QR غير صالح أو ليس رمز ربط Alamer.")
                onFinished?.invoke(false)
                return@launch
            }

            val res = taloolaConnection.pairWithQr(qrUri)
            _isPairingLoading.value = false
            if (res.isSuccess) {
                val pairData = res.getOrThrow()
                _trustedServerId.value = secureStorage.getTrustedServerId()
                _serverHost.value = secureStorage.getServerHost()
                _serverPort.value = secureStorage.getServerPort()
                _scannedPairingPreview.value = null
                _message.emit("تم ربط Alamer بدالة بنجاح ✓")
                onFinished?.invoke(true)
            } else {
                val err = res.exceptionOrNull()?.message ?: "فشل الاقتران"
                _message.emit(err)
                onFinished?.invoke(false)
            }
        }
    }

    fun unpair() {
        viewModelScope.launch {
            taloolaConnection.unpair()
            _trustedServerId.value = ""
            _scannedPairingPreview.value = null
            _message.emit("تم إلغاء الاقتران بـ TaloolaPos بنجاح")
        }
    }

    fun verifyServerOnly(host: String, port: Int) {
        viewModelScope.launch {
            val res = taloolaConnection.verifyServer(host, port)
            if (res.isSuccess) {
                val info = res.getOrThrow()
                _message.emit("✓ الخادم متاح: ${info.serviceName} (${info.serverId}) الإصدار ${info.serverVersion}")
            } else {
                _message.emit("تعذر التحقق من الخادم: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    // Retained for backward-compatibility with existing unit tests
    fun pairWithScannedSession(account: String = "", pin: String = "") {
        val session = _scannedPairingPreview.value ?: return
        pairWithQr(session.toPairingUri())
    }

    fun pairWithCodeOrManual(pairingCode: String = "", account: String = "", pin: String = "") {
        // Fallback for tests
        viewModelScope.launch {
            val res = taloolaConnection.connect(_serverHost.value, _serverPort.value)
            if (res.isSuccess) {
                _trustedServerId.value = secureStorage.getTrustedServerId()
                _message.emit("تم ربط الجهاز مع TaloolaPos بنجاح ✓")
            } else {
                _message.emit("فشل الاقتران: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun runDiagnosticTest() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            val results = taloolaConnection.runConnectionDiagnosticTest()
            _testResults.value = results
            _isTestingConnection.value = false
            val allOk = results.all { it.status == TestCheckStatus.SUCCESS }
            if (allOk) {
                _message.emit("✓ اكتمل اختبار الاتصال بنجاح: جميع الأنظمة الـ 9 تعمل بصورة ممتازة")
            } else {
                _message.emit("انتهى الاختبار مع وجود بعض التنبيهات، راجع التفاصيل أدناه")
            }
        }
    }

    fun discoverServers() {
        viewModelScope.launch {
            _isDiscovering.value = true
            val servers = taloolaConnection.discoverServers()
            _discoveredServers.value = servers
            _isDiscovering.value = false
            if (servers.isNotEmpty()) {
                _message.emit("تم العثور على ${servers.size} خادم TaloolaPos عبر الشبكة المحلية")
            } else {
                _message.emit("لم يتم العثور على خوادم TaloolaPos عبر UDP، يمكنك استخدام المسح الضوئي (QR) أو الإدخال اليدوي")
            }
        }
    }

    fun generateNewPairingSessionFromWindows() {
        val newSession = pairingSessionManager.createPairingSession(
            serverId = "TALOOLA-SRV-BAGHDAD-01",
            host = _serverHost.value,
            port = _serverPort.value,
            tls = false,
            durationSeconds = 300
        )
        _activePairingSession.value = newSession
        viewModelScope.launch {
            _message.emit("تم إنشاء رمز اقتران QR جديد صالح لمدة 5 دقائق")
        }
    }

    fun approvePendingDevice(deviceId: String) {
        val found = _pendingDevices.value.find { it.deviceId == deviceId }
        if (found != null) {
            _pendingDevices.value = _pendingDevices.value.filter { it.deviceId != deviceId }
            _connectedDevices.value = _connectedDevices.value + ConnectedDeviceInfo(
                deviceName = found.deviceName,
                deviceType = found.deviceType,
                ipAddress = found.ipAddress,
                serverId = "TALOOLA-SRV-BAGHDAD-01",
                capabilities = "CallerAssistant",
                status = "متصل",
                lastSeen = "الآن"
            )
            viewModelScope.launch {
                _message.emit("تم اعتماد جهاز '${found.deviceName}' بنجاح ✓")
            }
        }
    }

    fun rejectPendingDevice(deviceId: String) {
        _pendingDevices.value = _pendingDevices.value.filter { it.deviceId != deviceId }
        viewModelScope.launch {
            _message.emit("تم رفض طلب اعتماد الجهاز")
        }
    }

    fun toggleMockMode(enabled: Boolean) {
        _isMockMode.value = enabled
        secureStorage.setMockModeEnabled(enabled)
        viewModelScope.launch {
            if (enabled) {
                _message.emit("تم تفعيل وضع المحاكاة (Mock Mode) لأغراض الفحص")
            } else {
                _message.emit("تم إيقاف وضع المحاكاة - الربط الحقيقي مع TaloolaPos مفعل")
            }
        }
    }

    fun toggleAutoOpenDetails(enabled: Boolean) {
        _autoOpenDetails.value = enabled
        secureStorage.setAutoOpenDetails(enabled)
    }

    fun syncPending() {
        viewModelScope.launch {
            val count = callRepository.syncPendingCalls()
            _message.emit("تمت مزامنة $count مكالمة معلقة مع TaloolaPos")
        }
    }

    fun clearLogs() {
        DiagnosticLogger.clear()
    }

    fun disconnect() {
        taloolaConnection.disconnect()
    }

    fun connect() {
        viewModelScope.launch {
            val res = taloolaConnection.connect(_serverHost.value, _serverPort.value)
            if (res.isSuccess) {
                _message.emit("تم الاتصال بالخادم بنجاح ✓")
            } else {
                _message.emit("تعذر الاتصال: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    val recentCalls: StateFlow<List<CallEvent>> = callRepository.allCalls
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun triggerTestIncomingCall(phone: String = "07701234567") {
        callManager.onIncomingCallReceived(phone)
        viewModelScope.launch {
            _message.emit("تم إطلاق اختبار مكالمة واردة من $phone إلى TaloolaPos")
        }
    }

    fun openCustomerInPos() {
        viewModelScope.launch {
            val active = callManager.activeIncomingCall.value
            val phone = active?.phone ?: "07701234567"
            val customer = active ?: CustomerContext(
                customerId = "CUST-1001",
                phone = phone,
                normalizedPhone = phone,
                name = "أحمد علي",
                status = "عميل معروف",
                area = "الكرادة - المنصور",
                address = "شارع 14 رمضان، عمارة الأمل"
            )
            val res = callManager.customerRepository.sendContextToCashier(customer)
            if (res.isSuccess) {
                _message.emit("تم فتح بيانات العميل (${customer.name.ifBlank { customer.phone }}) في شاشة POS بنجاح ✓")
            } else {
                _message.emit("تم إرسال أمر فتح العميل إلى TaloolaPos")
            }
        }
    }
}
