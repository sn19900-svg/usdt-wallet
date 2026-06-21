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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    if (showNetworkSwitchConfirm) {
        NetworkSwitchDialog(
            switchingToReal = uiState.isTestnet,
            onConfirm = {
                showNetworkSwitchConfirm = false
                viewModel.toggleNetwork()
            },
            onDismiss = { showNetworkSwitchConfirm = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CryptoDark)
            .verticalScroll(rememberScrollState())
    ) {
        // ─── Network Toggle Banner ────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (uiState.isTestnet) CryptoYellow.copy(alpha = 0.15f) else CryptoRed.copy(alpha = 0.15f))
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    if (uiState.isTestnet) "🧪 وضع التجربة (أموال وهمية)" else "⚠️ وضع حقيقي (أموال فعلية)",
                    color = if (uiState.isTestnet) CryptoYellow else CryptoRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(onClick = { showNetworkSwitchConfirm = true }) {
                Text(
                    if (uiState.isTestnet) "التبديل لحقيقي" else "التبديل لتجريبي",
                    color = if (uiState.isTestnet) CryptoYellow else CryptoRed,
                    fontSize = 12.sp
                )
            }
        }

        // ─── Update Banner ────────────────────────────────────
        if (uiState.updateAvailable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CryptoGreen)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uiState.updateDownloadUrl))
                        context.startActivity(intent)
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "تحديث جديد متاح (${uiState.updateVersion})",
                        color = CryptoDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "اضغط هنا للتحميل",
                        color = CryptoDark.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }
                Icon(
                    Icons.Default.Download,
                    contentDescription = "تحديث",
                    tint = CryptoDark,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ─── Header Card ─────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            CryptoGreen.copy(alpha = 0.2f),
                            CryptoDark
                        )
                    )
                )
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {

                Text("رصيد USDT", color = CryptoGray, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))

                if (uiState.isLoading) {
                    CircularProgressIndicator(color = CryptoGreen, modifier = Modifier.size(32.dp))
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format("%.2f", uiState.usdtBalance),
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Bold,
                            color = CryptoWhite
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "USDT",
                            fontSize = 18.sp,
                            color = CryptoGreen,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = "TRX: ${String.format("%.2f", uiState.trxBalance)}",
                    color = CryptoGray,
                    fontSize = 13.sp
                )

                uiState.errorMessage?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "⚠️ $error",
                        color = CryptoRed,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(16.dp))

                // عنوان المحفظة
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(CryptoDarkCard)
                        .clickable {
                            clipboard.setText(AnnotatedString(uiState.address))
                            addressCopied = true
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (uiState.address.length > 16)
                            "${uiState.address.take(8)}...${uiState.address.takeLast(6)}"
                        else uiState.address,
                        color = CryptoGray,
                        fontSize = 13.sp
                    )
                    Icon(
                        imageVector = if (addressCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = if (addressCopied) CryptoGreen else CryptoGray,
                        modifier = Modifier.size(16.dp)
                    )
                }

                LaunchedEffect(addressCopied) {
                    if (addressCopied) {
                        kotlinx.coroutines.delay(2000)
                        addressCopied = false
                    }
                }
            }
        }

        // ─── Action Buttons ───────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.ArrowUpward,
                label = "إرسال",
                color = CryptoGreen,
                onClick = { viewModel.navigate(Screen.Send) }
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.ArrowDownward,
                label = "استقبال",
                color = CryptoBlue,
                onClick = { viewModel.navigate(Screen.Receive) }
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Refresh,
                label = "تحديث",
                color = CryptoGray,
                onClick = { viewModel.refreshBalance() }
            )
        }

        Spacer(Modifier.height(24.dp))

        // ─── Recent Transactions Preview ──────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("آخر المعاملات", fontWeight = FontWeight.SemiBold, color = CryptoWhite, fontSize = 16.sp)
            TextButton(onClick = { viewModel.navigate(Screen.History) }) {
                Text("عرض الكل", color = CryptoGreen, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (uiState.transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CryptoDarkCard)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 32.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("لا توجد معاملات بعد", color = CryptoGray, fontSize = 14.sp)
                }
            }
        } else {
            uiState.transactions.take(3).forEach { tx ->
                TransactionItem(
                    tx = tx,
                    myAddress = uiState.address,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ─── Network Info ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CryptoDarkCard)
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NetworkStat("الشبكة", "Tron TRC-20")
            NetworkStat("العملة", "USDT")
            NetworkStat("الرسوم", "~1 USDT")
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CryptoDarkCard)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Text(text = label, color = CryptoWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NetworkStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = CryptoGray, fontSize = 11.sp)
        Text(text = value, color = CryptoGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TransactionItem(
    tx: com.nabil.usdtwallet.data.repository.Transaction,
    myAddress: String,
    modifier: Modifier = Modifier
) {
    val isIncoming = tx.isIncoming
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CryptoDarkCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
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
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isIncoming) "استقبال" else "إرسال",
                color = CryptoWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isIncoming) "من: ${tx.from.take(8)}..." else "إلى: ${tx.to.take(8)}...",
                color = CryptoGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "${if (isIncoming) "+" else "-"}${String.format("%.2f", tx.amount)} USDT",
            color = if (isIncoming) CryptoGreen else CryptoRed,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NetworkSwitchDialog(
    switchingToReal: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(CryptoDarkCard)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (switchingToReal) "⚠️" else "🧪", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                if (switchingToReal) "التبديل للأموال الحقيقية؟" else "التبديل للتجربة الوهمية؟",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = CryptoWhite,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (switchingToReal)
                    "ستتعامل المحفظة الآن مع أموال USDT حقيقية على شبكة Tron الفعلية. تأكد أنك مستعد قبل المتابعة."
                else
                    "ستتحول المحفظة لشبكة Nile التجريبية، حيث الأموال وهمية بالكامل ولا قيمة فعلية لها.",
                fontSize = 13.sp,
                color = CryptoGray,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (switchingToReal) CryptoRed else CryptoGreen
                    )
                ) { Text("تأكيد", color = CryptoDark, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
