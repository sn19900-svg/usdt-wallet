package com.nabil.usdtwallet.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.nabil.usdtwallet.ui.Screen
import com.nabil.usdtwallet.ui.WalletViewModel
import com.nabil.usdtwallet.ui.theme.*

@Composable
fun ReceiveScreen(viewModel: WalletViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    val qrBitmap = remember(uiState.address) {
        generateQrCode(uiState.address, 400)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CryptoDark)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // Back
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { viewModel.navigate(Screen.Home) }) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = CryptoGray)
                Spacer(Modifier.width(4.dp))
                Text("رجوع", color = CryptoGray)
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("استقبال USDT", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CryptoWhite)
        Spacer(Modifier.height(4.dp))
        Text("شبكة Tron TRC-20 فقط", fontSize = 14.sp, color = CryptoGray)

        Spacer(Modifier.height(28.dp))

        // QR Code
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CryptoWhite)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            qrBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.fillMaxSize()
                )
            } ?: CircularProgressIndicator(color = CryptoDark)
        }

        Spacer(Modifier.height(24.dp))

        // العنوان
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CryptoDarkCard)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("عنوان المحفظة", color = CryptoGray, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = uiState.address,
                color = CryptoWhite,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(12.dp))

        // نسخ
        Button(
            onClick = {
                clipboard.setText(AnnotatedString(uiState.address))
                copied = true
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (copied) CryptoGreen.copy(alpha = 0.2f) else CryptoGreen
            )
        ) {
            Icon(
                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = null,
                tint = if (copied) CryptoGreen else CryptoDark,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (copied) "تم النسخ!" else "نسخ العنوان",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (copied) CryptoGreen else CryptoDark
            )
        }

        LaunchedEffect(copied) {
            if (copied) {
                kotlinx.coroutines.delay(2000)
                copied = false
            }
        }

        Spacer(Modifier.height(20.dp))

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
                text = "⚠️ أرسل TRC-20 فقط لهذا العنوان. إرسال شبكة أخرى يؤدي لفقدان الأموال.",
                color = CryptoYellow,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun generateQrCode(content: String, size: Int): Bitmap? {
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val writer = QRCodeWriter()
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x, y,
                    if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
