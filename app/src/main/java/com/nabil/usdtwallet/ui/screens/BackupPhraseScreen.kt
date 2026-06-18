package com.nabil.usdtwallet.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabil.usdtwallet.ui.WalletViewModel
import com.nabil.usdtwallet.ui.theme.*

@Composable
fun BackupPhraseScreen(viewModel: WalletViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var confirmed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CryptoDark)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        Text(
            text = "احفظ العبارة الاحتياطية",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = CryptoWhite
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "اكتب هذه الكلمات على ورقة واحتفظ بها في مكان آمن",
            fontSize = 14.sp,
            color = CryptoGray,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        // تحذير
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CryptoRed.copy(alpha = 0.15f))
                .border(1.dp, CryptoRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "⚠️ لا تشارك هذه الكلمات مع أحد - من يملكها يملك أموالك",
                color = CryptoRed,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(20.dp))

        // شبكة الكلمات 12
        val words = uiState.mnemonic
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CryptoDarkCard)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (row in 0 until 6) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 0 until 2) {
                        val index = row * 2 + col
                        if (index < words.size) {
                            WordChip(
                                number = index + 1,
                                word = words[index],
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // تأكيد
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CryptoDarkSurface)
                .clickable { confirmed = !confirmed }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = confirmed,
                onCheckedChange = { confirmed = it },
                colors = CheckboxDefaults.colors(checkedColor = CryptoGreen)
            )
            Text(
                text = "كتبت الكلمات وحفظتها في مكان آمن",
                color = CryptoWhite,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { viewModel.confirmBackupDone() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (confirmed) CryptoGreen else CryptoDarkSurface,
                contentColor = if (confirmed) CryptoDark else CryptoGray
            ),
            enabled = confirmed
        ) {
            Text(
                "دخول إلى المحفظة",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WordChip(number: Int, word: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CryptoDarkSurface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "$number",
            fontSize = 11.sp,
            color = CryptoGray,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = word,
            fontSize = 14.sp,
            color = CryptoWhite,
            fontWeight = FontWeight.Medium
        )
    }
}
