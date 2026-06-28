package com.nabil.usdtwallet.ui.screens

import android.annotation.SuppressLint
import android.webkit.*
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.viewinterop.AndroidView
import com.nabil.usdtwallet.ui.Screen
import com.nabil.usdtwallet.ui.WalletViewModel
import com.nabil.usdtwallet.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

// ─── Model ────────────────────────────────────────────────

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

private suspend fun fetchCoinPrice(symbol: String): CoinPrice? = withContext(Dispatchers.IO) {
    try {
        val upper = symbol.uppercase().let { if (!it.endsWith("USDT")) "${it}USDT" else it }
        val json = JSONObject(URL("https://api.binance.com/api/v3/ticker/24hr?symbol=$upper").readText())
        val entry = DEFAULT_COINS.find { it.first == upper }
        CoinPrice(
            symbol    = upper,
            name      = entry?.second ?: upper.removeSuffix("USDT"),
            price     = json.getString("lastPrice").toDouble(),
            change24h = json.getString("priceChangePercent").toDouble(),
            high24h   = json.getString("highPrice").toDouble(),
            low24h    = json.getString("lowPrice").toDouble(),
            volume24h = json.getString("quoteVolume").toDouble(),
            emoji     = entry?.third ?: "💰"
        )
    } catch (e: Exception) { null }
}

// ─── TradingView Widget HTML ──────────────────────────────

private fun buildTvHtml(symbol: String): String {
    val tvSym = if (symbol.contains(":")) symbol else "BINANCE:$symbol"
    return """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
*{margin:0;padding:0;box-sizing:border-box}
html,body{width:100%;height:100%;background:#131722;overflow:hidden}
.tradingview-widget-container{width:100%;height:100%}
</style>
</head>
<body>
<div class="tradingview-widget-container" style="height:100%;width:100%">
  <div id="tradingview_chart" style="height:calc(100% - 32px);width:100%"></div>
  <div class="tradingview-widget-copyright">
    <a href="https://www.tradingview.com/" rel="noopener nofollow" target="_blank">
    <span class="blue-text">Track all markets on TradingView</span></a>
  </div>
</div>
<script type="text/javascript" src="https://s3.tradingview.com/tv.js"></script>
<script type="text/javascript">
new TradingView.widget({
  "autosize": true,
  "symbol": "$tvSym",
  "interval": "60",
  "timezone": "Asia/Damascus",
  "theme": "dark",
  "style": "1",
  "locale": "ar",
  "enable_publishing": false,
  "withdateranges": true,
  "hide_side_toolbar": false,
  "allow_symbol_change": false,
  "studies": ["RSI@tv-basicstudies","MACD@tv-basicstudies"],
  "container_id": "tradingview_chart"
});
</script>
</body>
</html>"""
}

// ─── Main Screen ──────────────────────────────────────────

