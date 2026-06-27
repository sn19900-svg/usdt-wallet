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

private enum class AmountMode { CRYPTO, USDT }

@Composable
fun SendScreen(viewModel: WalletViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // اختيار الشبكة محلياً في هذه الشاشة
    var selectedChain by remember { mutableStateOf<ActiveChain?>(null) }

    // إذا لم يختر الشبكة بعد - عرض شاشة الاختيار
    if (selectedChain == null) {
        ChainSelectScreen(
            title = "إرسال — اختر الشبكة",
            onSelectTron = { selectedChain = ActiveChain.TRON; viewModel.switchChain(ActiveChain.TRON) },
            onSelectBsc  = { selectedChain = ActiveChain.BSC;  viewModel.switchChain(ActiveChain.BSC) },
            onBack = { viewModel.navigate(Screen.Home) }
        )
        return
    }

    val isBsc = selectedChain == ActiveChain.BSC
    val nativeCurrency = if (isBsc) "BNB" else "TRX"
    val chainName  = if (isBsc) "BSC BEP-20" else "Tron TRC-20"
    val chainColor = if (isBsc) CryptoYellow else CryptoRed
    val nativeUsdPrice = if (isBsc) uiState.bnbUsdPrice else uiState.trxUsdPrice

    var toAddress by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var amountMode by remember { mutableStateOf(AmountMode.CRYPTO) }
    var selectedCurrency by remember(isBsc) { mutableStateOf("USDT") }
    var showConfirm by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }

    val isAddressValid = if (isBsc)
        toAddress.startsWith("0x") && toAddress.length == 42
    else
        toAddress.startsWith("T") && toAddress.length == 34

    val availableBalance = when {
        selectedCurrency == "USDT" && isBsc -> uiState.bscUsdtBalance
        selectedCurrency == "USDT"           -> uiState.usdtBalance
        isBsc                                -> uiState.bnbBalance
        else                                 -> uiState.trxBalance
    }

    val amountInput = amount.toDoubleOrNull() ?: 0.0
    val amountInCrypto = when {
        amountMode == AmountMode.CRYPTO -> amountInput
        selectedCurrency == "USDT"     -> amountInput
        nativeUsdPrice > 0             -> amountInput / nativeUsdPrice
        else                           -> amountInput
    }

    val equivalentText = when {
        amountMode == AmountMode.USDT && selectedCurrency != "USDT" && nativeUsdPrice > 0 && amountInput > 0 ->
            "≈ ${String.format("%.6f", amountInCrypto)} $selectedCurrency"
        amountMode == AmountMode.CRYPTO && selectedCurrency != "USDT" && nativeUsdPrice > 0 && amountInput > 0 ->
            "≈ ${String.format("%.2f", amountInput * nativeUsdPrice)} USDT"
        else -> ""
    }

    val minTrxGas = 1.0
    val minBnbGas = 0.0002
    val hasEnoughGas = if (isBsc) uiState.bnbBalance >= minBnbGas else uiState.trxBalance >= minTrxGas
    val isAmountValid = amountInCrypto > 0 && amountInCrypto <= availableBalance
    val canSend = isAddressValid && isAmountValid && !uiState.isLoading && hasEnoughGas

    fun doSend() {
        when {
            selectedCurrency == "USDT" -> viewModel.sendUsdt(toAddress, amountInCrypto)
            isBsc                      -> viewModel.sendBnb(toAddress, amountInCrypto)
            else                       -> viewModel.sendTrx(toAddress, amountInCrypto)
        }
    }

    fun confirmWithBiometric() {
        if (activity == null || !BiometricAuthManager.canAuthenticate(activity)) { doSend(); return }
        BiometricAuthManager.authenticate(
            activity = activity,
            title = "تأكيد الإرسال",
            subtitle = "إرسال ${String.format("%.6f", amountInCrypto)} $selectedCurrency",
            onSuccess = { biometricError = null; doSend() },
            onError = { msg -> biometricError = "فشلت المصادقة: $msg" },
            onCancel = {}
        )
    }

    if (showQrScanner) {
        QrScannerScreen(
            onResult = { scanned ->
                toAddress = scanned.removePrefix("tron:").removePrefix("ethereum:")
                    .removePrefix("binance:").split("?")[0].trim()
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false }
        )
        return
    }

    if (uiState.sendSuccess) {
        SendSuccessDialog(txId = uiState.sendTxId) {
            viewModel.clearSendSuccess(); viewModel.navigate(Screen.Home)
        }
    }

    if (showConfirm) {
        ConfirmSendDialog(
            toAddress = toAddress,
            amount = amountInCrypto,
            amountUsdt = if (selectedCurrency != "USDT" && nativeUsdPrice > 0) amountInCrypto * nativeUsdPrice else amountInCrypto,
            currency = selectedCurrency,
            chainName = chainName,
            onConfirm = { showConfirm = false; confirmWithBiometric() },
            onDismiss = { showConfirm = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(CryptoDark).verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { selectedChain = null }) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = CryptoGray)
                Spacer(Modifier.width(4.dp))
                Text("رجوع", color = CryptoGray)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("إرسال", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CryptoWhite)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(chainColor))
            Spacer(Modifier.width(6.dp))
            Text(chainName, fontSize = 13.sp, color = chainColor)
        }

        Spacer(Modifier.height(16.dp))

        // اختيار العملة
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CryptoDarkCard).padding(4.dp)) {
            listOf("USDT", nativeCurrency).forEach { cur ->
                val sel = selectedCurrency == cur
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) chainColor else Color.Transparent)
                        .clickable { selectedCurrency = cur; amount = ""; amountMode = AmountMode.CRYPTO }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(cur, color = if (sel) CryptoDark else CryptoGray,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // الرصيد المتاح
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(chainColor.copy(0.1f)).padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("الرصيد المتاح", color = CryptoGray, fontSize = 14.sp)
            Text("${String.format("%.4f", availableBalance)} $selectedCurrency",
                color = chainColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))

        // بطاقة الغاز
        val nativeBal = if (isBsc) uiState.bnbBalance else uiState.trxBalance
        val minGas = if (isBsc) minBnbGas else minTrxGas
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (hasEnoughGas) CryptoGreen.copy(0.08f) else CryptoRed.copy(0.12f))
            .border(1.dp, if (hasEnoughGas) CryptoGreen.copy(0.3f) else CryptoRed.copy(0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (hasEnoughGas) "⛽" else "⚠️", fontSize = 18.sp)
                Column {
                    Text("رسوم الشبكة ($nativeCurrency)",
                        color = if (hasEnoughGas) CryptoGreen else CryptoRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (hasEnoughGas) "رصيدك: ${String.format("%.4f", nativeBal)} $nativeCurrency ✓"
                        else "رصيدك: ${String.format("%.4f", nativeBal)} $nativeCurrency — تحتاج $minGas على الأقل",
                        color = if (hasEnoughGas) CryptoGray else CryptoRed, fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // عنوان المستقبل
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("عنوان المستقبل", color = CryptoGray, fontSize = 13.sp)
            TextButton(onClick = { showQrScanner = true }) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = chainColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("مسح QR", color = chainColor, fontSize = 12.sp)
            }
        }
        OutlinedTextField(
            value = toAddress,
            onValueChange = { toAddress = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(if (isBsc) "0x..." else "Txxx...", color = CryptoGray, fontSize = 13.sp) },
            trailingIcon = {
                if (toAddress.isNotEmpty())
                    Icon(if (isAddressValid) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null, tint = if (isAddressValid) CryptoGreen else CryptoRed)
            },
            colors = outlinedFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true
        )
        if (toAddress.isNotEmpty() && !isAddressValid) {
            Spacer(Modifier.height(4.dp))
            Text(if (isBsc) "عنوان BSC غير صحيح (0x، 42 حرف)" else "عنوان Tron غير صحيح (T، 34 حرف)",
                color = CryptoRed, fontSize = 12.sp)
        }

        Spacer(Modifier.height(14.dp))

        // المبلغ
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("المبلغ (${if (amountMode == AmountMode.CRYPTO) selectedCurrency else "USDT"})",
                color = CryptoGray, fontSize = 13.sp)
            if (selectedCurrency != "USDT" && nativeUsdPrice > 0) {
                TextButton(onClick = {
                    amountMode = if (amountMode == AmountMode.CRYPTO) AmountMode.USDT else AmountMode.CRYPTO
                    amount = ""
                }) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = chainColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (amountMode == AmountMode.CRYPTO) "أدخل بـ USDT" else "أدخل بـ $selectedCurrency",
                        color = chainColor, fontSize = 12.sp)
                }
            }
        }
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("0.00", color = CryptoGray) },
            trailingIcon = {
                TextButton(onClick = {
                    amount = if (amountMode == AmountMode.CRYPTO)
                        String.format("%.6f", availableBalance)
                    else if (nativeUsdPrice > 0)
                        String.format("%.2f", availableBalance * nativeUsdPrice)
                    else String.format("%.6f", availableBalance)
                }) { Text("الكل", color = chainColor, fontSize = 13.sp) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = outlinedFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true
        )
        if (equivalentText.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(equivalentText, color = CryptoGray, fontSize = 12.sp)
        }
        if (amount.isNotEmpty() && amountInCrypto > availableBalance) {
            Spacer(Modifier.height(4.dp))
            Text("المبلغ أكبر من الرصيد المتاح", color = CryptoRed, fontSize = 12.sp)
        }

        biometricError?.let {
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CryptoRed.copy(0.15f)).padding(12.dp)) {
                Text(it, color = CryptoRed, fontSize = 13.sp)
            }
        }
        uiState.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CryptoRed.copy(0.15f)).padding(12.dp)) {
                Text(it, color = CryptoRed, fontSize = 13.sp)
            }
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
            if (uiState.isLoading)
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CryptoDark, strokeWidth = 2.dp)
            else {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("إرسال", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(CryptoYellow.copy(0.1f)).border(1.dp, CryptoYellow.copy(0.3f), RoundedCornerShape(12.dp)).padding(14.dp)) {
            Text("⚠️ تأكد من العنوان جيداً - المعاملات على Blockchain لا يمكن التراجع عنها",
                color = CryptoYellow, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

// ─── شاشة اختيار الشبكة ──────────────────────────────────

@Composable
fun ChainSelectScreen(title: String, onSelectTron: () -> Unit, onSelectBsc: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(CryptoDark).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = CryptoGray)
                Spacer(Modifier.width(4.dp))
                Text("رجوع", color = CryptoGray)
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CryptoWhite, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("اختر الشبكة المناسبة", fontSize = 14.sp, color = CryptoGray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))

        // Tron
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(CryptoRed.copy(0.12f)).border(1.5.dp, CryptoRed.copy(0.4f), RoundedCornerShape(18.dp))
                .clickable(onClick = onSelectTron).padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("🔴", fontSize = 36.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tron TRC-20", color = CryptoRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("رسوم منخفضة · سريع · مناسب للـ USDT", color = CryptoGray, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("الرصيد: ${String.format("%.2f", 0.0)} USDT", color = CryptoGray, fontSize = 12.sp)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CryptoRed)
            }
        }

        Spacer(Modifier.height(16.dp))

        // BSC
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(CryptoYellow.copy(0.12f)).border(1.5.dp, CryptoYellow.copy(0.4f), RoundedCornerShape(18.dp))
                .clickable(onClick = onSelectBsc).padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("🟡", fontSize = 36.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text("BSC BEP-20", color = CryptoYellow, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("شبكة Binance · رسوم BNB · واسعة الانتشار", color = CryptoGray, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("الرصيد: ${String.format("%.2f", 0.0)} USDT", color = CryptoGray, fontSize = 12.sp)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CryptoYellow)
            }
        }
    }
}

