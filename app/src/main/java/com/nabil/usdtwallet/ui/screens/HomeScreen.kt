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
    var addressCopied by remember { mutableStateOf("") }
    var showBreakdown by remember { mutableStateOf(false) }

    // الرصيد الإجمالي
    val totalUsdt = uiState.usdtBalance + uiState.bscUsdtBalance + uiState.solUsdtBalance + uiState.ethUsdtBalance +
        (uiState.bnbBalance * uiState.bnbUsdPrice) +
        (uiState.trxBalance * uiState.trxUsdPrice) +
        (uiState.solBalance * uiState.solUsdPrice) +
        (uiState.ethBalance * uiState.ethUsdPrice)

    // اسم المحفظة النشطة
    val activeWalletName = uiState.wallets.find { it.id == uiState.activeWalletId }?.name ?: "محفظتي"

    Column(
        modifier = Modifier.fillMaxSize().background(CryptoDark).verticalScroll(rememberScrollState())
    ) {
        // ─── Top Bar ─────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(if (uiState.isTestnet) CryptoYellow.copy(0.12f) else CryptoDarkCard)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // اسم المحفظة النشطة + تبديل
            Row(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(CryptoDarkCard)
                    .clickable { viewModel.navigate(Screen.Wallets) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("💼", fontSize = 14.sp)
                Text(activeWalletName, color = CryptoWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (uiState.wallets.size > 1) {
                    Icon(Icons.Default.UnfoldMore, contentDescription = null, tint = CryptoGray, modifier = Modifier.size(14.dp))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (uiState.isTestnet) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(CryptoYellow.copy(0.2f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("🧪 تجريبي", color = CryptoYellow, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                IconButton(onClick = { viewModel.navigate(Screen.Settings) }) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = CryptoGray, modifier = Modifier.size(20.dp))
                }
            }
        }

        // تحديث متاح
        if (uiState.updateAvailable) {
            Row(
                modifier = Modifier.fillMaxWidth().background(CryptoGreen)
                    .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uiState.updateDownloadUrl))) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تحديث جديد (${uiState.updateVersion})", color = CryptoDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.Download, contentDescription = null, tint = CryptoDark, modifier = Modifier.size(18.dp))
            }
        }

        // ─── Balance Card ─────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(CryptoGreen.copy(0.15f), CryptoDark)))
                .padding(horizontal = 20.dp, vertical = 28.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("إجمالي الرصيد", color = CryptoGray, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))

                if (uiState.isLoading) {
                    CircularProgressIndicator(color = CryptoGreen, modifier = Modifier.size(32.dp))
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(String.format("%.2f", totalUsdt), fontSize = 42.sp, fontWeight = FontWeight.Bold, color = CryptoWhite)
                        Spacer(Modifier.width(6.dp))
                        Text("USDT", fontSize = 18.sp, color = CryptoGreen, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }

                uiState.errorMessage?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("⚠️ $it", color = CryptoRed, fontSize = 11.sp, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.height(8.dp))

                // عرض/إخفاء التفاصيل
                TextButton(onClick = { showBreakdown = !showBreakdown }) {
                    Text(
                        if (showBreakdown) "إخفاء التفاصيل ▲" else "عرض تفاصيل المحفظة ▼",
                        color = CryptoGray, fontSize = 12.sp
                    )
                }

                if (showBreakdown) {
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NetworkBalanceCard("🔴", "TRC-20", uiState.usdtBalance, uiState.trxBalance, "TRX", uiState.address, CryptoRed, Modifier.weight(1f)) {
                            clipboard.setText(AnnotatedString(uiState.address)); addressCopied = "tron"
                        }
                        NetworkBalanceCard("🟡", "BEP-20", uiState.bscUsdtBalance, uiState.bnbBalance, "BNB", uiState.bscAddress, CryptoYellow, Modifier.weight(1f)) {
                            clipboard.setText(AnnotatedString(uiState.bscAddress)); addressCopied = "bsc"
                        }
                        NetworkBalanceCard("🟣", "SOL", uiState.solUsdtBalance, uiState.solBalance, "SOL", uiState.solanaAddress, Color(0xFF9945FF), Modifier.weight(1f)) {
                            clipboard.setText(AnnotatedString(uiState.solanaAddress)); addressCopied = "sol"
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NetworkBalanceCard("🔷", "ETH", uiState.ethUsdtBalance, uiState.ethBalance, "ETH", uiState.ethAddress, Color(0xFF627EEA), Modifier.weight(1f)) {
                            clipboard.setText(AnnotatedString(uiState.ethAddress)); addressCopied = "eth"
                        }
                        Spacer(Modifier.weight(2f))
                    }
                    if (addressCopied.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("✅ تم نسخ العنوان", color = CryptoGreen, fontSize = 11.sp)
                        LaunchedEffect(addressCopied) { kotlinx.coroutines.delay(2000); addressCopied = "" }
                    }
                }
            }
        }

        // ─── Action Buttons ───────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(Modifier.weight(1f), Icons.Default.ArrowUpward,   "إرسال",   CryptoGreen)  { viewModel.navigate(Screen.Send) }
            ActionButton(Modifier.weight(1f), Icons.Default.ArrowDownward, "استقبال", CryptoBlue)   { viewModel.navigate(Screen.Receive) }
            ActionButton(Modifier.weight(1f), Icons.Default.TrendingUp,    "السوق",   CryptoYellow) { viewModel.navigate(Screen.Market) }
            ActionButton(Modifier.weight(1f), Icons.Default.Refresh,       "تحديث",   CryptoGray)   { viewModel.refreshBalance(); viewModel.fetchPrices() }
        }

        Spacer(Modifier.height(20.dp))

        // ─── آخر المعاملات ────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("آخر المعاملات", fontWeight = FontWeight.SemiBold, color = CryptoWhite, fontSize = 16.sp)
            TextButton(onClick = { viewModel.navigate(Screen.History) }) {
                Text("عرض الكل", color = CryptoGreen, fontSize = 13.sp)
            }
        }

        if (uiState.transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp)).background(CryptoDarkCard).padding(28.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 28.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("لا توجد معاملات بعد", color = CryptoGray, fontSize = 13.sp)
                }
            }
        } else {
            uiState.transactions.take(3).forEach { tx ->
                TransactionItem(tx = tx, myAddress = uiState.address,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp))
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

// ─── بطاقة رصيد الشبكة ───────────────────────────────────

@Composable
private fun NetworkBalanceCard(
    emoji: String, name: String,
    usdt: Double, native: Double, nativeSym: String,
    address: String, color: Color,
    modifier: Modifier, onCopy: () -> Unit
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(color.copy(0.1f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$emoji $name", color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("${String.format("%.2f", usdt)}", color = CryptoWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("USDT", color = CryptoGray, fontSize = 9.sp)
        Spacer(Modifier.height(2.dp))
        Text("${String.format("%.4f", native)} $nativeSym", color = CryptoGray, fontSize = 9.sp)
        Spacer(Modifier.height(6.dp))
        if (address.isNotEmpty()) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .background(CryptoDarkCard).clickable(onClick = onCopy)
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${address.take(4)}..${address.takeLast(3)}", color = CryptoGray, fontSize = 8.sp)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CryptoGray, modifier = Modifier.size(8.dp))
            }
        }
    }
}

@Composable
private fun ActionButton(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(CryptoDarkCard).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(color.copy(0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Text(label, color = CryptoWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun TransactionItem(tx: com.nabil.usdtwallet.data.repository.Transaction, myAddress: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CryptoDarkCard).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape)
            .background(if (tx.isIncoming) CryptoGreen.copy(0.15f) else CryptoRed.copy(0.15f)),
            contentAlignment = Alignment.Center) {
            Icon(if (tx.isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = if (tx.isIncoming) CryptoGreen else CryptoRed, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(if (tx.isIncoming) "استقبال" else "إرسال", color = CryptoWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(if (tx.isIncoming) "من: ${tx.from.take(8)}..." else "إلى: ${tx.to.take(8)}...",
                color = CryptoGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${if (tx.isIncoming) "+" else "-"}${String.format("%.2f", tx.amount)} USDT",
            color = if (tx.isIncoming) CryptoGreen else CryptoRed, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
