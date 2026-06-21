-keep class com.nabil.usdtwallet.data.repository.** { *; }
-keep class com.nabil.usdtwallet.domain.** { *; }
-keepclassmembers class com.nabil.usdtwallet.** { *; }

# BouncyCastle: لا نحتفظ بالمكتبة كاملة، فقط نمنع تحذيرات البناء
# هذا يسمح لـ R8 بحذف آلاف الـ classes غير المستخدمة (تقليل classes.dex بشكل كبير)
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-keep class org.bouncycastle.crypto.digests.SHA256Digest { *; }
-keep class org.bouncycastle.crypto.digests.SHA512Digest { *; }
-keep class org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator { *; }
-keep class org.bouncycastle.crypto.PBEParametersGenerator { *; }
-keep class org.bouncycastle.crypto.params.KeyParameter { *; }
-keep class org.bouncycastle.jcajce.provider.digest.Keccak$* { *; }
-keep class org.bouncycastle.jce.ECNamedCurveTable { *; }
-keep class org.bouncycastle.jce.spec.ECParameterSpec { *; }
-keep class org.bouncycastle.math.ec.** { *; }
-keep class org.bouncycastle.asn1.sec.SECNamedCurves { *; }
-keep class org.bouncycastle.crypto.params.ECDomainParameters { *; }
-keep class org.bouncycastle.crypto.params.ECPrivateKeyParameters { *; }
-keep class org.bouncycastle.crypto.signers.ECDSASigner { *; }
-keep class org.bouncycastle.crypto.signers.HMacDSAKCalculator { *; }
-keep class org.bouncycastle.util.BigIntegers { *; }

-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

-keepclassmembers class com.nabil.usdtwallet.data.repository.** {
    <fields>;
}

# Google Tink (تستخدمها EncryptedSharedPreferences) تحتاج annotations اختيارية فقط للتوثيق
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Gson - حماية من مشاكل ParameterizedType بعد R8 minification
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Retrofit + OkHttp + Coroutines - القواعد الرسمية الموصى بها لـ R8
# المرجع: https://github.com/square/retrofit/blob/master/retrofit/src/main/resources/META-INF/proguard/retrofit2.pro
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Models التي تُسلسَل بـ Gson - نمنع R8 من تغيير الحقول أو البنية
-keep class com.nabil.usdtwallet.data.repository.TronAccountResponse { *; }
-keep class com.nabil.usdtwallet.data.repository.TronAccountWrapperResponse { *; }
-keep class com.nabil.usdtwallet.data.repository.TrxTransferRequest { *; }
-keep class com.nabil.usdtwallet.data.repository.TronTransactionRequest { *; }
-keep class com.nabil.usdtwallet.data.repository.TronTransactionResponse { *; }
-keep class com.nabil.usdtwallet.data.repository.TronResult { *; }
-keep class com.nabil.usdtwallet.data.repository.TronBroadcastRequest { *; }
-keep class com.nabil.usdtwallet.data.repository.TronBroadcastResponse { *; }
-keep class com.nabil.usdtwallet.data.repository.TronTxListResponse { *; }
-keep class com.nabil.usdtwallet.data.repository.TronTxItem { *; }
-keep interface com.nabil.usdtwallet.data.repository.TronApiService { *; }
