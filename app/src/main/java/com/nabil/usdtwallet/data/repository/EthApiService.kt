package com.nabil.usdtwallet.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ethereum Mainnet API
 * يستخدم Ankr Public RPC (مجاني بدون مفتاح)
 * عقد USDT ERC-20: 0xdAC17F958D2ee523a2206206994597C13D831ec7
 */
object EthApiService {

    private const val TAG = "EthApi"
    const val USDT_CONTRACT = "0xdAC17F958D2ee523a2206206994597C13D831ec7"

    private val RPC_ENDPOINTS = listOf(
        "https://rpc.ankr.com/eth",
        "https://ethereum.publicnode.com",
        "https://eth.llamarpc.com"
    )

    private suspend fun rpcCall(method: String, params: List<Any>): JSONObject? =
        withContext(Dispatchers.IO) {
            for (endpoint in RPC_ENDPOINTS) {
                try {
                    val body = JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("id", 1)
                        put("method", method)
                        put("params", org.json.JSONArray(params))
                    }
                    val conn = URL(endpoint).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.doOutput = true
                    conn.outputStream.write(body.toString().toByteArray())
                    val response = JSONObject(conn.inputStream.bufferedReader().readText())
                    conn.disconnect()
                    if (!response.has("error")) return@withContext response
                } catch (e: Exception) {
                    Log.w(TAG, "RPC $endpoint فشل: ${e.message}")
                }
            }
            null
        }

    // رصيد ETH
    suspend fun getEthBalance(address: String): Double = withContext(Dispatchers.IO) {
        try {
            val result = rpcCall("eth_getBalance", listOf(address, "latest"))
            val hex = result?.getJSONObject("result")?.toString()?.trim('"') ?: "0x0"
            hex.removePrefix("0x").toBigInteger(16).toBigDecimal()
                .divide(java.math.BigDecimal.TEN.pow(18)).toDouble()
        } catch (e: Exception) {
            Log.e(TAG, "خطأ رصيد ETH: ${e.message}")
            0.0
        }
    }

    // رصيد USDT ERC-20
    suspend fun getUsdtBalance(address: String): Double = withContext(Dispatchers.IO) {
        try {
            val paddedAddr = address.removePrefix("0x").padStart(64, '0')
            val data = "0x70a08231$paddedAddr" // balanceOf(address)
            val params = listOf(
                mapOf("to" to USDT_CONTRACT, "data" to data),
                "latest"
            )
            val result = rpcCall("eth_call", params)
            val hex = result?.getJSONObject("result")?.toString()?.trim('"') ?: "0x0"
            val raw = hex.removePrefix("0x").ifEmpty { "0" }.toBigInteger(16)
            // USDT ERC-20 يستخدم 6 decimals (مثل TRC-20)
            raw.toBigDecimal().divide(java.math.BigDecimal.TEN.pow(6)).toDouble()
        } catch (e: Exception) {
            Log.e(TAG, "خطأ رصيد USDT ERC-20: ${e.message}")
            0.0
        }
    }
}
