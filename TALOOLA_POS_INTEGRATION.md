# TaloolaPos Caller Assistant Integration Specification
## دليل الربط والاقتران التقني بين تطبيق "Alamer بدالة" ونظام TaloolaPos الرئيسي

---

### 1. نظرة عامة والمعمارية الأساسية
تطبيق **Alamer بدالة** هو Companion Client أصيل (Native Android) مصمم ليعمل جنباً إلى جنب مع نظام إدارة المطاعم والمبيعات المركزي **TaloolaPos** (Windows System).
- **المرجع الوحيد للبيانات:** نظام TaloolaPos المركزي هو المصدر الوحيد (Single Source of Truth) لبيانات العملاء، الطلبات، المناطق والمواقع الجغرافية.
- **طبيعة التطبيق:** التطبيق ليس نظام POS، ولا يمتلك قاعدة بيانات مستقلة للعملاء، ولا يتصل مباشرة بـ Firebase أو أي خادم خارجي دون المرور بـ TaloolaPos.
- **الدور الحصري:** جهاز بدالة هاتفي مخصص لمساعدة الكاشير وموظف الاستقبال عبر اكتشاف المتصل العراقي وعرض بياناته وإرسالها لشاشة الكاشير.

---

### 2. دورة حياة الاقتران والاتصال التسلسلية (Connection & Pairing Lifecycle)
تمت إعادة هيكلة الاتصال ليعتمد مساراً آمناً ومنضبطاً يتكون من 9 مراحل:

```text
DISCOVERY / BOOTSTRAP
        ↓
SERVER VERIFICATION
        ↓
PAIRING
        ↓
DEVICE REGISTRATION
        ↓
AUTHENTICATION
        ↓
CAPABILITY GRANT
        ↓
SIGNALR SESSION
        ↓
HEARTBEAT
        ↓
READY
```

1. **DISCOVERY / BOOTSTRAP (الاكتشاف والتمهيد):**
   - **المسار الأساسي (Primary):** مسح رمز QR أو إدخال كود الاقتران (6 أرقام مثل `748-291`).
   - **المسار الثاني (Secondary):** إدخال عنوان IP والمنفذ يدوياً.
   - **المسار الثالث (Tertiary):** البحث التلقائي عبر الشبكة المحلية (UDP Broadcast: `TALOOLA_POS_DISCOVER_V3`). إذا تعذر UDP لا يتم عرض فشل بل ينتقل فوراً للاتصال اليدوي.

2. **SERVER VERIFICATION (التحقق من هوية الخادم):**
   - قبل فتح أي اتصال دائم، يستعلم التطبيق عن نقطة النهاية:
     `GET /api/server/info` أو `GET /api/pairing/info`
   - التحقق من:
     - `serverId`: معرّف الخادم. إذا اختلف عن معرّف الخادم المقترن سابقاً، يتم عرض تحذير للمستخدم:
       `"تم اكتشاف تغيير في هوية الخادم. هل تريد اعتماد الخادم الجديد؟"`
     - `serviceName`: يجب أن يكون "TaloolaPos" أو "Taloola POS".
     - `serverVersion`: إصدار خادم TaloolaPos.
     - `protocolVersion`: إصدار البروتوكول المدعوم (3.x أو 1.x).

3. **PAIRING (الاقتران بجلسة مفردة):**
   - صيغة URI لرمز الاستجابة السريعة (QR Scheme):
     ```text
     taloolapos://pair?server=192.168.1.50&port=5000&id=POS-01&proto=3&code=748291&exp=1710000000
     ```
   - كود الاقتران المكون من 6 أرقام (Fallback Pairing Code) مثل: `748-291` أو `123456`.
   - جلسة الاقتران ذات استخدام لمرة واحدة (Single-use)، وصالحة لمدة 5 دقائق، ويتم إبطالها فور استهلاكها.

4. **DEVICE REGISTRATION (تسجيل الجهاز):**
   - توليد معرّف مشفر فريد للجهاز داخل Android Keystore:
     `DEV-ALAMER-XXXX`
   - إرسال بيانات المنصة:
     `DeviceName`, `Platform: Android`, `AppVersion: 1.0.0`, `DeviceType: CallerAssistant`.
   - حفظ بصمة شهادة الخادم والتوكن المشفر في التخزين الآمن المعزول عتادياً.

