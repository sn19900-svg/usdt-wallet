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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nabil.usdtwallet.data.repository.WalletAccount
import com.nabil.usdtwallet.ui.Screen
import com.nabil.usdtwallet.ui.WalletViewModel
import com.nabil.usdtwallet.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WalletsScreen(viewModel: WalletViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddOptions by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<WalletAccount?>(null) }
    var deleteTarget by remember { mutableStateOf<WalletAccount?>(null) }

    // Dialog إعادة التسمية
    renameTarget?.let { wallet ->
        RenameDialog(
            currentName = wallet.name,
            onConfirm = { newName ->
                viewModel.renameWallet(wallet.id, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    // Dialog تأكيد الحذف
    deleteTarget?.let { wallet ->
        DeleteWalletConfirmDialog(
            walletName = wallet.name,
            onConfirm = {
                viewModel.deleteWalletById(wallet.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    // Dialog اختيار إضافة أو استيراد
    if (showAddOptions) {
        AddWalletOptionsDialog(
            onCreateNew = {
                showAddOptions = false
                viewModel.createNewWallet("محفظة ${uiState.wallets.size + 1}")
            },
            onImport = {
                showAddOptions = false
                viewModel.navigate(Screen.ImportWallet)
            },
            onDismiss = { showAddOptions = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(CryptoDark)) {

        // ─── Header ──────────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth().background(CryptoDarkCard).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.navigate(Screen.Home) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = CryptoGray)
                }
                Spacer(Modifier.width(4.dp))
                Text("محافظي", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CryptoWhite,
                    modifier = Modifier.weight(1f))
                // زر إضافة محفظة
                IconButton(onClick = { showAddOptions = true }) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة", tint = CryptoGreen,
                        modifier = Modifier.size(26.dp))
                }
            }
            Text("${uiState.wallets.size} محفظة", color = CryptoGray, fontSize = 12.sp,
                modifier = Modifier.padding(start = 12.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(uiState.wallets) { wallet ->
                val isActive = wallet.id == uiState.activeWalletId
                WalletCard(
                    wallet   = wallet,
                    isActive = isActive,
                    onClick  = {
                        if (!isActive) viewModel.switchWallet(wallet.id)
                        else viewModel.navigate(Screen.Home)
                    },
                    onRename = { renameTarget = wallet },
                    onDelete = { if (uiState.wallets.size > 1) deleteTarget = wallet }
                )
            }

            item {
                // زر إضافة محفظة في الأسفل
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(CryptoDarkCard)
                        .border(1.dp, CryptoGreen.copy(0.4f), RoundedCornerShape(14.dp))
                        .clickable { showAddOptions = true }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = CryptoGreen, modifier = Modifier.size(20.dp))
                        Text("إضافة محفظة جديدة", color = CryptoGreen, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

// ─── بطاقة المحفظة ────────────────────────────────────────

@Composable
private fun WalletCard(
    wallet: WalletAccount,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(wallet.createdAt))

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (isActive) CryptoGreen.copy(0.1f) else CryptoDarkCard)
            .border(
                width = if (isActive) 1.5.dp else 0.dp,
                color = if (isActive) CryptoGreen.copy(0.5f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // أيقونة المحفظة
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(if (isActive) CryptoGreen.copy(0.2f) else CryptoDarkSurface),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isActive) "✅" else "💼", fontSize = 22.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(wallet.name, color = CryptoWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (isActive) {
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(CryptoGreen.copy(0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("نشطة", color = CryptoGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text("أُنشئت: $date", color = CryptoGray, fontSize = 11.sp)
            }

            // زر القائمة
            IconButton(onClick = { expanded = !expanded }) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = CryptoGray)
            }
        }

        // الشبكات
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NetworkChip("🔴 TRC-20", wallet.tronAddress)
            NetworkChip("🟡 BEP-20", wallet.bscAddress)
            NetworkChip("🟣 SOL", wallet.solanaAddress)
        }

        // قائمة الإجراءات
        if (expanded) {
            Divider(color = CryptoDarkSurface)
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { expanded = false; onRename() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = CryptoBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إعادة تسمية", color = CryptoBlue, fontSize = 13.sp)
                }
                TextButton(
                    onClick = { expanded = false; onDelete() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = CryptoRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("حذف", color = CryptoRed, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun NetworkChip(label: String, address: String) {
    val hasAddress = address.isNotEmpty()
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (hasAddress) CryptoDarkSurface else CryptoDarkSurface.copy(0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = if (hasAddress) CryptoWhite else CryptoGray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            if (hasAddress) {
                Text("${address.take(4)}...${address.takeLast(3)}", color = CryptoGray, fontSize = 9.sp)
            } else {
                Text("غير متاح", color = CryptoGray.copy(0.5f), fontSize = 9.sp)
            }
        }
    }
}

// ─── Dialogs ──────────────────────────────────────────────

@Composable
private fun AddWalletOptionsDialog(onCreateNew: () -> Unit, onImport: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CryptoDarkCard).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("إضافة محفظة", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CryptoWhite)
            Spacer(Modifier.height(20.dp))

            // إنشاء جديدة
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(CryptoGreen.copy(0.1f)).border(1.dp, CryptoGreen.copy(0.3f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onCreateNew).padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("✨", fontSize = 28.sp)
                    Column {
                        Text("إنشاء محفظة جديدة", color = CryptoGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("إنشاء محفظة جديدة بعبارة استرداد جديدة", color = CryptoGray, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // استيراد
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(CryptoBlue.copy(0.1f)).border(1.dp, CryptoBlue.copy(0.3f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onImport).padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("📥", fontSize = 28.sp)
                    Column {
                        Text("استيراد محفظة", color = CryptoBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("استيراد بـ 12 كلمة من Trust Wallet أو أي محفظة أخرى", color = CryptoGray, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("إلغاء", color = CryptoGray)
            }
        }
    }
}

@Composable
private fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CryptoDarkCard).padding(24.dp)) {
            Text("إعادة تسمية المحفظة", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CryptoWhite)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("اسم المحفظة", color = CryptoGray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CryptoGreen, unfocusedBorderColor = CryptoDarkSurface,
                    focusedTextColor = CryptoWhite, unfocusedTextColor = CryptoWhite,
                    cursorColor = CryptoGreen, focusedContainerColor = CryptoDarkCard, unfocusedContainerColor = CryptoDarkCard,
                    focusedLabelColor = CryptoGreen, unfocusedLabelColor = CryptoGray
                ),
                shape = RoundedCornerShape(12.dp), singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CryptoGray), colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoGray)) { Text("إلغاء") }
                Button(onClick = { if (name.isNotEmpty()) onConfirm(name) }, modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = CryptoGreen)) {
                    Text("حفظ", color = CryptoDark, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DeleteWalletConfirmDialog(walletName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CryptoDarkCard).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🗑️", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text("حذف المحفظة؟", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CryptoRed)
            Spacer(Modifier.height(8.dp))
            Text("هل تريد حذف \"$walletName\"؟\nتأكد من حفظ عبارة الاسترداد قبل الحذف.",
                color = CryptoGray, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CryptoGray), colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoGray)) { Text("إلغاء") }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoRed)) {
                    Text("حذف", color = CryptoWhite, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
