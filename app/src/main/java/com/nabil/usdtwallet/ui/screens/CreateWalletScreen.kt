package com.nabil.usdtwallet.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabil.usdtwallet.ui.WalletViewModel
import com.nabil.usdtwallet.ui.theme.*

@Composable
fun CreateWalletScreen(viewModel: WalletViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CryptoDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(40.dp))

            // Logo & Title
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // أيقونة USDT
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(CryptoGreen.copy(alpha = 0.3f), CryptoDarkCard)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "₮",
                        fontSize = 52.sp,
                        color = CryptoGreen,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "محفظة USDT",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = CryptoWhite
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "محفظتك الخاصة على شبكة Tron TRC-20",
                    fontSize = 14.sp,
                    color = CryptoGray,
                    textAlign = TextAlign.Center
                )
            }

            // Features
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CryptoDarkCard)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureRow("🔐", "مشفّرة ومحمية على جهازك فقط")
                FeatureRow("🌍", "أرسل واستقبل من أي محفظة في العالم")
                FeatureRow("📝", "12 كلمة احتياطية للاستعادة دائماً")
                FeatureRow("💸", "رسوم منخفضة جداً على شبكة Tron")
            }

            // Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.createNewWallet() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoGreen),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = CryptoDark,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "إنشاء محفظة جديدة",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CryptoDark
                        )
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.navigate(com.nabil.usdtwallet.ui.Screen.ImportWallet) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoGreen),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CryptoGreen)
                ) {
                    Text(
                        "استيراد محفظة موجودة",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FeatureRow(emoji: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = emoji, fontSize = 20.sp)
        Text(text = text, color = CryptoGray, fontSize = 14.sp)
    }
}
