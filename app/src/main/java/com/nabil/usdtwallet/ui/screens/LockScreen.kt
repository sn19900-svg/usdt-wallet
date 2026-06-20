package com.nabil.usdtwallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.nabil.usdtwallet.domain.auth.BiometricAuthManager
import com.nabil.usdtwallet.ui.WalletViewModel
import com.nabil.usdtwallet.ui.theme.*

@Composable
fun LockScreen(viewModel: WalletViewModel) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    fun tryAuthenticate() {
        if (activity == null) return
        errorMessage = null
        isChecking = true

        if (!BiometricAuthManager.canAuthenticate(activity)) {
            // لا يوجد بصمة أو PIN مفعّل على الجهاز - نسمح بالدخول مباشرة
            // (لا يمكن إجبار المستخدم على إعداد قفل جهاز لا يملكه)
            viewModel.onUnlocked()
            return
        }

        BiometricAuthManager.authenticate(
            activity = activity,
            title = "افتح محفظتي",
            subtitle = "استخدم بصمتك أو رمز القفل للمتابعة",
            onSuccess = {
                isChecking = false
                viewModel.onUnlocked()
            },
            onError = { msg ->
                isChecking = false
                errorMessage = "فشلت المصادقة: $msg"
            },
            onCancel = {
                isChecking = false
            }
        )
    }

    LaunchedEffect(Unit) {
        tryAuthenticate()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(CryptoGreen.copy(alpha = 0.12f), CryptoDark)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CryptoDarkCard),
                contentAlignment = Alignment.Center
            ) {
                Text("₮", fontSize = 44.sp, color = CryptoGreen, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "محفظتي مقفلة",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = CryptoWhite
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "افتحها بالبصمة أو رمز قفل جهازك",
                fontSize = 14.sp,
                color = CryptoGray,
                textAlign = TextAlign.Center
            )

            errorMessage?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, fontSize = 13.sp, color = CryptoRed, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = { tryAuthenticate() },
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CryptoGreen),
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = CryptoDark,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("فتح القفل", color = CryptoDark, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
