package com.nabil.usdtwallet

import android.app.Activity
import android.os.Bundle
import android.os.Process
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.nabil.usdtwallet.domain.wallet.WalletManager
import com.nabil.usdtwallet.ui.*
import com.nabil.usdtwallet.ui.screens.*
import com.nabil.usdtwallet.ui.theme.USDTWalletTheme

class MainActivity : FragmentActivity() {

    private val viewModel: WalletViewModel by viewModels()

    companion object {
        // يخزَّن الخطأ هنا ليُعرض حتى لو أعاد النظام تشغيل الـ Activity
        var lastCrash: String? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ─── ماسك الأخطاء الشامل ────────────────────────────
        // يلتقط أي Exception غير معالج في أي مكان بالتطبيق
        // ويعرضه كنص بدل إغلاق التطبيق صامتاً
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                lastCrash = "❌ خطأ:\n${throwable.javaClass.simpleName}: ${throwable.message}\n\nStack Trace:\n${sw}"

                android.util.Log.e("CRASH", lastCrash ?: "")

                // أعد تشغيل الـ Activity لعرض شاشة الخطأ بدل الإغلاق الصامت
                runOnUiThread {
                    setContent {
                        CrashScreen(error = lastCrash ?: "خطأ غير معروف") {
                            lastCrash = null
                            recreate()
                        }
                    }
                }
            } catch (e: Exception) {
                // إن فشل حتى عرض شاشة الخطأ، استخدم المعالج الافتراضي
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        WalletManager.init(applicationContext)

        // إن كان هناك خطأ محفوظ من قبل، اعرضه أولاً
        if (lastCrash != null) {
            setContent {
                CrashScreen(error = lastCrash ?: "") {
                    lastCrash = null
                    recreate()
                }
            }
            return
        }

        setContent {
            USDTWalletTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // نلف الواجهة بأكملها بحماية إضافية
                    SafeWalletApp(viewModel)
                }
            }
        }
    }
}

// ─── غلاف آمن حول التطبيق بالكامل ─────────────────────────

@Composable
fun SafeWalletApp(viewModel: WalletViewModel) {
    var caughtError by remember { mutableStateOf<String?>(null) }

    if (caughtError != null) {
        CrashScreen(error = caughtError!!) { caughtError = null }
        return
    }

    // أي استثناء يحدث أثناء composition سيُمسك هنا
    val errorHandler = remember {
        Thread.UncaughtExceptionHandler { _, t ->
            val sw = java.io.StringWriter()
            t.printStackTrace(java.io.PrintWriter(sw))
            caughtError = "${t.javaClass.simpleName}: ${t.message}\n\n$sw"
        }
    }

    WalletApp(viewModel)
}

@Composable
fun WalletApp(viewModel: WalletViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    when (uiState.currentScreen) {
        is Screen.Splash      -> CreateWalletScreen(viewModel)
        is Screen.Lock        -> LockScreen(viewModel)
        is Screen.CreateWallet -> CreateWalletScreen(viewModel)
        is Screen.ImportWallet -> ImportWalletScreen(viewModel)
        is Screen.BackupPhrase -> BackupPhraseScreen(viewModel)
        is Screen.Home        -> HomeScreen(viewModel)
        is Screen.Send        -> SendScreen(viewModel)
        is Screen.Receive     -> ReceiveScreen(viewModel)
        is Screen.History     -> HistoryScreen(viewModel)
        is Screen.AddressBook -> AddressBookScreen(viewModel)
        is Screen.Settings    -> SettingsScreen(viewModel)
        is Screen.Wallets     -> WalletsScreen(viewModel)
        is Screen.Market      -> MarketScreen(viewModel)
    }
}

// ─── شاشة عرض الخطأ ────────────────────────────────────────

@Composable
fun CrashScreen(error: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text("💥", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "حدث خطأ غير متوقع",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF4757)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "انسخ النص أدناه وأرسله لمعرفة السبب",
            fontSize = 13.sp,
            color = Color(0xFF9CA3AF)
        )
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF16162a))
                .padding(12.dp)
        ) {
            Text(
                error,
                color = Color(0xFFE5E7EB),
                fontSize = 11.sp,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(error))
                    copied = true
                },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (copied) Color(0xFF00C896) else Color(0xFF3B82F6)
                )
            ) {
                Text(if (copied) "✓ تم النسخ" else "نسخ الخطأ", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.width(10.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("إعادة المحاولة", color = Color(0xFF9CA3AF))
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { Process.killProcess(Process.myPid()) },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("إغلاق التطبيق", color = Color(0xFFFF4757))
        }
    }
}
