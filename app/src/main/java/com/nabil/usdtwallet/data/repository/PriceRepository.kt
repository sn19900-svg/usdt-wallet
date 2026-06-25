package com.nabil.usdtwallet.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class CryptoPrices(
    val usdtUsd: Double = 1.0,
    val bnbUsd: Double = 0.0,
    val trxUsd: Double = 0.0,
    val usdSyp: Double = 14000.0
)

object PriceRepository {

    private const val TAG = "PriceRepository"
    private var cachedPrices: CryptoPrices = CryptoPrices()
    private var lastFetch: Long = 0
    private const val CACHE_DURATION = 300_000L // 5 دقائق

    suspend fun getPrices(): CryptoPrices = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastFetch < CACHE_DURATION) return@withContext cachedPrices

        var bnbUsd = cachedPrices.bnbUsd
        var trxUsd = cachedPrices.trxUsd
        var usdSyp = cachedPrices.usdSyp

        // ─── 1. أسعار العملات الرقمية من CoinGecko ──────────
        try {
            val json = JSONObject(
                URL("https://api.coingecko.com/api/v3/simple/price?ids=binancecoin,tron&vs_currencies=usd")
                    .readText()
            )
            bnbUsd = json.optJSONObject("binancecoin")?.optDouble("usd", bnbUsd) ?: bnbUsd
            trxUsd = json.optJSONObject("tron")?.optDouble("usd", trxUsd) ?: trxUsd
            Log.i(TAG, "✅ CoinGecko: BNB=$bnbUsd, TRX=$trxUsd")
        } catch (e: Exception) {
            Log.e(TAG, "❌ CoinGecko: ${e.message}")
        }

        // ─── 2. سعر الدولار من sp-today.com ─────────────────
        // الصفحة تعرض: "USD...13,650 13,750" (شراء - بيع)
        // نأخذ متوسط الشراء والبيع
        try {
            val conn = URL("https://sp-today.com/en").openConnection() as HttpsURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            conn.setRequestProperty("Accept-Language", "en-US")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val html = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            // النمط الدقيق من الصفحة: "US Dollar 13,650 13,750"
            val pattern = Regex("""US Dollar\s+([\d,]+)\s+([\d,]+)""")
            val match = pattern.find(html)

            if (match != null) {
                val buy  = match.groupValues[1].replace(",", "").toDoubleOrNull()
                val sell = match.groupValues[2].replace(",", "").toDoubleOrNull()
                if (buy != null && sell != null && buy > 1000) {
                    usdSyp = (buy + sell) / 2.0
                    Log.i(TAG, "✅ sp-today: شراء=$buy، بيع=$sell، متوسط=$usdSyp ل.س")
                }
            } else {
                // نمط احتياطي: أول رقم بعد "USD"
                val fallback = Regex("""USD[^>]*?(\d{2,3}[,،]\d{3})""").find(html)
                val parsed = fallback?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
                if (parsed != null && parsed > 1000) {
                    usdSyp = parsed
                    Log.i(TAG, "✅ sp-today (fallback): $usdSyp ل.س")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ sp-today: ${e.message}")
            // ─── 3. موقع lirat.org كاحتياطي ─────────────────
            try {
                val html2 = URL("https://lirat.org/").readText()
                // lirat.org يعرض: "دولار إدلب · 12420"
                val p = Regex("""دولار[^\d]*([\d,]+)""").find(html2)
                val v = p?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
                if (v != null && v > 1000) {
                    usdSyp = v
                    Log.i(TAG, "✅ lirat.org: $usdSyp ل.س")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "❌ lirat.org: ${e2.message}")
            }
        }

        cachedPrices = CryptoPrices(usdtUsd = 1.0, bnbUsd = bnbUsd, trxUsd = trxUsd, usdSyp = usdSyp)
        lastFetch = now
        Log.i(TAG, "📊 الأسعار النهائية: BNB=$bnbUsd, TRX=$trxUsd, USD/SYP=$usdSyp")
        cachedPrices
    }
}
