package com.nabil.usdtwallet.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class CryptoPrices(
    val usdtUsd: Double = 1.0,
    val bnbUsd: Double = 0.0,
    val trxUsd: Double = 0.0,
    val usdSyp: Double = 14000.0  // سيُحدَّث من sp-today.com
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

        // ─── 1. جلب أسعار العملات الرقمية من CoinGecko ──────
        try {
            val url = "https://api.coingecko.com/api/v3/simple/price?ids=binancecoin,tron&vs_currencies=usd"
            val response = URL(url).readText()
            val json = JSONObject(response)
            bnbUsd = json.optJSONObject("binancecoin")?.optDouble("usd", bnbUsd) ?: bnbUsd
            trxUsd = json.optJSONObject("tron")?.optDouble("usd", trxUsd) ?: trxUsd
            Log.i(TAG, "✅ أسعار العملات: BNB=$bnbUsd, TRX=$trxUsd")
        } catch (e: Exception) {
            Log.e(TAG, "❌ فشل CoinGecko: ${e.message}")
        }

        // ─── 2. جلب سعر الدولار مقابل الليرة من sp-today.com ─
        try {
            val html = URL("https://sp-today.com/ar").readText()

            // البحث عن النمط: "14,000" أو "14000" في HTML
            // sp-today يعرض السعر بصيغة "14,000" أو "١٤٬٠٠٠"
            val patterns = listOf(
                Regex(""""usd"[^}]*?"buy"\s*:\s*"?([\d,،٠-٩]+)"?"""),
                Regex("""دولار[^>]*?>\s*([\d,،]+)\s*</"""),
                Regex(""""buy_rate"\s*:\s*"?([\d,.]+)"?"""),
                Regex(""""rate"\s*:\s*"?([\d,.]+)"?""")
            )

            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val rawNum = match.groupValues[1]
                        .replace(",", "")
                        .replace("،", "")
                        .replace("٠", "0").replace("١", "1").replace("٢", "2")
                        .replace("٣", "3").replace("٤", "4").replace("٥", "5")
                        .replace("٦", "6").replace("٧", "7").replace("٨", "8")
                        .replace("٩", "9")
                    val parsed = rawNum.toDoubleOrNull()
                    if (parsed != null && parsed > 1000) {
                        usdSyp = parsed
                        Log.i(TAG, "✅ سعر الدولار من sp-today: $usdSyp ل.س")
                        break
                    }
                }
            }

            // إذا فشلت الـ patterns، نجرّب طريقة بديلة
            if (usdSyp == cachedPrices.usdSyp) {
                // البحث عن أي رقم بين 10000 و 20000 في الصفحة (نطاق معقول لسعر الدولار)
                val numberPattern = Regex("""(\d{2}[,،]?\d{3})""")
                val matches = numberPattern.findAll(html)
                for (m in matches) {
                    val num = m.groupValues[1].replace(",", "").replace("،", "").toDoubleOrNull()
                    if (num != null && num in 10000.0..25000.0) {
                        usdSyp = num
                        Log.i(TAG, "✅ سعر الدولار (بديل): $usdSyp ل.س")
                        break
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ فشل sp-today: ${e.message}")
            // احتياطي: نحاول lirat.org
            try {
                val html2 = URL("https://lirat.org/").readText()
                val pattern = Regex("""دولار[^>]*>\s*(\d{4,6})""")
                val match = pattern.find(html2)
                val parsed = match?.groupValues?.get(1)?.toDoubleOrNull()
                if (parsed != null && parsed > 1000) {
                    usdSyp = parsed
                    Log.i(TAG, "✅ سعر الدولار من lirat.org: $usdSyp ل.س")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "❌ فشل lirat.org: ${e2.message}")
            }
        }

        cachedPrices = CryptoPrices(
            usdtUsd = 1.0,
            bnbUsd  = bnbUsd,
            trxUsd  = trxUsd,
            usdSyp  = usdSyp
        )
        lastFetch = now
        cachedPrices
    }
}
