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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.nabil.usdtwallet.domain.auth.BiometricAuthManager
import com.nabil.usdtwallet.ui.ActiveChain
import com.nabil.usdtwallet.ui.Screen
import com.nabil.usdtwallet.ui.WalletViewModel
import com.nabil.usdtwallet.ui.theme.*

@Composable
fun SendScreen(viewModel: WalletViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var toAddress by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }

    // العملة حسب السلسلة: USDT أو TRX/BNB
    val isBsc = uiState.activeChain == ActiveChain.BSC
    val nativeCurrency = if (isBsc) "BNB" else "TRX"
    val chainName = if (isBsc) "BSC BEP-20" else "Tron TRC-20"
    val chainColor = if (isBsc) CryptoYellow else CryptoRed

    var selectedCurrency by remember(isBsc) {
        mutableStateOf("USDT")
    }

    // التحقق من العنوان حسب السلسلة
    val isAddressValid = if (isBsc) {
        toAddress.startsWith("0x") && toAddress.length == 42
    } else {
        toAddress.startsWith("T") && toAddress.length == 34
    }

    val availableBalance = when {
        selectedCurrency == "USDT" && isBsc  -> uiState.bscUsdtBalance
        selectedCurrency == "USDT"            -> uiState.usdtBalance
        isBsc                                 -> uiState.bnbBalance
        else                                  -> uiState.trxBalance
    }

    val amountDouble = amount.toDoubleOrNull() ?: 0.0
    val isAmountValid = amountDouble > 0 && amountDouble <= availableBalance
    val canSend = isAddressValid && isAmountValid && !uiState.isLoading

    fun confirmWithBiometric() {
        val doSend = {
            when {
                selectedCurrency == "USDT" -> viewModel.sendUsdt(toAddress, amountDouble)
                isBsc                      -> viewModel.sendBnb(toAddress, amountDouble)
                else                       -> viewModel.sendTrx(toAddress, amountDouble)
            }
        }
        if (activity == null) { doSend(); return }
        if (!BiometricAuthManager.canAuthenticate(activity)) { doSend(); return }
        BiometricAuthManager.authenticate(
            activity = activity,
            title = "تأكيد الإرسال",
            subtitle = "أكّد هويتك لإتمام إرسال ${String.format("%.2f", amountDouble)} $selectedCurrency",
            onSuccess = { biometricError = null; doSend() },
            onError = { msg -> biometricError = "فشلت المصادقة: $msg" },
            onCancel = {}
        )
    }

    if (uiState.sendSuccess) {
        SendSuccessDialog(
            txId = uiState.sendTxId,
            onDismiss = { viewModel.clearSendSuccess(); viewModel.navigate(Screen.Home) }
        )
    }

    if (showConfirm) {
        ConfirmSendDialog(
            toAddress = toAddress,
            amount = amountDouble,
            currency = selectedCurrency,
            chainName = chainName,
            onConfirm = { showConfirm = false; confirmWithBiometric() },
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

        Text("إرسال", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CryptoWhite)
        Spacer(Modifier.height(4.dp))

        // اسم الشبكة الحالية
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(chainColor)
            )
            Spacer(Modifier.width(6.dp))
            Text(chainName, fontSize = 14.sp, color = chainColor)
        }

        Spacer(Modifier.height(20.dp))

        // اختيار العملة (USDT أو TRX/BNB)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CryptoDarkCard)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("USDT", nativeCurrency).forEach { currency ->
                val isSelected = selectedCurrency == currency
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) chainColor else Color.Transparent)
                        .clickable { selectedCurrency = currency; amount = "" }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        currency,
                        color = if (isSelected) CryptoDark else CryptoGray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // الرصيد المتاح
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(chainColor.copy(alpha = 0.1f))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("الرصيد المتاح", color = CryptoGray, fontSize = 14.sp)
            Text(
                "${String.format("%.4f", availableBalance)} $selectedCurrency",
                color = chainColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(20.dp))

        // عنوان المستقبل
        val addressHint = if (isBsc) "0x..." else "Txxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        val addressError = if (isBsc)
            "عنوان BSC غير صحيح (يبدأ بـ 0x ويحتوي 42 حرف)"
        else
            "عنوان Tron غير صحيح (يبدأ بـ T ويحتوي 34 حرف)"

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("عنوان المستقبل", color = CryptoGray, fontSize = 13.sp)
            TextButton(onClick = { showQrScanner = true }) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = chainColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("مسح QR", color = chainColor, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = toAddress,
            onValueChange = { toAddress = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(addressHint, color = CryptoGray, fontSize = 13.sp) },
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
            Text(addressError, color = CryptoRed, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))

        // المبلغ
        Text("المبلغ ($selectedCurrency)", color = CryptoGray, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("0.00", color = CryptoGray) },
            trailingIcon = {
                TextButton(onClick = { amount = String.format("%.6f", availableBalance) }) {
                    Text("الكل", color = chainColor, fontSize = 13.sp)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = outlinedFieldColors(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        if (amount.isNotEmpty() && amountDouble > availableBalance) {
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
            Text(
                if (isBsc) "~0.001 BNB" else if (selectedCurrency == "USDT") "~1 TRX" else "~0.1 TRX",
                color = CryptoGray,
                fontSize = 13.sp
            )
        }

        biometricError?.let {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CryptoRed.copy(alpha = 0.15f))
                    .padding(12.dp)
            ) { Text(it, color = CryptoRed, fontSize = 13.sp) }
        }

        uiState.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CryptoRed.copy(alpha = 0.15f))
                    .padding(12.dp)
            ) { Text(it, color = CryptoRed, fontSize = 13.sp) }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { showConfirm = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canSend) chainColor else CryptoDarkSurface,
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
    currency: String,
    chainName: String,
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
            ConfirmRow("المبلغ", "${String.format("%.6f", amount)} $currency")
            Spacer(Modifier.height(10.dp))
            ConfirmRow("إلى", "${toAddress.take(10)}...${toAddress.takeLast(6)}")
            Spacer(Modifier.height(10.dp))
            ConfirmRow("الشبكة", chainName)
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Text("${txId.take(16)}...", color = CryptoWhite, fontSize = 13.sp, textAlign = TextAlign.Center)
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
