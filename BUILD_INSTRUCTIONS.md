# Build Instructions - Alamer بدالة

### المتطلبات الأساسية
- Android Studio Ladybug | 2024.2.1 أو أحدث
- Android SDK 36 (Minimum SDK: 24 - Android 7.0 Nougat)
- Java 11 أو 17
- Gradle 8.11+ و AGP 9.1.1

### خطوات البناء والتشغيل
1. **استيراد المشروع:**
   افتح مجلد المشروع في Android Studio.
2. **مزامنة Gradle:**
   سيقوم Gradle تلقائياً بتنزيل حزم Jetpack Compose و Room و OkHttp.
3. **بناء نسخة التصحيح (Debug APK):**
   ```bash
   gradle :app:assembleDebug
   ```
4. **بناء نسخة الإنتاج (Release APK):**
   ```bash
   gradle :app:assembleRelease
   ```
5. **تشغيل الاختبارات البرمجية:**
   ```bash
   gradle :app:testDebugUnitTest
   ```

### التشغيل الأولي
- التطبيق مضبوط افتراضياً على **Mock Mode** لتمكين المطور من فحص كافة الواجهات وسيناريوهات المكالمات الواردة مباشرة دون اشتراط وجود خادم TaloolaPos حقيقي في أول تشغيل.
- للتحويل إلى الخادم الفعلي، انتقل إلى تبويب **المطور** وعطّل Mock Mode، ثم أدخل عنوان خادم TaloolaPos في تبويب **ربط الجهاز**.
