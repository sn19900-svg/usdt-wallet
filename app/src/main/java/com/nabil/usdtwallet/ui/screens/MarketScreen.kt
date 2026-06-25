package com.nabil.usdtwallet.ui.screens

import androidx.compose.animation.core.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nabil.usdtwallet.ui.Screen
import com.nabil.usdtwallet.ui.WalletViewModel
import com.nabil.usdtwallet.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

// ─── Models ───────────────────────────────────────────────

data class CoinPrice(
    val symbol: String,
    val name: String,
    val price: Double,
    val change24h: Double,
    val high24h: Double,
    val low24h: Double,
    val volume24h: Double,
    val emoji: String
)

data class KlinePoint(val time: Long, val open: Double, val high: Double, val low: Double, val close: Double)

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
        val json = JSONObject(URL("https://api.binance.com/api/v3/ticker/24hr?symbol=$upper").readText())
        val defaultEntry = DEFAULT_COINS.find { it.first == upper }
        val base = upper.removeSuffix("USDT")
        CoinPrice(
            symbol    = upper,
            name      = defaultEntry?.second ?: base,
            price     = json.getString("lastPrice").toDouble(),
            change24h = json.getString("priceChangePercent").toDouble(),
            high24h   = json.getString("highPrice").toDouble(),
            low24h    = json.getString("lowPrice").toDouble(),
            volume24h = json.getString("quoteVolume").toDouble(),
            emoji     = defaultEntry?.third ?: "💰"
        )
    } catch (e: Exception) { null }
}

private suspend fun fetchKlines(symbol: String, interval: String = "1h", limit: Int = 48): List<KlinePoint> =
    withContext(Dispatchers.IO) {
        try {
            val upper = symbol.uppercase().let { if (!it.endsWith("USDT")) "${it}USDT" else it }
            val arr = JSONArray(
                URL("https://api.binance.com/api/v3/klines?symbol=$upper&interval=$interval&limit=$limit").readText()
            )
            (0 until arr.length()).map { i ->
                val k = arr.getJSONArray(i)
                KlinePoint(
                    time  = k.getLong(0),
                    open  = k.getString(1).toDouble(),
                    high  = k.getString(2).toDouble(),
                    low   = k.getString(3).toDouble(),
                    close = k.getString(4).toDouble()
                )
            }
        } catch (e: Exception) { emptyList() }
    }

// ─── Main Screen ──────────────────────────────────────────

