package com.nabil.usdtwallet.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL

data class CryptoPrices(
    val usdtUsd: Double = 1.0,
    val bnbUsd: Double = 0.0,
    val trxUsd: Double = 0.0,
    val solUsd: Double = 0.0,
    val ethUsd: Double = 0.0
)

object PriceRepository {

    private const val TAG = "PriceRepository"
    private var cachedPrices: CryptoPrices = CryptoPrices()
    private var lastFetch: Long = 0
    private const val CACHE_DURATION = 120_000L

    suspend fun getPrices(): CryptoPrices = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastFetch < CACHE_DURATION) return@withContext cachedPrices

        var bnbUsd = cachedPrices.bnbUsd
        var trxUsd = cachedPrices.trxUsd
        var solUsd = cachedPrices.solUsd
        var ethUsd = cachedPrices.ethUsd

        try {
            val symbols = "%5B%22BNBUSDT%22,%22TRXUSDT%22,%22SOLUSDT%22,%22ETHUSDT%22%5D"
            val arr = JSONArray(
                URL("https://api.binance.com/api/v3/ticker/price?symbols=$symbols").readText()
            )
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val price = obj.getString("price").toDoubleOrNull() ?: 0.0
                when (obj.getString("symbol")) {
                    "BNBUSDT" -> bnbUsd = price
                    "TRXUSDT" -> trxUsd = price
                    "SOLUSDT" -> solUsd = price
                    "ETHUSDT" -> ethUsd = price
                }
            }
            Log.i(TAG, "✅ Binance: BNB=$bnbUsd TRX=$trxUsd SOL=$solUsd ETH=$ethUsd")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Binance: ${e.message}")
        }

        cachedPrices = CryptoPrices(
            usdtUsd = 1.0,
            bnbUsd  = bnbUsd,
            trxUsd  = trxUsd,
            solUsd  = solUsd,
            ethUsd  = ethUsd
        )
        lastFetch = now
        cachedPrices
    }
}
