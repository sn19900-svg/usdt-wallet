package com.nabil.usdtwallet.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    private const val CACHE_DURATION = 120_000L // دقيقتان

    suspend fun getPrices(): CryptoPrices = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastFetch < CACHE_DURATION) return@withContext cachedPrices

        var bnbUsd = cachedPrices.bnbUsd
        var trxUsd = cachedPrices.trxUsd
        var solUsd = cachedPrices.solUsd
        var usdSyp = cachedPrices.usdSyp

        // ─── 1. أسعار العملات من Binance ─────────────────────
        try {
            val symbols = "%5B%22BNBUSDT%22,%22TRXUSDT%22,%22SOLUSDT%22%5D" // ["BNBUSDT","TRXUSDT"]
            val json = JSONArray(
                URL("https://api.binance.com/api/v3/ticker/price?symbols=$symbols").readText()
            )
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val symbol = obj.getString("symbol")
                val price = obj.getString("price").toDoubleOrNull() ?: 0.0
                when (symbol) {
                    "BNBUSDT" -> bnbUsd = price
                    "TRXUSDT" -> trxUsd = price
                }
            }
            Log.i(TAG, "✅ Binance: BNB=$bnbUsd, TRX=$trxUsd")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Binance prices: ${e.message}")
        }

        // ─── 2. سعر USD/SYP من sp-today.com ──────────────────
        // المنطق: نجرب عدة أنماط لاستخراج السعر بدقة
        try {
            val conn = URL("https://sp-today.com/en").openConnection() as HttpsURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
            conn.setRequestProperty("Accept", "text/html")
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            val html = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            var found = false

            // النمط 1: data-buy="13650" أو data-sell="13750"
            val dataBuy = Regex("""data-buy=["\']?([\d]+)["\']?""").find(html)
            if (dataBuy != null) {
                val buy = dataBuy.groupValues[1].toDoubleOrNull()
                val dataSell = Regex("""data-sell=["\']?([\d]+)["\']?""").find(html)
                val sell = dataSell?.groupValues?.get(1)?.toDoubleOrNull()
                if (buy != null && buy > 5000) {
                    usdSyp = if (sell != null && sell > 5000) (buy + sell) / 2.0 else buy
                    Log.i(TAG, "✅ sp-today نمط1: $usdSyp ل.س")
                    found = true
                }
            }

            // النمط 2: "US Dollar" متبوعاً برقمين (شراء - بيع)
            if (!found) {
                val p2 = Regex("""US Dollar\D+([\d,]+)\D+([\d,]+)""").find(html)
                if (p2 != null) {
                    val buy = p2.groupValues[1].replace(",", "").toDoubleOrNull()
                    val sell = p2.groupValues[2].replace(",", "").toDoubleOrNull()
                    if (buy != null && buy > 5000) {
                        usdSyp = if (sell != null) (buy + sell) / 2.0 else buy
                        Log.i(TAG, "✅ sp-today نمط2: $usdSyp ل.س")
                        found = true
                    }
                }
            }

            // النمط 3: أي رقم من 5 خانات بين 10000 و 25000
            if (!found) {
                val p3 = Regex("""(\d{5})""").findAll(html)
                for (m in p3) {
                    val v = m.groupValues[1].toDoubleOrNull() ?: continue
                    if (v in 10000.0..25000.0) {
                        usdSyp = v
                        Log.i(TAG, "✅ sp-today نمط3: $usdSyp ل.س")
                        found = true
                        break
                    }
                }
            }

            if (!found) Log.w(TAG, "⚠️ sp-today: لم يُعثر على سعر")

        } catch (e: Exception) {
            Log.e(TAG, "❌ sp-today: ${e.message}")

            // ─── 3. احتياطي: جلب من exchangerate-api ─────────
            // ملاحظة: SYP الرسمي ≠ السوق السوداء
            // نستخدمه كآخر خيار مع تعديل معامل السوق الموازي
            try {
                val json = JSONObject(
                    URL("https://open.er-api.com/v6/latest/USD").readText()
                )
                val rates = json.optJSONObject("rates")
                val officialRate = rates?.optDouble("SYP", 0.0) ?: 0.0
                // السعر الرسمي السوري ~12,500 لكن السوق الموازي أعلى
                // نضرب × 1.1 كتقريب معقول
                if (officialRate > 1000) {
                    usdSyp = officialRate * 1.1
                    Log.i(TAG, "✅ er-api (تقريبي): $usdSyp ل.س")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "❌ er-api: ${e2.message}")
            }
        }

        cachedPrices = CryptoPrices(
            usdtUsd = 1.0,
            bnbUsd = bnbUsd,
            trxUsd = trxUsd,
            usdSyp = usdSyp
        )
        lastFetch = now
        Log.i(TAG, "📊 النهائي: BNB=$bnbUsd TRX=$trxUsd USD/SYP=$usdSyp")
        cachedPrices
    }
}
