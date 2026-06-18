package com.nabil.usdtwallet.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nabil.usdtwallet.domain.wallet.WalletManager
import com.nabil.usdtwallet.ui.Screen
import com.nabil.usdtwallet.ui.WalletViewModel
import com.nabil.usdtwallet.ui.theme.*

@Composable
fun SendScreen(viewModel: WalletViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var toAddress by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }

    val isAddressValid = toAddress.length == 34 && toAddress.startsWith("T")
    val amountDouble = amount.toDoubleOrNull() ?: 0.0
    val isAmountValid = amountDouble > 0 && amountDouble <= uiState.usdtBalance
    val canSend = isAddressValid && isAmountValid && !uiState.isLoading

    // نجاح الإرسال
    if (uiState.sendSuccess) {
        SendSuccessDialog(
            txId = uiState.sendTxId,
            onDismiss = {
                viewModel.clearSendSuccess()
                viewModel.navigate(Screen.Home)
            }
        )
    }

    // تأكيد الإرسال
    if (showConfirm) {
        ConfirmSendDialog(
            toAddress = toAddress,
            amount = amountDouble,
            onConfirm = {
                showConfirm = false
                viewModel.sendUsdt(toAddress, amountDouble)
            },
            onDismiss = { showConfirm = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CryptoDark)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { viewModel.navigate(Screen.Home) }) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = CryptoGray)
                Spacer(Modifier.width(4.dp))
                Text("رجوع", color = CryptoGray)
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("إرسال USDT", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CryptoWhite)
        Spacer(Modifier.height(4.dp))
        Text("شبكة Tron TRC-20", fontSize = 14.sp, color = CryptoGray)

        Spacer(Modifier.height(24.dp))

        // الرصيد المتاح
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CryptoGreen.copy(alpha = 0.1f))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("الرصيد المتاح", color = CryptoGray, fontSize = 14.sp)
            Text(
                "${String.format("%.2f", uiState.usdtBalance)} USDT",
                color = CryptoGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(20.dp))

        // عنوان المستقبل
        Text("عنوان المستقبل", color = CryptoGray, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = toAddress,
            onValueChange = { toAddress = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Txxxxxxxxxxxxxxxxxxxxxxxxxxxxx", color = CryptoGray, fontSize = 13.sp) },
            trailingIcon = {
                if (toAddress.isNotEmpty()) {
                    Icon(
                        imageVector = if (isAddressValid) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isAddressValid) CryptoGreen else CryptoRed
                    )
                }
            },
            colors = outlinedFieldColors(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        if (toAddress.isNotEmpty() && !isAddressValid) {
            Spacer(Modifier.height(4.dp))
            Text("عنوان Tron غير صحيح (يبدأ بـ T ويحتوي 34 حرف)", color = CryptoRed, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))

        // المبلغ
        Text("المبلغ (USDT)", color = CryptoGray, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("0.00", color = CryptoGray) },
            trailingIcon = {
                TextButton(onClick = { amount = String.format("%.2f", uiState.usdtBalance) }) {
                    Text("الكل", color = CryptoGreen, fontSize = 13.sp)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = outlinedFieldColors(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        if (amount.isNotEmpty() && amountDouble > uiState.usdtBalance) {
            Spacer(Modifier.height(4.dp))
            Text("المبلغ أكبر من الرصيد المتاح", color = CryptoRed, fontSize = 12.sp)
        }

        Spacer(Modifier.height(12.dp))

        // رسوم الشبكة
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("رسوم الشبكة", color = CryptoGray, fontSize = 13.sp)
            Text("~1 USDT (من TRX)", color = CryptoGray, fontSize = 13.sp)
        }

        // خطأ
        uiState.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CryptoRed.copy(alpha = 0.15f))
                    .padding(12.dp)
            ) {
                Text(it, color = CryptoRed, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { showConfirm = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canSend) CryptoGreen else CryptoDarkSurface,
                contentColor = if (canSend) CryptoDark else CryptoGray
            ),
            enabled = canSend
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CryptoDark, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("إرسال", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
                "⚠️ تأكد من العنوان جيداً - المعاملات على Blockchain لا يمكن التراجع عنها",
                color = CryptoYellow,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ConfirmSendDialog(
    toAddress: String,
    amount: Double,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(CryptoDarkCard)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("تأكيد الإرسال", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CryptoWhite)
            Spacer(Modifier.height(20.dp))

            ConfirmRow("المبلغ", "${String.format("%.2f", amount)} USDT")
            Spacer(Modifier.height(10.dp))
            ConfirmRow("إلى", "${toAddress.take(10)}...${toAddress.takeLast(6)}")
            Spacer(Modifier.height(10.dp))
            ConfirmRow("الشبكة", "Tron TRC-20")

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CryptoGray),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoGray)
                ) { Text("إلغاء") }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoGreen)
                ) { Text("تأكيد", color = CryptoDark, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SendSuccessDialog(txId: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(CryptoDarkCard)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("✅", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text("تم الإرسال بنجاح!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CryptoGreen)
            Spacer(Modifier.height(12.dp))
            if (txId.isNotEmpty()) {
                Text("TX ID:", color = CryptoGray, fontSize = 12.sp)
                Text(
                    "${txId.take(16)}...",
                    color = CryptoWhite,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CryptoGreen)
            ) { Text("العودة للرئيسية", color = CryptoDark, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CryptoDarkSurface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = CryptoGray, fontSize = 13.sp)
        Text(value, color = CryptoWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CryptoGreen,
    unfocusedBorderColor = CryptoDarkSurface,
    focusedTextColor = CryptoWhite,
    unfocusedTextColor = CryptoWhite,
    cursorColor = CryptoGreen,
    focusedContainerColor = CryptoDarkCard,
    unfocusedContainerColor = CryptoDarkCard
)
