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
                val account = api.getAccount(address)

                // TRX balance (من sun إلى TRX، 1 TRX = 1,000,000 sun)
                val trxBalance = account.trxBalance / 1_000_000.0

                // USDT TRC-20 balance
                val usdtBalance = account.trc20
                    .find { it.containsKey(TronApiClient.USDT_CONTRACT_TRC20) }
                    ?.get(TronApiClient.USDT_CONTRACT_TRC20)
                    ?.toLongOrNull()
                    ?.let { it / 1_000_000.0 } // USDT يملك 6 decimals
                    ?: 0.0

                Result.Success(WalletBalance(usdt = usdtBalance, trx = trxBalance))
            } catch (e: Exception) {
                Log.e(TAG, "خطأ في جلب الرصيد: ${e.message}")
                Result.Error("تعذّر جلب الرصيد: ${e.message}")
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
