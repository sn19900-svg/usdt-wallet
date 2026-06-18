package com.nabil.usdtwallet.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabil.usdtwallet.data.repository.Transaction
import com.nabil.usdtwallet.ui.Screen
import com.nabil.usdtwallet.ui.WalletViewModel
import com.nabil.usdtwallet.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(viewModel: WalletViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CryptoDark)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CryptoDarkCard)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.navigate(Screen.Home) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = CryptoGray)
                }
                Spacer(Modifier.width(8.dp))
                Text("سجل المعاملات", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CryptoWhite)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.loadTransactions() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = CryptoGreen)
                }
            }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CryptoGreen)
            }
            return
        }

        if (uiState.transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("لا توجد معاملات بعد", color = CryptoGray, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.loadTransactions() }) {
                        Text("تحديث", color = CryptoGreen)
                    }
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.transactions) { tx ->
                HistoryTxItem(
                    tx = tx,
                    myAddress = uiState.address,
                    onCopyTxId = { clipboard.setText(AnnotatedString(tx.txId)) }
                )
            }
        }
    }
}

@Composable
private fun HistoryTxItem(
    tx: Transaction,
    myAddress: String,
    onCopyTxId: () -> Unit
) {
    val isIncoming = tx.isIncoming
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(tx.timestamp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CryptoDarkCard)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // أيقونة
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isIncoming) CryptoGreen.copy(alpha = 0.15f)
                        else CryptoRed.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = if (isIncoming) CryptoGreen else CryptoRed,
                    modifier = Modifier.size(22.dp)
                )
            }

            // معلومات
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isIncoming) "استقبال من" else "إرسال إلى",
                    color = CryptoWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isIncoming)
                        "${tx.from.take(10)}...${tx.from.takeLast(6)}"
                    else
                        "${tx.to.take(10)}...${tx.to.takeLast(6)}",
                    color = CryptoGray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = dateStr, color = CryptoGray, fontSize = 11.sp)
            }

            // المبلغ
            Text(
                text = "${if (isIncoming) "+" else "-"}${String.format("%.2f", tx.amount)}",
                color = if (isIncoming) CryptoGreen else CryptoRed,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = CryptoDarkSurface)
        Spacer(Modifier.height(8.dp))

        // TX ID
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TX: ${tx.txId.take(14)}...",
                color = CryptoGray,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onCopyTxId,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "نسخ TX",
                    tint = CryptoGray,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
