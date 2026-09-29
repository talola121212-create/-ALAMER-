# Permissions & Telecom Policy - Alamer بدالة

### 1. سياسة الصلاحيات المخففة (Least-Privilege Policy)
يلتزم التطبيق التزاماً صارماً بسياسات Google Play ومتطلبات بيئات العمل الاحترافية:
- **لا يطلب التطبيق دور المتصل الافتراضي (`ROLE_DIALER`):** التطبيق مخصص لمساعد البدالة فقط وليس بديلاً عن تطبيق الهاتف الافتراضي للجهاز.
- **استخدام `RoleManager.ROLE_CALL_SCREENING`:** يتم طلب خدمة فحص المكالمات فقط لاستخراج رقم المتصل الوارد دون أي صلاحيات للتحكم بالمكالمات أو حظرها.

### 2. قائمة الصلاحيات المعلنة في `AndroidManifest.xml`
| الصلاحية | الغرض |
|---|---|
| `INTERNET` | الاتصال بخادم TaloolaPos عبر SignalR والشبكة المحلية |
| `ACCESS_NETWORK_STATE` | مراقبة حالة اتصال الشبكة والتحول بين الأنماط المتصلة وغير المتصلة |
| `ACCESS_WIFI_STATE` | فحص حالة شبكة Wi-Fi المحلية للمطعم |
| `CHANGE_WIFI_MULTICAST_STATE` | إرسال واستقبال حزم UDP Broadcast لاكتشاف الخوادم المحلية على المنفذ 5051 |
| `POST_NOTIFICATIONS` | إظهار إشعار ذو أولوية عليا (High Priority Notification) ببيانات المتصل الواردة عند تشغيل التطبيق في الخلفية |
| `BIND_SCREENING_SERVICE` | تصريح خدمة Android Telecom لتوجيه تفاصيل المكالمات الواردة لـ `CallScreeningServiceImpl` |

### 3. سرعة الاستجابة لـ Android Telecom
في الدالة `onScreenCall`:
- يتم استدعاء `respondToCall(callDetails, response)` **فوراً وبشكل متزامن ودون أي انتظار للشبكة أو قواعد البيانات**.
- يتم تحويل معالجة رقم الهاتف والبحث في TaloolaPos إلى مسار خلفي مستقل (Coroutine Scope) لمنع أي تعليق أو تأخير في شاشة استقبال المكالمات على الهاتف.
