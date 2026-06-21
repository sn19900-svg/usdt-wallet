package com.nabil.usdtwallet.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WalletBalance(
    val usdt: Double,
    val trx: Double
)

data class Transaction(
    val txId: String,
    val from: String,
    val to: String,
    val amount: Double,
    val timestamp: Long,
    val isIncoming: Boolean
)

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

class WalletRepository {

    private val api get() = TronApiClient.create()
    private val TAG = "WalletRepository"

    suspend fun getBalance(address: String): Result<WalletBalance> {
        return withContext(Dispatchers.IO) {
            try {
                val wrapper = api.getAccount(address)
                val account = wrapper.data.firstOrNull()

                if (account == null) {
                    // الحساب جديد ولم يُفعَّل بعد على الشبكة (لا يوجد أي معاملة واردة بعد)
                    return@withContext Result.Success(WalletBalance(usdt = 0.0, trx = 0.0))
                }

                // TRX balance (من sun إلى TRX، 1 TRX = 1,000,000 sun)
                val trxBalance = account.trxBalance / 1_000_000.0

                // USDT TRC-20 balance - تفكيك JsonArray يدوياً (آمن من مشاكل R8/Generics)
                var usdtBalance = 0.0
                val targetContract = TronApiClient.USDT_CONTRACT_TRC20
                account.trc20?.forEach { element ->
                    val obj = element.asJsonObject
                    if (obj.has(targetContract)) {
                        val raw = obj.get(targetContract).asString
                        usdtBalance = raw.toLongOrNull()?.div(1_000_000.0) ?: 0.0
                    }
                }

                Result.Success(WalletBalance(usdt = usdtBalance, trx = trxBalance))
            } catch (e: Exception) {
                Log.e(TAG, "خطأ في جلب الرصيد: ${e.javaClass.simpleName}: ${e.message}")
                Result.Error("${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    suspend fun getTransactions(address: String): Result<List<Transaction>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getTrc20Transactions(
                    address = address,
                    contractAddress = TronApiClient.USDT_CONTRACT_TRC20
                )
                val txList = response.data.map { item ->
                    Transaction(
                        txId = item.txId,
                        from = item.from,
                        to = item.to,
                        amount = item.value.toLongOrNull()?.div(1_000_000.0) ?: 0.0,
                        timestamp = item.timestamp,
                        isIncoming = item.to.equals(address, ignoreCase = true)
                    )
                }
                Result.Success(txList)
            } catch (e: Exception) {
                Log.e(TAG, "خطأ في جلب المعاملات: ${e.message}")
                Result.Error("تعذّر جلب المعاملات")
            }
        }
    }
}
