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
    val usdSyp: Double = 13000.0  // سعر الدولار بالليرة السورية تقريبي
)

object PriceRepository {

    private const val TAG = "PriceRepository"
    private var cachedPrices: CryptoPrices = CryptoPrices()
    private var lastFetch: Long = 0
    private const val CACHE_DURATION = 60_000L // دقيقة واحدة

    suspend fun getPrices(): CryptoPrices = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastFetch < CACHE_DURATION) return@withContext cachedPrices

        try {
            val url = "https://api.coingecko.com/api/v3/simple/price?ids=binancecoin,tron&vs_currencies=usd"
            val response = URL(url).readText()
            val json = JSONObject(response)

            val bnbUsd = json.optJSONObject("binancecoin")?.optDouble("usd", 0.0) ?: 0.0
            val trxUsd = json.optJSONObject("tron")?.optDouble("usd", 0.0) ?: 0.0

            cachedPrices = CryptoPrices(
                usdtUsd = 1.0,
                bnbUsd = bnbUsd,
                trxUsd = trxUsd,
                usdSyp = 13000.0
            )
            lastFetch = now
            Log.i(TAG, "أسعار محدّثة: BNB=$bnbUsd, TRX=$trxUsd")
        } catch (e: Exception) {
            Log.e(TAG, "فشل جلب الأسعار: ${e.message}")
        }
        cachedPrices
    }
}
