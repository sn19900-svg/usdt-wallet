package com.nabil.usdtwallet.data.repository

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// ─── JSON-RPC Request/Response ────────────────────────────

data class JsonRpcRequest(
    @SerializedName("jsonrpc") val jsonrpc: String = "2.0",
    @SerializedName("method")  val method: String,
    @SerializedName("params")  val params: List<Any>,
    @SerializedName("id")      val id: Int = 1
)

data class JsonRpcResponse<T>(
    @SerializedName("jsonrpc") val jsonrpc: String = "",
    @SerializedName("id")      val id: Int = 0,
    @SerializedName("result")  val result: T? = null,
    @SerializedName("error")   val error: JsonRpcError? = null
)

data class JsonRpcError(
    @SerializedName("code")    val code: Int = 0,
    @SerializedName("message") val message: String = ""
)

// ─── API Interface ─────────────────────────────────────────

interface BscApiService {

    // eth_getBalance → رصيد BNB (بالـ Wei)
    @POST(".")
    suspend fun getBalance(
        @Body request: JsonRpcRequest
    ): JsonRpcResponse<String>

    // eth_call → استدعاء قراءة من عقد (لرصيد USDT)
    @POST(".")
    suspend fun ethCall(
        @Body request: JsonRpcRequest
    ): JsonRpcResponse<String>

    // eth_getTransactionCount → nonce
    @POST(".")
    suspend fun getTransactionCount(
        @Body request: JsonRpcRequest
    ): JsonRpcResponse<String>

    // eth_gasPrice
    @POST(".")
    suspend fun getGasPrice(
        @Body request: JsonRpcRequest
    ): JsonRpcResponse<String>

    // eth_sendRawTransaction → بث المعاملة الموقّعة
    @POST(".")
    suspend fun sendRawTransaction(
        @Body request: JsonRpcRequest
    ): JsonRpcResponse<String>

    // eth_getTransactionReceipt → التحقق من نجاح المعاملة
    @POST(".")
    suspend fun getTransactionReceipt(
        @Body request: JsonRpcRequest
    ): JsonRpcResponse<com.google.gson.JsonObject>
}

// ─── Client Builder ────────────────────────────────────────

object BscApiClient {

    // عقد USDT الرسمي على BSC
    const val USDT_CONTRACT_BEP20_MAINNET = "0x55d398326f99059fF775485246999027B3197955"
    const val USDT_CONTRACT_BEP20_TESTNET = "0x337610d27c682E347C9cD60BD4b3b107C9d34dDd"

    private const val MAINNET_URL = "https://bsc-dataseed1.binance.org/"
    private const val TESTNET_URL = "https://bsc-testnet.public.blastapi.io/"

    // Chain ID للتوقيع EIP-155
    const val MAINNET_CHAIN_ID = 56L
    const val TESTNET_CHAIN_ID = 97L

    val USDT_CONTRACT_BEP20: String
        get() = if (NetworkConfig.isTestnet) USDT_CONTRACT_BEP20_TESTNET else USDT_CONTRACT_BEP20_MAINNET

    val CHAIN_ID: Long
        get() = if (NetworkConfig.isTestnet) TESTNET_CHAIN_ID else MAINNET_CHAIN_ID

    fun create(): BscApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val baseUrl = if (NetworkConfig.isTestnet) TESTNET_URL else MAINNET_URL

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BscApiService::class.java)
    }
}

// ─── Helper Extension ──────────────────────────────────────

/**
 * يحوّل hex string من الـ RPC (مثل "0x1a2b") إلى BigInteger
 */
fun String.hexToBigInteger(): java.math.BigInteger {
    val clean = removePrefix("0x").ifEmpty { "0" }
    return java.math.BigInteger(clean, 16)
}
