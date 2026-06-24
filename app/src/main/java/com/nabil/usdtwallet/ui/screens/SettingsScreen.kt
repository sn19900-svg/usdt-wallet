package com.nabil.usdtwallet.ui.screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nabil.usdtwallet.ui.Screen
import com.nabil.usdtwallet.ui.WalletViewModel
import com.nabil.usdtwallet.ui.theme.*

@Composable
fun SettingsScreen(viewModel: WalletViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMnemonic by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        DeleteWalletDialog(
            onConfirm = { viewModel.deleteWallet() },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (showMnemonic) {
        MnemonicDialog(
            words = viewModel.getMnemonic(),
            onDismiss = { showMnemonic = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CryptoDark)
            .verticalScroll(rememberScrollState())
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
                Text("الإعدادات", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CryptoWhite)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ─── قسم الشبكة ─────────────────────────────────────
        SectionHeader("الشبكة")

        SettingRow(
            icon = Icons.Default.SwapHoriz,
            title = if (uiState.isTestnet) "وضع التجربة (وهمي)" else "وضع حقيقي",
            subtitle = if (uiState.isTestnet) "اضغط للتبديل للأموال الحقيقية" else "اضغط للتبديل للوضع التجريبي",
            iconColor = if (uiState.isTestnet) CryptoYellow else CryptoRed,
            onClick = { viewModel.toggleNetwork() }
        )

        // ─── قسم الإشعارات ──────────────────────────────────
        SectionHeader("الإشعارات")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CryptoDarkCard)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = CryptoGreen, modifier = Modifier.size(22.dp))
                Column {
                    Text("إشعارات المعاملات", color = CryptoWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("تنبيه عند وصول USDT", color = CryptoGray, fontSize = 12.sp)
                }
            }
            Switch(
                checked = uiState.notificationsEnabled,
                onCheckedChange = { viewModel.toggleNotifications(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CryptoDark,
                    checkedTrackColor = CryptoGreen
                )
            )
        }

        // ─── قسم الأمان ─────────────────────────────────────
        SectionHeader("الأمان والنسخ الاحتياطي")

        SettingRow(
            icon = Icons.Default.Key,
            title = "عبارة الاسترداد",
            subtitle = "عرض الـ 12 كلمة السرية",
            iconColor = CryptoYellow,
            onClick = { showMnemonic = true }
        )

        SettingRow(
            icon = Icons.Default.Contacts,
            title = "دفتر العناوين",
            subtitle = "إدارة العناوين المحفوظة",
            iconColor = CryptoBlue,
            onClick = { viewModel.navigate(Screen.AddressBook) }
        )

        // ─── قسم المحفظة ─────────────────────────────────────
        SectionHeader("المحفظة")

        SettingRow(
            icon = Icons.Default.Delete,
            title = "حذف المحفظة",
            subtitle = "تحذير: هذا الإجراء لا يمكن التراجع عنه",
            iconColor = CryptoRed,
            onClick = { showDeleteConfirm = true }
        )

        // ─── معلومات التطبيق ──────────────────────────────────
        SectionHeader("حول التطبيق")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CryptoDarkCard)
                .padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("💳", fontSize = 32.sp)
                Spacer(Modifier.height(8.dp))
                Text("محفظتي", color = CryptoWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("USDT Wallet", color = CryptoGray, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("الإصدار ${com.nabil.usdtwallet.BuildConfig.BUILD_NUMBER}", color = CryptoGray, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("يدعم TRC-20 و BEP-20", color = CryptoGreen, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = CryptoGray,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CryptoDarkCard)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = CryptoWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = CryptoGray, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CryptoGray, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MnemonicDialog(words: List<String>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(CryptoDarkCard)
                .padding(24.dp)
        ) {
            Text("⚠️ عبارة الاسترداد السرية", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CryptoYellow)
            Spacer(Modifier.height(8.dp))
            Text("لا تشارك هذه الكلمات مع أحد أبداً", color = CryptoGray, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                modifier = Modifier.height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(words.size) { i ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CryptoDarkSurface)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${i + 1}", color = CryptoGray, fontSize = 10.sp)
                            Text(words[i], color = CryptoWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CryptoGreen)
            ) { Text("إغلاق", color = CryptoDark, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun DeleteWalletDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(CryptoDarkCard)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🗑️", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text("حذف المحفظة؟", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CryptoRed)
            Spacer(Modifier.height(8.dp))
            Text(
                "سيتم حذف جميع البيانات المحلية. تأكد من حفظ عبارة الاسترداد قبل المتابعة.",
                color = CryptoGray, fontSize = 13.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss, modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CryptoGray),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoGray)
                ) { Text("إلغاء") }
                Button(
                    onClick = onConfirm, modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoRed)
                ) { Text("حذف", color = CryptoWhite, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
