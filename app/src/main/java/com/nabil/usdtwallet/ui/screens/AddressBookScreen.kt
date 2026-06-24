package com.nabil.usdtwallet.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nabil.usdtwallet.data.repository.SavedAddress
import com.nabil.usdtwallet.ui.Screen
import com.nabil.usdtwallet.ui.WalletViewModel
import com.nabil.usdtwallet.ui.theme.*

@Composable
fun AddressBookScreen(
    viewModel: WalletViewModel,
    onSelectAddress: ((String) -> Unit)? = null  // null = وضع الإدارة، غير null = وضع الاختيار
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddAddressDialog(
            onSave = { name, address ->
                viewModel.saveAddress(name, address)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

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
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    if (onSelectAddress != null) {} // أغلق الشاشة
                    else viewModel.navigate(Screen.Settings)
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = CryptoGray)
                }
                Spacer(Modifier.width(4.dp))
                Text("دفتر العناوين", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CryptoWhite)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة", tint = CryptoGreen)
                }
            }
        }

        if (uiState.savedAddresses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📒", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("لا توجد عناوين محفوظة", color = CryptoGray, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showAddDialog = true }) {
                        Text("+ إضافة عنوان", color = CryptoGreen)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.savedAddresses) { saved ->
                    AddressBookItem(
                        saved = saved,
                        onSelect = onSelectAddress?.let { { onSelectAddress(saved.address) } },
                        onCopy = { clipboard.setText(AnnotatedString(saved.address)) },
                        onDelete = { viewModel.deleteAddress(saved.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddressBookItem(
    saved: SavedAddress,
    onSelect: (() -> Unit)?,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val chainColor = if (saved.chain == "BSC") CryptoYellow else CryptoRed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CryptoDarkCard)
            .then(if (onSelect != null) Modifier.clickable(onClick = onSelect) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // أيقونة السلسلة
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(chainColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (saved.chain == "BSC") "🟡" else "🔴", fontSize = 20.sp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(saved.name, color = CryptoWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${saved.address.take(10)}...${saved.address.takeLast(6)}",
                color = CryptoGray,
                fontSize = 12.sp
            )
            Text(saved.chain, color = chainColor, fontSize = 11.sp)
        }

        Row {
            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = CryptoGray, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = CryptoRed, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AddAddressDialog(
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(CryptoDarkCard)
                .padding(24.dp)
        ) {
            Text("إضافة عنوان", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CryptoWhite)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("الاسم (مثال: محفظة العمل)", color = CryptoGray) },
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedFieldColors(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = address,
                onValueChange = { address = it.trim() },
                label = { Text("العنوان", color = CryptoGray) },
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedFieldColors(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
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
                    onClick = { if (name.isNotEmpty() && address.isNotEmpty()) onSave(name, address) },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = name.isNotEmpty() && address.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoGreen)
                ) { Text("حفظ", color = CryptoDark, fontWeight = FontWeight.Bold) }
            }
        }
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
    unfocusedContainerColor = CryptoDarkCard,
    focusedLabelColor = CryptoGreen,
    unfocusedLabelColor = CryptoGray
)