5. **AUTHENTICATION (مصادقة موظف البدالة):**
   - تسجيل دخول المشغل باسم الحساب (مثل: `بدالة 1`) ورمز PIN المشفر عبر AES-256-GCM.
   - إصدار Session Token مشفر للجلسة الحالية.

6. **CAPABILITY GRANT (منح صلاحية البدالة الحصرية):**
   - يطلب التطبيق حصراً صلاحية `CallerAssistant`.
   - لا يمتلك التطبيق أي صلاحيات إدارية أو صلاحيات كاشير أو تعديل أسعار أو مبيعات.
   - التحقق الصارم من استجابة الخادم: إذا لم تتضمن الاستجابة `callerAssistantGranted = true`، ترفض الجلسة فوراً.

7. **SIGNALR SESSION (قناة الاتصال اللحظي):**
   - نقطة النهاية: `ws://{host}:{port}/posHub`
   - بروتوكول: SignalR JSON Hub Protocol v1.0
   - محدد السجلات: `0x1E` (`\u001e`)
   - دوال الاستدعاء:
     - `RegisterCallerDevice(deviceId, token)`
     - `CallerHeartbeat(timestamp)`
     - `GetCallerCustomerContext(phoneNumber)`
     - `SendCallerContextToCashier(customerId, phone, notes)`
     - `OnCallerDeviceApproved(status)`
     - `OnServerHeartbeat(serverTime)`

8. **HEARTBEAT (نبض المراقبة اللحظي):**
   - فحص دوري كل 15 إلى 30 ثانية مع قياس وقت الاستجابة (Round-Trip Time RTT).
   - في حال فقدان 2 إلى 3 نبضات متتالية، يتم تحويل الحالة تلقائياً إلى `RECONNECTING`.

9. **READY (جاهز للعمل):**
   - التطبيق جاهز ومستعد لاكتشاف واستقبال المكالمات وإرسال السياق للكاشير.

---

### 3. اختبار الاتصال التسلسلي (9-Step Diagnostic Test)
يحتوي التطبيق على محرك فحص تسلسلي مدمج يفحص الأنظمة التسعة ويقيس زمن الاستجابة بالميلي ثانية (ms):
1. **Wi-Fi / Local Network:** فحص الاتصال بالشبكة المحلية.
2. **IP / Port Reachability:** فحص الوصول لمنفذ TCP 5000.
3. **Server Info:** فحص استجابة GET `/api/server/info`.
4. **Server Identity Match:** مطابقة معرّف الخادم الموثوق في Keystore.
5. **Pairing Valid:** صلاحية رمز أو جلسة الاقتران.
6. **Authentication Valid:** صحة جلسة المشغل المشفرة.
7. **CallerAssistant Granted:** تأكيد منح صلاحية البدالة حصراً من الخادم.
8. **SignalR (/posHub):** اتصال قناة SignalR اللحظية.
9. **Heartbeat:** فحص نبض الاتصال واستقرار زمن الاستجابة.

---

### 4. سجل التشخيص الآمن (Safe Diagnostic Logger)
- تسجيل زمني لجميع عمليات المصافحة، الاكتشاف، والاتصال.
- تشفير وحجب تام لكافة البيانات الحساسة (No PINs, No Session Tokens, No Private Keys).
- إمكانية نسخ ومشاركة السجل بنقرة واحدة لدعم مهندسي وفنيي الشبكات في المطعم.

---

### 5. محاكي لوحة تحكم سيرفر Windows (TaloolaPos Server Center)
يوفر التطبيق واجهة محاكاة كاملة لكيفية عرض السيرفر على نظام Windows، وتتضمن 5 بطاقات مراقبة:
- **CARD 1 (حالة الخادم):** Server ID, IP, Port, Protocol Version.
- **CARD 2 (حالة الشبكة):** LAN Status, TCP 5000 Listening, UDP 5051 Broadcast, TLS Status.
- **CARD 3 (الأجهزة المتصلة):** قائمة أجهزة البدالة والكاشيرات، مع أزرار الموافقة والرفض للأجهزة الجديدة.
- **CARD 4 (نشاط البدالة):** عدد المكالمات النشطة، الجلسات اللحظية، والرسائل المعلقة.
- **CARD 5 (الاقتران):** إنشاء رمز QR، توليد كود اقتران (مثل `748-291`) مع مؤقت عد تنازلي لصلاحية الجلسة.
