package com.nabil.usdtwallet.domain.usecase

import android.util.Log
import com.nabil.usdtwallet.data.repository.EthApiService
import com.nabil.usdtwallet.data.repository.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.jcajce.provider.digest.Keccak
import org.json.JSONObject
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL

/**
 * إرسال ETH و USDT ERC-20 على Ethereum Mainnet الحقيقية
 * (منفصل تماماً عن BSC رغم نفس الخوارزمية)
 */
object EthTransactionSigner {

    private const val TAG = "EthSigner"
    private const val CHAIN_ID = 1L // Ethereum Mainnet
    private const val GAS_LIMIT_TOKEN = 80_000L
    private const val GAS_LIMIT_ETH = 21_000L

    private val RPC_ENDPOINTS = listOf(
        "https://rpc.ankr.com/eth",
        "https://ethereum.publicnode.com",
        "https://eth.llamarpc.com"
    )

    private val curveParams = SECNamedCurves.getByName("secp256k1")
    private val domainParams = ECDomainParameters(curveParams.curve, curveParams.g, curveParams.n, curveParams.h)

    private suspend fun rpcCall(method: String, params: List<Any>): JSONObject? = withContext(Dispatchers.IO) {
        for (endpoint in RPC_ENDPOINTS) {
            try {
                val body = JSONObject().apply {
                    put("jsonrpc", "2.0"); put("id", 1); put("method", method)
                    put("params", org.json.JSONArray(params))
                }
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 15000; conn.readTimeout = 15000
                conn.doOutput = true
                conn.outputStream.write(body.toString().toByteArray())
                val r = JSONObject(conn.inputStream.bufferedReader().readText())
                conn.disconnect()
                if (!r.has("error")) return@withContext r
            } catch (e: Exception) {
                Log.w(TAG, "$endpoint فشل: ${e.message}")
            }
        }
        null
    }

