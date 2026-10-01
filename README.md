# ConnectTag File Storage Library (file-storage-lib-kmp)

[![JitPack](https://jitpack.io/v/connecttag/file-storage-lib-kmp.svg)](https://jitpack.io/#connecttag/file-storage-lib-kmp)

مكتبة تخزين وقراءة وإدارة الملفات والوسائط للتطبيقات عبر المنصات (Kotlin Multiplatform) مع دعم الذاكرة العشوائية ونظام الملفات عبر Okio وتكامل مخصص مع أنظمة Android:
- **Android** (API 24+)
- **Desktop JVM** (Windows, macOS, Linux)
- **iOS** (Arm64, SimulatorArm64)

---

## 📦 التثبيت والإضافة للمشروع (Installation via JitPack)

### 1. إضافة مستودع JitPack في `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. إضافة التبعية في `build.gradle.kts`:
```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            // For Kotlin Multiplatform:
            implementation("com.github.connecttag.file-storage-lib-kmp:file-storage-lib-kmp:1.0.0")
        }
    }
}
```

الحزمة الأساسية للمكتبة: `org.connecttag.lib.filestorage.*`

---

## 🌟 الميزات الأساسية

1. **`FileStorage` (عقد التخزين الموحد):**
   - واجهة برمجية موحدة لإجراء عمليات الحفظ، القراءة، الحذف، والمسح عبر مفتاح `FileResourceId(namespace, key)`.
2. **`OkioFileStorage`:**
   - تطبيق قوي بالاعتماد على محرك `okio.FileSystem` لحفظ محتويات الملفات مع بيانات وصفية كاملة `metadata.json` (مثل حجم الملف، نوع المحتوى، ETag، وتواريخ الصلاحية).
3. **`InMemoryFileStorage`:**
   - تخزين مؤقت وسريع في الذاكرة مع حماية تزامن عبر Coroutine Mutex للاختبارات ولعمليات الكاش الخفيفة.
4. **`FilePayloadReader` & Android Reader:**
   - قراءة وتدقيق أمان لحمولات الملفات مع تحديد الحجم الأقصى المسموح وفلترة أنواع MIME المقبولة.
   - دعم قراءة `content://` URIs في بيئة Android بأمان عبر `AndroidContentUriFileReader`.
