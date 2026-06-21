package com.nabil.usdtwallet.domain.usecase

import android.util.Log
import com.nabil.usdtwallet.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.crypto.digests.SHA256Digest
import java.math.BigInteger

/**
 * يوقّع ويبث معاملة USDT TRC-20
 * الخطوات:
 *  1. triggerSmartContract → نحصل على rawData للمعاملة
 *  2. نوقّع بالمفتاح الخاص (ECDSA secp256k1 عبر BouncyCastle)
 *  3. broadcastTransaction → نبث للشبكة
 */
object TronTransactionSigner {

    private val api get() = TronApiClient.create()
    private const val TAG = "TronSigner"

    private val curveParams = SECNamedCurves.getByName("secp256k1")
    private val domainParams = ECDomainParameters(curveParams.curve, curveParams.g, curveParams.n, curveParams.h)

    suspend fun sendUsdt(
        fromAddress: String,
        toAddress: String,
        amountUsdt: Double,
        privateKeyHex: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val amountSun = (amountUsdt * 1_000_000).toLong()
            val parameter = buildTransferParameter(toAddress, amountSun)

            val triggerRequest = TronTransactionRequest(
                ownerAddress = fromAddress,
                toAddress = toAddress,
                contractAddress = TronApiClient.USDT_CONTRACT_TRC20,
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

            val signedTx = signTransaction(transaction, privateKeyHex)
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

    suspend fun sendTrx(
        fromAddress: String,
        toAddress: String,
        amountTrx: Double,
        privateKeyHex: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val amountSun = (amountTrx * 1_000_000).toLong()

            val request = TrxTransferRequest(
                ownerAddress = fromAddress,
                toAddress = toAddress,
                amount = amountSun,
                visible = true
            )

            val createResponse = api.createTrxTransaction(request)
            val transaction = createResponse.transaction
                ?: return@withContext Result.Error("لم يتم استلام بيانات المعاملة")

            // معاملة TRX المباشرة لا تحتوي result.result لأنها ليست عقداً ذكياً
            val signedTx = signTransaction(transaction, privateKeyHex)
            val broadcastResponse = api.broadcastTransaction(signedTx)

            if (broadcastResponse.result) {
                val txId = broadcastResponse.txId ?: ""
                Log.i(TAG, "✅ تم إرسال TRX: $txId")
                Result.Success(txId)
            } else {
                val msg = broadcastResponse.message ?: "فشل البث"
                Log.e(TAG, "Broadcast TRX failed: $msg")
                Result.Error("فشل الإرسال: $msg")
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في إرسال TRX: ${e.message}", e)
            Result.Error("خطأ: ${e.message}")
        }
    }

    private fun buildTransferParameter(toAddress: String, amountSun: Long): String {
        val addressBytes = base58ToBytes(toAddress)
        val addressHex = bytesToHex(addressBytes.drop(1).toByteArray())
        val paddedAddress = addressHex.padStart(64, '0')
        val amountHex = amountSun.toString(16)
        val paddedAmount = amountHex.padStart(64, '0')
        return paddedAddress + paddedAmount
    }

    private fun signTransaction(
        transaction: com.google.gson.JsonObject,
        privateKeyHex: String
    ): com.google.gson.JsonObject {
        val txId = transaction.get("txID")?.asString
            ?: throw IllegalStateException("txID غير موجود")

        val txBytes = hexToBytes(txId)
        val privateKeyInt = BigInteger(privateKeyHex, 16)

        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        val privKeyParams = ECPrivateKeyParameters(privateKeyInt, domainParams)
        signer.init(true, privKeyParams)

        val sig = signer.generateSignature(txBytes)
        var r = sig[0]
        var s = sig[1]

        val halfCurveOrder = domainParams.n.shiftRight(1)
        if (s > halfCurveOrder) {
            s = domainParams.n.subtract(s)
        }

        val recId = findRecoveryId(txBytes, r, s, privateKeyInt)

        val rBytes = bigIntTo32Bytes(r)
        val sBytes = bigIntTo32Bytes(s)
        val vByte = byteArrayOf((27 + recId).toByte())

        val sigHex = bytesToHex(rBytes + sBytes + vByte)

        val signedTx = transaction.deepCopy()
        val sigArray = com.google.gson.JsonArray()
        sigArray.add(sigHex)
        signedTx.add("signature", sigArray)

        return signedTx
    }

    private fun findRecoveryId(messageHash: ByteArray, r: BigInteger, s: BigInteger, privateKey: BigInteger): Int {
        val expectedPublicKey = curveParams.g.multiply(privateKey).normalize()

        for (recId in 0..1) {
            try {
                val recovered = recoverPublicKey(messageHash, r, s, recId)
                if (recovered != null && recovered.equals(expectedPublicKey)) {
                    return recId
                }
            } catch (e: Exception) {
                // جرّب القيمة التالية
            }
        }
        return 0
    }

    private fun recoverPublicKey(
        messageHash: ByteArray,
        r: BigInteger,
        s: BigInteger,
        recId: Int
    ): org.bouncycastle.math.ec.ECPoint? {
        val n = domainParams.n
        val curve = domainParams.curve

        val x = r.add(n.multiply(BigInteger.valueOf((recId / 2).toLong())))
        val prime = (curve as org.bouncycastle.math.ec.ECCurve.Fp).q
        if (x >= prime) return null

        val rPoint = decompressKey(x, recId and 1 == 1)
        if (!rPoint.multiply(n).isInfinity) return null

        val e = BigInteger(1, messageHash)
        val eInv = BigInteger.ZERO.subtract(e).mod(n)
        val rInv = r.modInverse(n)
        val srInv = rInv.multiply(s).mod(n)
        val eInvrInv = rInv.multiply(eInv).mod(n)

        val q = domainParams.g.multiply(eInvrInv).add(rPoint.multiply(srInv))
        return q.normalize()
    }

    private fun decompressKey(xBN: BigInteger, yBit: Boolean): org.bouncycastle.math.ec.ECPoint {
        val curve = domainParams.curve as org.bouncycastle.math.ec.ECCurve.Fp
        val compEnc = org.bouncycastle.util.BigIntegers.asUnsignedByteArray(curve.fieldSize / 8, xBN)
        val prefix = if (yBit) 0x03 else 0x02
        val encoded = byteArrayOf(prefix.toByte()) + compEnc
        return curve.decodePoint(encoded)
    }

    private fun bigIntTo32Bytes(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return when {
            bytes.size == 32 -> bytes
            bytes.size > 32 -> bytes.drop(bytes.size - 32).toByteArray()
            else -> ByteArray(32 - bytes.size) + bytes
        }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = if (hex.length % 2 != 0) "0$hex" else hex
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

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
        else ByteArray(25 - bytes.size) + bytes
    }
}