@Composable
fun MarketScreen(viewModel: WalletViewModel) {
    val focusManager = LocalFocusManager.current
    val searchFocus  = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    var searchQuery   by remember { mutableStateOf("") }
    var searchResult  by remember { mutableStateOf<CoinPrice?>(null) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError   by remember { mutableStateOf<String?>(null) }
    var defaultCoins  by remember { mutableStateOf<List<CoinPrice>>(emptyList()) }
    var isLoading     by remember { mutableStateOf(true) }
    var lastUpdated   by remember { mutableStateOf("") }

    // الشارت — نعرضه كشاشة كاملة (وليس Dialog)
    var chartCoin by remember { mutableStateOf<CoinPrice?>(null) }

    // إذا فُتح الشارت — نعرض شاشة الشارت الكاملة
    chartCoin?.let { coin ->
        BackHandler { chartCoin = null }
        TradingViewFullScreen(coin = coin, onBack = { chartCoin = null })
        return
    }

    LaunchedEffect(isLoading) {
        if (!isLoading) return@LaunchedEffect
        val results = DEFAULT_COINS.mapNotNull { (sym, name, emoji) ->
            fetchCoinPrice(sym)?.copy(name = name, emoji = emoji)
        }
        lastUpdated = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        defaultCoins = results
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(CryptoDark)) {

        // Header
        Column(modifier = Modifier.fillMaxWidth().background(CryptoDarkCard).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.navigate(Screen.Home) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = CryptoGray)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("أسعار السوق", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CryptoWhite)
                    if (lastUpdated.isNotEmpty())
                        Text("Binance · $lastUpdated", fontSize = 11.sp, color = CryptoGray)
                }
                IconButton(onClick = { isLoading = true; searchResult = null; searchQuery = "" }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = CryptoGreen)
                }
            }

            Spacer(Modifier.height(10.dp))

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
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = {
                    coroutineScope.launch {
                        val q = searchQuery.trim(); if (q.isEmpty()) return@launch
                        searchLoading = true; searchError = null; searchResult = null
                        val result = fetchCoinPrice(q)
                        if (result != null) searchResult = result
                        else searchError = "لم يُعثر على \"$q\""
                        searchLoading = false
                        focusManager.clearFocus()
                    }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CryptoGreen, unfocusedBorderColor = CryptoDarkSurface,
                    focusedTextColor = CryptoWhite, unfocusedTextColor = CryptoWhite,
                    cursorColor = CryptoGreen, focusedContainerColor = CryptoDarkSurface,
                    unfocusedContainerColor = CryptoDarkSurface
                ),
                shape = RoundedCornerShape(12.dp), singleLine = true
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                    item { CoinCard(coin = coin, onClick = { chartCoin = coin }) }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("العملات الرئيسية", color = CryptoGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("• اضغط لعرض الشارت 📊", color = CryptoGray.copy(0.6f), fontSize = 10.sp)
                }
            }

            if (isLoading) {
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
                    CoinCard(coin = coin, onClick = { chartCoin = coin })
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(CryptoDarkCard).clickable(onClick = onClick).padding(14.dp),
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
            Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
                .background(changeColor.copy(0.15f))
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("${if (isPositive) "+" else ""}${String.format("%.2f", coin.change24h)}%",
                    color = changeColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Icon(Icons.Default.ShowChart, contentDescription = null, tint = CryptoGray.copy(0.5f), modifier = Modifier.size(16.dp))
    }
}

// ─── شاشة TradingView كاملة ───────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TradingViewFullScreen(coin: CoinPrice, onBack: () -> Unit) {
    val isPositive = coin.change24h >= 0

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF131722))) {

        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(CryptoDarkCard)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = CryptoWhite)
            }
            Text(coin.emoji, fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(coin.name, color = CryptoWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatPrice(coin.price), color = CryptoWhite, fontSize = 14.sp)
                    Text(
                        "${if (isPositive) "+" else ""}${String.format("%.2f", coin.change24h)}%",
                        color = if (isPositive) CryptoGreen else CryptoRed,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
            // إحصائيات مصغرة
            Column(horizontalAlignment = Alignment.End) {
                Text("H: ${formatPrice(coin.high24h)}", color = CryptoGreen, fontSize = 10.sp)
                Text("L: ${formatPrice(coin.low24h)}", color = CryptoRed, fontSize = 10.sp)
            }
        }

        // TradingView WebView — شاشة كاملة
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
                    }
                    webChromeClient = WebChromeClient()
                    setBackgroundColor(android.graphics.Color.parseColor("#131722"))
                    loadDataWithBaseURL(
                        "https://s3.tradingview.com",
                        buildTvHtml(coin.symbol),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun formatPrice(price: Double) = when {
    price >= 1000  -> "$${String.format("%,.2f", price)}"
    price >= 1     -> "$${String.format("%.4f", price)}"
    price >= 0.001 -> "$${String.format("%.6f", price)}"
    else           -> "$${String.format("%.8f", price)}"
}
