package com.nabil.usdtwallet.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object SolanaApiService {

    private const val TAG = "SolanaApi"

    // عقد USDT على Solana (SPL Token)
    const val USDT_MINT = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"

    // RPC endpoints (مجانية)
    private val RPC_ENDPOINTS = listOf(
        "https://api.mainnet-beta.solana.com",
        "https://solana-api.projectserum.com",
        "https://rpc.ankr.com/solana"
    )

    private suspend fun rpcCall(method: String, params: List<Any>): JSONObject? =
        withContext(Dispatchers.IO) {
            for (endpoint in RPC_ENDPOINTS) {
                try {
                    val body = JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("id", 1)
                        put("method", method)
                        put("params", JSONArray(params))
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

    // ─── رصيد SOL ─────────────────────────────────────────
    suspend fun getSolBalance(address: String): Double = withContext(Dispatchers.IO) {
        try {
            val result = rpcCall("getBalance", listOf(address))
            val lamports = result?.getJSONObject("result")?.getLong("value") ?: 0L
            lamports / 1_000_000_000.0 // 1 SOL = 1e9 lamports
        } catch (e: Exception) {
            Log.e(TAG, "خطأ رصيد SOL: ${e.message}")
            0.0
        }
    }

    // ─── رصيد USDT SPL ────────────────────────────────────
    suspend fun getUsdtBalance(address: String): Double = withContext(Dispatchers.IO) {
        try {
            val params = listOf(
                address,
                mapOf("mint" to USDT_MINT),
                mapOf("encoding" to "jsonParsed")
            )
            val result = rpcCall("getTokenAccountsByOwner", params)
            val accounts = result?.getJSONObject("result")?.getJSONArray("value") ?: return@withContext 0.0

            var total = 0.0
            for (i in 0 until accounts.length()) {
                val info = accounts.getJSONObject(i)
                    .getJSONObject("account")
                    .getJSONObject("data")
                    .getJSONObject("parsed")
                    .getJSONObject("info")
                    .getJSONObject("tokenAmount")
                total += info.getDouble("uiAmount")
            }
            total
        } catch (e: Exception) {
            Log.e(TAG, "خطأ رصيد USDT SPL: ${e.message}")
            0.0
        }
    }

    // ─── جلب المعاملات الأخيرة ────────────────────────────
    suspend fun getRecentTransactions(address: String, limit: Int = 10): List<Transaction> =
        withContext(Dispatchers.IO) {
            try {
                val sigParams = listOf(address, mapOf("limit" to limit))
                val sigsResult = rpcCall("getSignaturesForAddress", sigParams)
                val sigs = sigsResult?.getJSONObject("result") as? JSONArray ?: return@withContext emptyList()

                val txList = mutableListOf<Transaction>()
                for (i in 0 until minOf(sigs.length(), 5)) {
                    val sig = sigs.getJSONObject(i)
                    val blockTime = sig.optLong("blockTime", 0L) * 1000L
                    txList.add(Transaction(
                        txId = sig.getString("signature"),
                        from = address,
                        to = "",
                        amount = 0.0,
                        timestamp = blockTime,
                        isIncoming = false
                    ))
                }
                txList
            } catch (e: Exception) {
                Log.e(TAG, "خطأ في المعاملات: ${e.message}")
                emptyList()
            }
        }
}