// ─── Dialogs ──────────────────────────────────────────────

@Composable
private fun ConfirmSendDialog(toAddress: String, amount: Double, amountUsdt: Double, currency: String, chainName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(CryptoDarkCard).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("تأكيد الإرسال", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CryptoWhite)
            Spacer(Modifier.height(20.dp))
            ConfirmRow("المبلغ", "${String.format("%.6f", amount)} $currency")
            if (currency != "USDT") { Spacer(Modifier.height(6.dp)); ConfirmRow("ما يعادل", "≈ ${String.format("%.2f", amountUsdt)} USDT") }
            Spacer(Modifier.height(10.dp))
            ConfirmRow("إلى", "${toAddress.take(10)}...${toAddress.takeLast(6)}")
            Spacer(Modifier.height(10.dp))
            ConfirmRow("الشبكة", chainName)
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CryptoGray), colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoGray)) { Text("إلغاء") }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = CryptoGreen)) { Text("تأكيد", color = CryptoDark, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SendSuccessDialog(txId: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(CryptoDarkCard).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✅", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text("تم الإرسال بنجاح!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CryptoGreen)
            if (txId.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text("TX ID:", color = CryptoGray, fontSize = 12.sp); Text("${txId.take(16)}...", color = CryptoWhite, fontSize = 13.sp, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = CryptoGreen)) { Text("العودة للرئيسية", color = CryptoDark, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(CryptoDarkSurface).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = CryptoGray, fontSize = 13.sp)
        Text(value, color = CryptoWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CryptoGreen, unfocusedBorderColor = CryptoDarkSurface,
    focusedTextColor = CryptoWhite, unfocusedTextColor = CryptoWhite,
    cursorColor = CryptoGreen, focusedContainerColor = CryptoDarkCard, unfocusedContainerColor = CryptoDarkCard
)
