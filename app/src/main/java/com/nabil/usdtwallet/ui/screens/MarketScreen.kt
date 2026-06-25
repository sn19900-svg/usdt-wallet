package com.nabil.usdtwallet.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabil.usdtwallet.ui.Screen
import com.nabil.usdtwallet.ui.WalletViewModel
import com.nabil.usdtwallet.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

// ─── Model ────────────────────────────────────────────────

data class CoinPrice(
    val symbol: String,       // BTCUSDT
    val name: String,         // Bitcoin
    val price: Double,
    val change24h: Double,    // نسبة التغيير خلال 24 ساعة
    val high24h: Double,
    val low24h: Double,
    val volume24h: Double,
    val emoji: String
)

// ─── العملات الافتراضية ───────────────────────────────────

private val DEFAULT_COINS = listOf(
    Triple("BNBUSDT",  "BNB",      "🟡"),
    Triple("BTCUSDT",  "Bitcoin",  "🟠"),
    Triple("ETHUSDT",  "Ethereum", "🔵"),
    Triple("SOLUSDT",  "Solana",   "🟣"),
    Triple("XRPUSDT",  "XRP",      "⚫"),
    Triple("ADAUSDT",  "Cardano",  "🔷"),
    Triple("DOGEUSDT", "Dogecoin", "🐶"),
    Triple("TRXUSDT",  "Tron",     "🔴"),
)

// ─── Binance API ──────────────────────────────────────────

private suspend fun fetchCoinPrice(symbol: String): CoinPrice? = withContext(Dispatchers.IO) {
    try {
        val upper = symbol.uppercase().let { if (!it.endsWith("USDT")) "${it}USDT" else it }
        val url = "https://api.binance.com/api/v3/ticker/24hr?symbol=$upper"
        val json = JSONObject(URL(url).readText())

        val price    = json.getString("lastPrice").toDouble()
        val change   = json.getString("priceChangePercent").toDouble()
        val high     = json.getString("highPrice").toDouble()
        val low      = json.getString("lowPrice").toDouble()
        val volume   = json.getString("quoteVolume").toDouble()

        val base = upper.removeSuffix("USDT")
        val defaultEntry = DEFAULT_COINS.find { it.first == upper }

        CoinPrice(
            symbol   = upper,
            name     = defaultEntry?.second ?: base,
            price    = price,
            change24h = change,
            high24h  = high,
            low24h   = low,
            volume24h = volume,
            emoji    = defaultEntry?.third ?: "💰"
        )
    } catch (e: Exception) {
        null
    }
}

// ─── Screen ───────────────────────────────────────────────

