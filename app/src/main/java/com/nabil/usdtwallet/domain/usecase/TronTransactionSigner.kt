package com.nabil.usdtwallet.domain.usecase

import android.util.Log
import com.google.gson.Gson
import com.nabil.usdtwallet.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.core.Utils
import java.math.BigInteger

/**
 * يوقّع ويبث معاملة USDT TRC-20
 * الخطوات:
 *  1. triggerSmartContract → نحصل على rawData للمعاملة
 *  2. نوقّع بالمفتاح الخاص
 *  3. broadcastTransaction → نبث للشبكة
 */
object TronTransactionSigner {

    private val api = TronApiClient.create()
    private val gson = Gson()
    private const val TAG = "TronSigner"

    suspend fun sendUsdt(
        fromAddress: String,
        toAddress: String,
        amountUsdt: Double,
        privateKeyHex: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. تحويل المبلغ لـ sun (6 decimals)
            val amountSun = (amountUsdt * 1_000_000).toLong()

            // 2. بناء parameter لـ transfer(address,uint256)
            val parameter = buildTransferParameter(toAddress, amountSun)

            // 3. طلب المعاملة من TronGrid
            val triggerRequest = TronTransactionRequest(
                ownerAddress = fromAddress,
                toAddress = toAddress,
                contractAddress = USDT_CONTRACT_TRC20,
                functionSelector = "transfer(address,uint256)",
                parameter = parameter,
                feeLimit = 15_000_000L,
                visible = true
            )

            val triggerResponse = api.triggerSmartContract(triggerRequest)

            if (triggerResponse.result?.success != true) {
                val msg = triggerResponse.result?.message ?: "فشل في بناء المعاملة"
                Log.e(TAG, "Trigger failed: $msg")
                return@withContext Result.Error("فشل في بناء المعاملة: $msg")
            }

            val transaction = triggerResponse.transaction
                ?: return@withContext Result.Error("لم يتم استلام بيانات المعاملة")

            // 4. توقيع المعاملة
            val signedTx = signTransaction(transaction, privateKeyHex)

            // 5. بث المعاملة
            val broadcastResponse = api.broadcastTransaction(signedTx)

            if (broadcastResponse.result) {
                val txId = broadcastResponse.txId ?: ""
                Log.i(TAG, "✅ تم الإرسال: $txId")
                Result.Success(txId)
            } else {
                val msg = broadcastResponse.message ?: "فشل البث"
                Log.e(TAG, "Broadcast failed: $msg")
                Result.Error("فشل الإرسال: $msg")
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في الإرسال: ${e.message}", e)
            Result.Error("خطأ: ${e.message}")
        }
    }

    /**
     * بناء ABI parameter لـ transfer(address,uint256)
     * العنوان: 32 بايت (12 صفر + 20 بايت عنوان بدون بادئة 0x41)
     * المبلغ:  32 بايت uint256
     */
    private fun buildTransferParameter(toAddress: String, amountSun: Long): String {
        val addressBytes = base58ToBytes(toAddress)
        // نأخذ آخر 20 بايت (بدون بادئة 0x41)
        val addressHex = Utils.HEX.encode(addressBytes.drop(1).toByteArray())
        val paddedAddress = addressHex.padStart(64, '0')

        val amountHex = amountSun.toString(16)
        val paddedAmount = amountHex.padStart(64, '0')

        return paddedAddress + paddedAmount
    }

    /**
     * توقيع المعاملة بـ ECDSA secp256k1
     */
    @Suppress("UNCHECKED_CAST")
    private fun signTransaction(
        transaction: Map<String, Any>,
        privateKeyHex: String
    ): Map<String, Any> {
        val txId = transaction["txID"] as? String
            ?: throw IllegalStateException("txID غير موجود")

        val txBytes = Utils.HEX.decode(txId)
        val ecKey = ECKey.fromPrivate(BigInteger(privateKeyHex, 16))
        val signature = ecKey.sign(Sha256Hash.wrap(txBytes))

        // تنسيق التوقيع: r (32) + s (32) + v (1)
        val r = signature.r.toByteArray().let {
            if (it.size > 32) it.drop(1).toByteArray() else it.copyOf(32)
        }
        val s = signature.s.toByteArray().let {
            if (it.size > 32) it.drop(1).toByteArray() else it.copyOf(32)
        }
        val v = byteArrayOf(0x1b)
        val sigHex = Utils.HEX.encode(r + s + v)

        val signedTx = transaction.toMutableMap()
        signedTx["signature"] = listOf(sigHex)

        return signedTx
    }

    /**
     * Base58Check decode لعنوان Tron
     */
    private fun base58ToBytes(address: String): ByteArray {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = BigInteger.ZERO
        for (char in address) {
            val digit = alphabet.indexOf(char)
            if (digit < 0) throw IllegalArgumentException("حرف غير صحيح: $char")
            num = num.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit.toLong()))
        }
        val bytes = num.toByteArray()
        return if (bytes.size > 25) bytes.drop(bytes.size - 25).toByteArray()
        else bytes.copyOf(25)
    }
}
