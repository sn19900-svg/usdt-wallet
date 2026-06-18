package com.nabil.usdtwallet.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabil.usdtwallet.ui.WalletViewModel
import com.nabil.usdtwallet.ui.Screen
import com.nabil.usdtwallet.ui.theme.*

@Composable
fun ImportWalletScreen(viewModel: WalletViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var phraseText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CryptoDark)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        // Back
        TextButton(
            onClick = { viewModel.navigate(Screen.CreateWallet) }
        ) {
            Text("→ رجوع", color = CryptoGray)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "استيراد محفظة",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = CryptoWhite
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "أدخل الـ 12 كلمة مفصولة بمسافات",
            color = CryptoGray,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(24.dp))

        // حقل الإدخال
        OutlinedTextField(
            value = phraseText,
            onValueChange = { phraseText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            placeholder = {
                Text(
                    "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12",
                    color = CryptoGray,
                    fontSize = 13.sp
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CryptoGreen,
                unfocusedBorderColor = CryptoDarkSurface,
                focusedTextColor = CryptoWhite,
                unfocusedTextColor = CryptoWhite,
                cursorColor = CryptoGreen,
                focusedContainerColor = CryptoDarkCard,
                unfocusedContainerColor = CryptoDarkCard
            ),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            )
        )

        // عرض الكلمات المدخلة
        val words = phraseText.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (words.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${words.size}/12 كلمة",
                color = if (words.size == 12) CryptoGreen else CryptoYellow,
                fontSize = 13.sp
            )
        }

        // خطأ
        uiState.errorMessage?.let { error ->
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CryptoRed.copy(alpha = 0.15f))
                    .padding(12.dp)
            ) {
                Text(text = error, color = CryptoRed, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        // تحذير
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CryptoYellow.copy(alpha = 0.1f))
                .border(1.dp, CryptoYellow.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "💡 تأكد أنك على جهازك الشخصي قبل إدخال الكلمات",
                color = CryptoYellow,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                val wordList = phraseText.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                viewModel.importWallet(wordList)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CryptoGreen),
            enabled = words.size == 12 && !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = CryptoDark,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    "استيراد المحفظة",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CryptoDark
                )
            }
        }
    }
}