    suspend fun sendUsdt(fromAddress: String, toAddress: String, amountUsdt: Double, privateKeyHex: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val nonce = getNonce(fromAddress) ?: return@withContext Result.Error("فشل جلب nonce")
                val gasPrice = getGasPrice() ?: return@withContext Result.Error("فشل جلب سعر الغاز")

                val amountRaw = BigInteger.valueOf((amountUsdt * 1e6).toLong()) // USDT ERC-20: 6 decimals
                val paddedTo = toAddress.removePrefix("0x").padStart(64, '0')
                val paddedAmount = amountRaw.toString(16).padStart(64, '0')
                val data = "0xa9059cbb$paddedTo$paddedAmount"

                val rawTx = buildAndSignTx(nonce, gasPrice, GAS_LIMIT_TOKEN, EthApiService.USDT_CONTRACT, BigInteger.ZERO, data, privateKeyHex)
                broadcast(rawTx)
            } catch (e: Exception) {
                Log.e(TAG, "خطأ إرسال USDT ETH: ${e.message}")
                Result.Error("خطأ: ${e.message}")
            }
        }

    suspend fun sendEth(fromAddress: String, toAddress: String, amountEth: Double, privateKeyHex: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val nonce = getNonce(fromAddress) ?: return@withContext Result.Error("فشل جلب nonce")
                val gasPrice = getGasPrice() ?: return@withContext Result.Error("فشل جلب سعر الغاز")
                val amountWei = BigInteger.valueOf((amountEth * 1e18).toLong())
                val rawTx = buildAndSignTx(nonce, gasPrice, GAS_LIMIT_ETH, toAddress, amountWei, "", privateKeyHex)
                broadcast(rawTx)
            } catch (e: Exception) {
                Result.Error("خطأ: ${e.message}")
            }
        }

    private suspend fun getNonce(address: String): BigInteger? =
        rpcCall("eth_getTransactionCount", listOf(address, "latest"))
            ?.optString("result")?.removePrefix("0x")?.ifEmpty { "0" }?.toBigInteger(16)

    private suspend fun getGasPrice(): BigInteger? =
        rpcCall("eth_gasPrice", emptyList())
            ?.optString("result")?.removePrefix("0x")?.ifEmpty { "0" }?.toBigInteger(16)

    private suspend fun broadcast(rawTx: String): Result<String> {
        val response = rpcCall("eth_sendRawTransaction", listOf(rawTx))
        val txHash = response?.optString("result") ?: ""
        return if (txHash.isNotEmpty() && txHash != "null") {
            Log.i(TAG, "✅ ETH تم الإرسال: $txHash")
            Result.Success(txHash)
        } else {
            val err = response?.optJSONObject("error")?.optString("message") ?: "فشل البث"
            Result.Error(err)
        }
    }

    private fun buildAndSignTx(nonce: BigInteger, gasPrice: BigInteger, gasLimit: Long, to: String, value: BigInteger, data: String, privateKeyHex: String): String {
        val toBytes = hexToBytes(to.removePrefix("0x"))
        val dataBytes = if (data.isEmpty()) byteArrayOf() else hexToBytes(data.removePrefix("0x"))

        val rlpForSigning = encodeRlp(listOf(nonce, gasPrice, BigInteger.valueOf(gasLimit), toBytes, value, dataBytes,
            BigInteger.valueOf(CHAIN_ID), BigInteger.ZERO, BigInteger.ZERO))
        val txHash = keccak256(rlpForSigning)

        val privKeyInt = BigInteger(privateKeyHex, 16)
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        signer.init(true, ECPrivateKeyParameters(privKeyInt, domainParams))
        val sig = signer.generateSignature(txHash)
        var r = sig[0]; var s = sig[1]
        val halfOrder = domainParams.n.shiftRight(1)
        if (s > halfOrder) s = domainParams.n.subtract(s)

        val recId = findRecoveryId(txHash, r, s, privKeyInt)
        val v = BigInteger.valueOf(CHAIN_ID * 2 + 35 + recId)

        val signedRlp = encodeRlp(listOf(nonce, gasPrice, BigInteger.valueOf(gasLimit), toBytes, value, dataBytes, v, r, s))
        return "0x" + bytesToHex(signedRlp)
    }

    // ─── RLP + crypto helpers (نفس منطق BscTransactionSigner) ──

    private fun encodeRlp(items: List<Any>): ByteArray {
        val encoded = items.map { encodeRlpItem(it) }.fold(byteArrayOf()) { acc, b -> acc + b }
        return encodeRlpLength(encoded, 0xc0)
    }

    private fun encodeRlpItem(item: Any): ByteArray = when (item) {
        is BigInteger -> {
            if (item == BigInteger.ZERO) byteArrayOf(0x80.toByte())
            else {
                val bytes = item.toByteArray().let { if (it[0] == 0.toByte()) it.drop(1).toByteArray() else it }
                encodeRlpLength(bytes, 0x80)
            }
        }
        is ByteArray -> {
            if (item.isEmpty()) byteArrayOf(0x80.toByte())
            else if (item.size == 1 && item[0].toInt() and 0xff < 0x80) item
            else encodeRlpLength(item, 0x80)
        }
        else -> throw IllegalArgumentException()
    }

    private fun encodeRlpLength(data: ByteArray, offset: Int): ByteArray = when {
        data.size == 1 && offset == 0x80 && data[0].toInt() and 0xff < 0x80 -> data
        data.size <= 55 -> byteArrayOf((offset + data.size).toByte()) + data
        else -> {
            val lenBytes = bigIntTo32Bytes(BigInteger.valueOf(data.size.toLong())).dropWhile { it == 0.toByte() }.toByteArray()
            byteArrayOf((offset + 55 + lenBytes.size).toByte()) + lenBytes + data
        }
    }

    private fun keccak256(input: ByteArray): ByteArray = Keccak.Digest256().digest(input)

    private fun findRecoveryId(hash: ByteArray, r: BigInteger, s: BigInteger, privateKey: BigInteger): Int {
        val expectedPub = curveParams.g.multiply(privateKey).normalize()
        for (recId in 0..1) {
            try {
                val recovered = recoverPublicKey(hash, r, s, recId)
                if (recovered != null && recovered.equals(expectedPub)) return recId
            } catch (_: Exception) {}
        }
        return 0
    }

    private fun recoverPublicKey(hash: ByteArray, r: BigInteger, s: BigInteger, recId: Int): org.bouncycastle.math.ec.ECPoint? {
        val n = domainParams.n
        val curve = domainParams.curve as org.bouncycastle.math.ec.ECCurve.Fp
        val x = r.add(n.multiply(BigInteger.valueOf((recId / 2).toLong())))
        if (x >= curve.q) return null
        val rPoint = decompressKey(x, recId and 1 == 1)
        if (!rPoint.multiply(n).isInfinity) return null
        val e = BigInteger(1, hash)
        val eInv = BigInteger.ZERO.subtract(e).mod(n)
        val rInv = r.modInverse(n)
        return domainParams.g.multiply(rInv.multiply(eInv).mod(n)).add(rPoint.multiply(rInv.multiply(s).mod(n))).normalize()
    }

    private fun decompressKey(xBN: BigInteger, yBit: Boolean): org.bouncycastle.math.ec.ECPoint {
        val curve = domainParams.curve as org.bouncycastle.math.ec.ECCurve.Fp
        val compEnc = org.bouncycastle.util.BigIntegers.asUnsignedByteArray(curve.fieldSize / 8, xBN)
        val prefix = if (yBit) 0x03.toByte() else 0x02.toByte()
        return curve.decodePoint(byteArrayOf(prefix) + compEnc)
    }

    private fun bigIntTo32Bytes(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return when {
            bytes.size == 32 -> bytes
            bytes.size > 32 -> bytes.drop(bytes.size - 32).toByteArray()
            else -> ByteArray(32 - bytes.size) + bytes
        }
    }

    private fun bytesToHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.length % 2 != 0) "0$hex" else hex
        return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
