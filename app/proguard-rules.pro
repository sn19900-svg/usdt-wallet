-keep class com.nabil.usdtwallet.data.repository.** { *; }
-keep class com.nabil.usdtwallet.domain.** { *; }

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
