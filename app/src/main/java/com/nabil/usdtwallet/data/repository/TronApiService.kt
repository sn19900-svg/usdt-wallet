package com.nabil.usdtwallet.data.repository

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// ─── Models ───────────────────────────────────────────────

data class TronAccountResponse(
    @SerializedName("balance") val trxBalance: Long = 0,
    @SerializedName("trc20") val trc20: List<Map<String, String>> = emptyList()
)

data class TronTransactionRequest(
    @SerializedName("owner_address") val ownerAddress: String,
    @SerializedName("to_address") val toAddress: String,
    @SerializedName("contract_address") val contractAddress: String,
    @SerializedName("function_selector") val functionSelector: String = "transfer(address,uint256)",
    @SerializedName("parameter") val parameter: String,
    @SerializedName("fee_limit") val feeLimit: Long = 15000000,
    @SerializedName("call_value") val callValue: Long = 0,
    @SerializedName("visible") val visible: Boolean = true
)

data class TronTransactionResponse(
    @SerializedName("result") val result: TronResult? = null,
    @SerializedName("transaction") val transaction: Map<String, Any>? = null,
    @SerializedName("txID") val txId: String? = null
)

data class TronResult(
    @SerializedName("result") val success: Boolean = false,
    @SerializedName("message") val message: String? = null
)

data class TronBroadcastRequest(
    @SerializedName("transaction") val transaction: String
)

data class TronBroadcastResponse(
    @SerializedName("result") val result: Boolean = false,
    @SerializedName("txid") val txId: String? = null,
    @SerializedName("message") val message: String? = null
)

data class TronTxListResponse(
    @SerializedName("data") val data: List<TronTxItem> = emptyList(),
    @SerializedName("success") val success: Boolean = false
)

data class TronTxItem(
    @SerializedName("transaction_id") val txId: String = "",
    @SerializedName("block_timestamp") val timestamp: Long = 0,
    @SerializedName("from") val from: String = "",
    @SerializedName("to") val to: String = "",
    @SerializedName("value") val value: String = "0",
    @SerializedName("type") val type: String = ""
)

// ─── API Interface ─────────────────────────────────────────

interface TronApiService {

    @GET("v1/accounts/{address}")
    suspend fun getAccount(
        @Path("address") address: String
    ): TronAccountResponse

    @POST("wallet/triggersmartcontract")
    suspend fun triggerSmartContract(
        @Body request: TronTransactionRequest
    ): TronTransactionResponse

    @POST("wallet/broadcasttransaction")
    suspend fun broadcastTransaction(
        @Body transaction: Map<String, Any>
    ): TronBroadcastResponse

    @GET("v1/accounts/{address}/transactions/trc20")
    suspend fun getTrc20Transactions(
        @Path("address") address: String,
        @Query("limit") limit: Int = 20,
        @Query("contract_address") contractAddress: String = USDT_CONTRACT_TRC20
    ): TronTxListResponse
}

// ─── Client Builder ────────────────────────────────────────

object TronApiClient {

    const val USDT_CONTRACT_TRC20 = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
    private const val BASE_URL = "https://api.trongrid.io/"

    // احصل على API Key مجاني من: https://www.trongrid.io/
    var API_KEY = "" // اختياري - يزيد الحد من 100 لـ 1000 طلب/ثانية

    fun create(): TronApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    if (API_KEY.isNotEmpty()) addHeader("TRON-PRO-API-KEY", API_KEY)
                    addHeader("Accept", "application/json")
                }.build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TronApiService::class.java)
    }
}

const val USDT_CONTRACT_TRC20 = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
