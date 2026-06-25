package com.nabil.usdtwallet.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabil.usdtwallet.ui.ActiveChain
import com.nabil.usdtwallet.ui.Screen
import com.nabil.usdtwallet.ui.WalletViewModel
import com.nabil.usdtwallet.ui.theme.*

@Composable
fun HomeScreen(viewModel: WalletViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var addressCopied by remember { mutableStateOf(false) }
    var showNetworkSwitchConfirm by remember { mutableStateOf(false) }

    val currentAddress = if (uiState.activeChain == ActiveChain.TRON) uiState.address else uiState.bscAddress
    val chainColor = if (uiState.activeChain == ActiveChain.TRON) CryptoRed else CryptoYellow
    val usdtBalance = if (uiState.activeChain == ActiveChain.TRON) uiState.usdtBalance else uiState.bscUsdtBalance
    val nativeBalance = if (uiState.activeChain == ActiveChain.TRON) uiState.trxBalance else uiState.bnbBalance
    val nativeSymbol = if (uiState.activeChain == ActiveChain.TRON) "TRX" else "BNB"
    val nativeUsdPrice = if (uiState.activeChain == ActiveChain.TRON) uiState.trxUsdPrice else uiState.bnbUsdPrice

    // قيمة الرصيد بالدولار والليرة
    val totalUsd = usdtBalance + (nativeBalance * nativeUsdPrice)
    val totalSyp = totalUsd * uiState.usdSypRate

    if (showNetworkSwitchConfirm) {
        NetworkSwitchDialog(
            switchingToReal = uiState.isTestnet,
            onConfirm = { showNetworkSwitchConfirm = false; viewModel.toggleNetwork() },
            onDismiss = { showNetworkSwitchConfirm = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CryptoDark)
            .verticalScroll(rememberScrollState())
    ) {
        // ─── Top Bar ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (uiState.isTestnet) CryptoYellow.copy(alpha = 0.15f) else CryptoRed.copy(alpha = 0.15f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { showNetworkSwitchConfirm = true }) {
                Text(
                    if (uiState.isTestnet) "🧪 وهمي" else "⚠️ حقيقي",
                    color = if (uiState.isTestnet) CryptoYellow else CryptoRed,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
            // زر الإعدادات
            IconButton(onClick = { viewModel.navigate(Screen.Settings) }) {
                Icon(Icons.Default.Settings, contentDescription = "الإعدادات", tint = CryptoGray, modifier = Modifier.size(20.dp))
            }
        }

        // ─── تحديث متاح ──────────────────────────────────────
        if (uiState.updateAvailable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CryptoGreen)
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uiState.updateDownloadUrl)))
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تحديث جديد (${uiState.updateVersion}) - اضغط للتحميل", color = CryptoDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.Download, contentDescription = null, tint = CryptoDark, modifier = Modifier.size(18.dp))
            }
        }

        // ─── Chain Selector ───────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CryptoDarkCard)
        ) {
            ChainTab("🔴 Tron TRC-20", uiState.activeChain == ActiveChain.TRON, CryptoRed, Modifier.weight(1f)) {
                viewModel.switchChain(ActiveChain.TRON)
            }
            ChainTab("🟡 BSC BEP-20", uiState.activeChain == ActiveChain.BSC, CryptoYellow, Modifier.weight(1f)) {
                viewModel.switchChain(ActiveChain.BSC)
            }
        }

        // ─── Balance Card ─────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(chainColor.copy(alpha = 0.2f), CryptoDark)))
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {

                Text("رصيد USDT", color = CryptoGray, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))

                if (uiState.isLoading) {
                    CircularProgressIndicator(color = chainColor, modifier = Modifier.size(32.dp))
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            String.format("%.2f", usdtBalance),
                            fontSize = 44.sp, fontWeight = FontWeight.Bold, color = CryptoWhite
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("USDT", fontSize = 18.sp, color = chainColor, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("$nativeSymbol: ${String.format("%.4f", nativeBalance)}", color = CryptoGray, fontSize = 13.sp)

                // القيمة بالدولار والليرة السورية
                if (totalUsd > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("≈ ${String.format("%.2f", totalUsd)} $", color = CryptoGreen, fontSize = 12.sp)
                        Text("≈ ${String.format("%,.0f", totalSyp)} ل.س", color = CryptoGray, fontSize = 12.sp)
                    }
                }

                uiState.errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("⚠️ $it", color = CryptoRed, fontSize = 12.sp, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.height(14.dp))

                // العنوان
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(CryptoDarkCard)
                        .clickable { clipboard.setText(AnnotatedString(currentAddress)); addressCopied = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (currentAddress.length > 16) "${currentAddress.take(8)}...${currentAddress.takeLast(6)}" else currentAddress,
                        color = CryptoGray, fontSize = 13.sp
                    )
                    Icon(
                        if (addressCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = if (addressCopied) CryptoGreen else CryptoGray,
                        modifier = Modifier.size(16.dp)
                    )
                }

                LaunchedEffect(addressCopied) {
                    if (addressCopied) { kotlinx.coroutines.delay(2000); addressCopied = false }
                }
            }
        }

        // ─── Action Buttons ───────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionButton(Modifier.weight(1f), Icons.Default.ArrowUpward, "إرسال", CryptoGreen) { viewModel.navigate(Screen.Send) }
            ActionButton(Modifier.weight(1f), Icons.Default.ArrowDownward, "استقبال", CryptoBlue) { viewModel.navigate(Screen.Receive) }
            ActionButton(Modifier.weight(1f), Icons.Default.Contacts, "عناوين", CryptoYellow) { viewModel.navigate(Screen.AddressBook) }
            ActionButton(Modifier.weight(1f), Icons.Default.Refresh, "تحديث", CryptoGray) { viewModel.refreshBalance() }
            ActionButton(Modifier.weight(1f), Icons.Default.TrendingUp, "السوق", CryptoGreen) { viewModel.navigate(Screen.Market) }
        }

        Spacer(Modifier.height(20.dp))

        // ─── آخر المعاملات ────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("آخر المعاملات", fontWeight = FontWeight.SemiBold, color = CryptoWhite, fontSize = 16.sp)
            TextButton(onClick = { viewModel.navigate(Screen.History) }) {
                Text("عرض الكل", color = CryptoGreen, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(6.dp))

        if (uiState.transactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp)).background(CryptoDarkCard).padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 28.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("لا توجد معاملات بعد", color = CryptoGray, fontSize = 13.sp)
                }
            }
        } else {
            uiState.transactions.take(3).forEach { tx ->
                TransactionItem(tx = tx, myAddress = currentAddress, modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        // ─── معلومات الشبكة ───────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp)).background(CryptoDarkCard).padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (uiState.activeChain == ActiveChain.TRON) {
                NetworkStat("الشبكة", "Tron TRC-20")
                NetworkStat("العملة", "USDT")
                NetworkStat("الرسوم", "~1 TRX")
            } else {
                NetworkStat("الشبكة", "BSC BEP-20")
                NetworkStat("العملة", "USDT")
                NetworkStat("الرسوم", "~0.001 BNB")
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun ChainTab(label: String, selected: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) color.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) color else CryptoGray, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ActionButton(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(CryptoDarkCard).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Text(label, color = CryptoWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NetworkStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = CryptoGray, fontSize = 11.sp)
        Text(value, color = CryptoGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TransactionItem(tx: com.nabil.usdtwallet.data.repository.Transaction, myAddress: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CryptoDarkCard).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(if (tx.isIncoming) CryptoGreen.copy(alpha = 0.15f) else CryptoRed.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (tx.isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = if (tx.isIncoming) CryptoGreen else CryptoRed,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(if (tx.isIncoming) "استقبال" else "إرسال", color = CryptoWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                if (tx.isIncoming) "من: ${tx.from.take(8)}..." else "إلى: ${tx.to.take(8)}...",
                color = CryptoGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "${if (tx.isIncoming) "+" else "-"}${String.format("%.2f", tx.amount)} USDT",
            color = if (tx.isIncoming) CryptoGreen else CryptoRed,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NetworkSwitchDialog(switchingToReal: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(CryptoDarkCard).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (switchingToReal) "⚠️" else "🧪", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(if (switchingToReal) "التبديل للأموال الحقيقية؟" else "التبديل للتجربة الوهمية؟", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CryptoWhite, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                if (switchingToReal) "ستتعامل المحفظة مع أموال USDT حقيقية. تأكد أنك مستعد."
                else "ستتحول لشبكة تجريبية، الأموال وهمية بالكامل.",
                fontSize = 13.sp, color = CryptoGray, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss, modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CryptoGray),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoGray)
                ) { Text("إلغاء") }
                Button(
                    onClick = onConfirm, modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (switchingToReal) CryptoRed else CryptoGreen)
                ) { Text("تأكيد", color = CryptoDark, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