@Composable
fun MarketScreen(viewModel: WalletViewModel) {
    val focusManager = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }

    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<CoinPrice?>(null) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    var defaultCoins by remember { mutableStateOf<List<CoinPrice>>(emptyList()) }
    var isLoadingDefault by remember { mutableStateOf(true) }
    var lastUpdated by remember { mutableStateOf("") }

    // جلب العملات الافتراضية
    LaunchedEffect(Unit) {
        loadDefaultCoins { coins, time ->
            defaultCoins = coins
            lastUpdated = time
            isLoadingDefault = false
        }
    }

    // بحث عن عملة
    suspend fun doSearch() {
        val q = searchQuery.trim()
        if (q.isEmpty()) return
        searchLoading = true
        searchError = null
        searchResult = null
        val result = fetchCoinPrice(q)
        if (result != null) {
            searchResult = result
        } else {
            searchError = "لم يتم العثور على \"$q\" — تأكد من الرمز (مثال: BTC, ETH, SOL)"
        }
        searchLoading = false
        focusManager.clearFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CryptoDark)
    ) {
        // ─── Header ──────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CryptoDarkCard)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.navigate(Screen.Home) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = CryptoGray)
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("أسعار السوق", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CryptoWhite)
                    if (lastUpdated.isNotEmpty()) {
                        Text("Binance · $lastUpdated", fontSize = 11.sp, color = CryptoGray)
                    }
                }
                // زر تحديث
                IconButton(onClick = {
                    isLoadingDefault = true
                    searchResult = null
                    searchQuery = ""
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = CryptoGreen)
                }
            }

            Spacer(Modifier.height(10.dp))

            // ─── خانة البحث ──────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it.uppercase()
                    if (it.isEmpty()) { searchResult = null; searchError = null }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocus),
                placeholder = {
                    Text("ابحث عن رمز العملة (BTC, ETH, SOL...)", color = CryptoGray, fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = CryptoGray, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (searchLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CryptoGreen, strokeWidth = 2.dp)
                    } else if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = ""; searchResult = null; searchError = null }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح", tint = CryptoGray, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = {
                    kotlinx.coroutines.GlobalScope.launch { doSearch() }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CryptoGreen,
                    unfocusedBorderColor = CryptoDarkSurface,
                    focusedTextColor = CryptoWhite,
                    unfocusedTextColor = CryptoWhite,
                    cursorColor = CryptoGreen,
                    focusedContainerColor = CryptoDarkSurface,
                    unfocusedContainerColor = CryptoDarkSurface
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        // ─── المحتوى ──────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // نتيجة البحث
            if (searchResult != null || searchError != null) {
                item {
                    Text(
                        "نتيجة البحث",
                        color = CryptoGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                searchError?.let { err ->
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CryptoRed.copy(alpha = 0.1f))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(err, color = CryptoRed, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                searchResult?.let { coin ->
                    item { CoinCard(coin = coin, expanded = true) }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // العملات الافتراضية
            item {
                Text(
                    "العملات الرئيسية",
                    color = CryptoGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (isLoadingDefault) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = CryptoGreen)
                            Spacer(Modifier.height(12.dp))
                            Text("جاري تحميل الأسعار...", color = CryptoGray, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(defaultCoins) { coin ->
                    CoinCard(coin = coin, expanded = false)
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }

    // تحديث تلقائي عند تغيير isLoadingDefault
    LaunchedEffect(isLoadingDefault) {
        if (isLoadingDefault) {
            loadDefaultCoins { coins, time ->
                defaultCoins = coins
                lastUpdated = time
                isLoadingDefault = false
            }
        }
    }
}

// ─── بطاقة العملة ─────────────────────────────────────────

@Composable
private fun CoinCard(coin: CoinPrice, expanded: Boolean) {
    val isPositive = coin.change24h >= 0
    val changeColor = if (isPositive) CryptoGreen else CryptoRed
    val changePrefix = if (isPositive) "+" else ""

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
                    .clip(RoundedCornerShape(12.dp))
                    .background(CryptoDarkSurface),
                contentAlignment = Alignment.Center
            ) {
                Text(coin.emoji, fontSize = 22.sp)
            }

            // الاسم والرمز
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    coin.name,
                    color = CryptoWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    coin.symbol.removeSuffix("USDT"),
                    color = CryptoGray,
                    fontSize = 12.sp
                )
            }

            // السعر والتغيير
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatPrice(coin.price),
                    color = CryptoWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(changeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "$changePrefix${String.format("%.2f", coin.change24h)}%",
                        color = changeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // تفاصيل موسعة عند البحث
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Divider(color = CryptoDarkSurface)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("أعلى 24h", formatPrice(coin.high24h), CryptoGreen)
                StatItem("أدنى 24h", formatPrice(coin.low24h), CryptoRed)
                StatItem("حجم التداول", formatVolume(coin.volume24h), CryptoGray)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = CryptoGray, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─── Helpers ──────────────────────────────────────────────

private fun formatPrice(price: Double): String {
    return when {
        price >= 1000  -> "$${String.format("%,.2f", price)}"
        price >= 1     -> "$${String.format("%.4f", price)}"
        price >= 0.001 -> "$${String.format("%.6f", price)}"
        else           -> "$${String.format("%.8f", price)}"
    }
}

private fun formatVolume(volume: Double): String {
    return when {
        volume >= 1_000_000_000 -> "$${String.format("%.2fB", volume / 1_000_000_000)}"
        volume >= 1_000_000     -> "$${String.format("%.2fM", volume / 1_000_000)}"
        volume >= 1_000         -> "$${String.format("%.2fK", volume / 1_000)}"
        else                    -> "$${String.format("%.2f", volume)}"
    }
}

private suspend fun loadDefaultCoins(
    onDone: suspend (List<CoinPrice>, String) -> Unit
) {
    val results = DEFAULT_COINS.mapNotNull { (symbol, name, emoji) ->
        fetchCoinPrice(symbol)?.copy(name = name, emoji = emoji)
    }
    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        .format(java.util.Date())
    onDone(results, time)
}