@Composable
fun MarketScreen(viewModel: WalletViewModel) {
    val focusManager = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<CoinPrice?>(null) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var defaultCoins by remember { mutableStateOf<List<CoinPrice>>(emptyList()) }
    var isLoadingDefault by remember { mutableStateOf(true) }
    var lastUpdated by remember { mutableStateOf("") }
    var selectedCoin by remember { mutableStateOf<CoinPrice?>(null) }

    // جلب العملات الافتراضية
    LaunchedEffect(isLoadingDefault) {
        if (!isLoadingDefault) return@LaunchedEffect
        val results = DEFAULT_COINS.mapNotNull { (sym, name, emoji) ->
            fetchCoinPrice(sym)?.copy(name = name, emoji = emoji)
        }
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        defaultCoins = results
        lastUpdated = time
        isLoadingDefault = false
    }

    // Dialog الشارت
    selectedCoin?.let { coin ->
        CoinChartDialog(coin = coin, onDismiss = { selectedCoin = null })
    }

    Column(modifier = Modifier.fillMaxSize().background(CryptoDark)) {

        // ─── Header ──────────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth().background(CryptoDarkCard).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.navigate(Screen.Home) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = CryptoGray)
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("أسعار السوق", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CryptoWhite)
                    if (lastUpdated.isNotEmpty())
                        Text("Binance · $lastUpdated", fontSize = 11.sp, color = CryptoGray)
                }
                IconButton(onClick = { isLoadingDefault = true; searchResult = null; searchQuery = "" }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = CryptoGreen)
                }
            }

            Spacer(Modifier.height(10.dp))

            // ─── البحث ───────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it.uppercase()
                    if (it.isEmpty()) { searchResult = null; searchError = null }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                placeholder = { Text("ابحث: BTC, ETH, SOL, AVAX...", color = CryptoGray, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CryptoGray, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchLoading)
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CryptoGreen, strokeWidth = 2.dp)
                    else if (searchQuery.isNotEmpty())
                        IconButton(onClick = { searchQuery = ""; searchResult = null; searchError = null }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = CryptoGray, modifier = Modifier.size(18.dp))
                        }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    coroutineScope.launch {
                        val q = searchQuery.trim(); if (q.isEmpty()) return@launch
                        searchLoading = true; searchError = null; searchResult = null
                        val result = fetchCoinPrice(q)
                        searchResult = result
                        if (result == null) searchError = "لم يُعثر على \"$q\" — تأكد من الرمز"
                        searchLoading = false
                        focusManager.clearFocus()
                    }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CryptoGreen, unfocusedBorderColor = CryptoDarkSurface,
                    focusedTextColor = CryptoWhite, unfocusedTextColor = CryptoWhite,
                    cursorColor = CryptoGreen, focusedContainerColor = CryptoDarkSurface, unfocusedContainerColor = CryptoDarkSurface
                ),
                shape = RoundedCornerShape(12.dp), singleLine = true
            )
        }

        // ─── القائمة ─────────────────────────────────────────
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // نتيجة البحث
            if (searchResult != null || searchError != null) {
                item { Text("نتيجة البحث", color = CryptoGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                searchError?.let { err ->
                    item {
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(CryptoRed.copy(0.1f)).padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(err, color = CryptoRed, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                searchResult?.let { coin ->
                    item { CoinCard(coin = coin, onClick = { selectedCoin = coin }) }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            item { Text("العملات الرئيسية", color = CryptoGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }

            if (isLoadingDefault) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = CryptoGreen)
                            Spacer(Modifier.height(12.dp))
                            Text("جاري التحميل...", color = CryptoGray, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(defaultCoins) { coin ->
                    CoinCard(coin = coin, onClick = { selectedCoin = coin })
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

// ─── بطاقة العملة ─────────────────────────────────────────

@Composable
private fun CoinCard(coin: CoinPrice, onClick: () -> Unit) {
    val isPositive = coin.change24h >= 0
    val changeColor = if (isPositive) CryptoGreen else CryptoRed

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CryptoDarkCard)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(CryptoDarkSurface),
            contentAlignment = Alignment.Center) {
            Text(coin.emoji, fontSize = 22.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(coin.name, color = CryptoWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(coin.symbol.removeSuffix("USDT"), color = CryptoGray, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatPrice(coin.price), color = CryptoWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(changeColor.copy(0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("${if (isPositive) "+" else ""}${String.format("%.2f", coin.change24h)}%",
                    color = changeColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CryptoGray, modifier = Modifier.size(16.dp))
    }
}

// ─── Dialog الشارت ────────────────────────────────────────

@Composable
private fun CoinChartDialog(coin: CoinPrice, onDismiss: () -> Unit) {
    var klines by remember { mutableStateOf<List<KlinePoint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedInterval by remember { mutableStateOf("1h") }
    val coroutineScope = rememberCoroutineScope()

    val intervals = listOf("15m", "1h", "4h", "1d")

    LaunchedEffect(coin.symbol, selectedInterval) {
        isLoading = true
        klines = fetchKlines(coin.symbol, selectedInterval, 60)
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CryptoDarkCard).padding(16.dp)
        ) {
            // العنوان
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(coin.emoji, fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(coin.name, color = CryptoWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(coin.symbol, color = CryptoGray, fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = CryptoGray)
                }
            }

            Spacer(Modifier.height(8.dp))

            // السعر والتغيير
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatPrice(coin.price), color = CryptoWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                val isPositive = coin.change24h >= 0
                Text(
                    "${if (isPositive) "+" else ""}${String.format("%.2f", coin.change24h)}%",
                    color = if (isPositive) CryptoGreen else CryptoRed,
                    fontSize = 14.sp, modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // تبديل الفترة
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                intervals.forEach { interval ->
                    val selected = interval == selectedInterval
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (selected) CryptoGreen else CryptoDarkSurface)
                            .clickable { selectedInterval = interval }.padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(interval, color = if (selected) CryptoDark else CryptoGray,
                            fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // الشارت
            Box(modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(CryptoDarkSurface)) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CryptoGreen, modifier = Modifier.size(28.dp))
                    }
                } else if (klines.isNotEmpty()) {
                    LineChart(klines = klines)
                }
            }

            Spacer(Modifier.height(12.dp))

            // إحصائيات 24 ساعة
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBox("أعلى 24h", formatPrice(coin.high24h), CryptoGreen)
                StatBox("أدنى 24h", formatPrice(coin.low24h), CryptoRed)
                StatBox("الحجم", formatVolume(coin.volume24h), CryptoGray)
            }
        }
    }
}

// ─── شارت خطي بسيط ────────────────────────────────────────

@Composable
private fun LineChart(klines: List<KlinePoint>) {
    val prices = klines.map { it.close }
    val minPrice = prices.minOrNull() ?: 0.0
    val maxPrice = prices.maxOrNull() ?: 1.0
    val range = maxPrice - minPrice
    val isGreen = (prices.lastOrNull() ?: 0.0) >= (prices.firstOrNull() ?: 0.0)
    val lineColor = if (isGreen) Color(0xFF00C896) else Color(0xFFFF4757)

    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        val w = size.width
        val h = size.height
        val step = if (prices.size > 1) w / (prices.size - 1) else w

        // رسم منطقة التعبئة
        val fillPath = Path()
        prices.forEachIndexed { i, price ->
            val x = i * step
            val y = h - ((price - minPrice) / range * h).toFloat()
            if (i == 0) fillPath.moveTo(x, y) else fillPath.lineTo(x, y)
        }
        fillPath.lineTo((prices.size - 1) * step, h)
        fillPath.lineTo(0f, h)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.3f), Color.Transparent),
                startY = 0f, endY = h
            )
        )

        // رسم الخط
        val linePath = Path()
        prices.forEachIndexed { i, price ->
            val x = i * step
            val y = h - ((price - minPrice) / range * h).toFloat()
            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }
        drawPath(path = linePath, color = lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))

        // نقطة آخر سعر
        val lastX = (prices.size - 1) * step
        val lastY = h - ((prices.last() - minPrice) / range * h).toFloat()
        drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(lastX, lastY))
    }
}

// ─── Helpers ──────────────────────────────────────────────

@Composable
private fun StatBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = CryptoGray, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatPrice(price: Double) = when {
    price >= 1000  -> "$${String.format("%,.2f", price)}"
    price >= 1     -> "$${String.format("%.4f", price)}"
    price >= 0.001 -> "$${String.format("%.6f", price)}"
    else           -> "$${String.format("%.8f", price)}"
}

private fun formatVolume(volume: Double) = when {
    volume >= 1_000_000_000 -> "$${String.format("%.2fB", volume / 1_000_000_000)}"
    volume >= 1_000_000     -> "$${String.format("%.2fM", volume / 1_000_000)}"
    volume >= 1_000         -> "$${String.format("%.2fK", volume / 1_000)}"
    else                    -> "$${String.format("%.2f", volume)}"
}
