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
import org.bouncycastle.jcajce.provider.digest.Keccak
import java.math.BigInteger

/**
 * يوقّع ويبث معاملات USDT BEP-20 وBNB على شبكة BSC
 * يستخدم نفس المفتاح الخاص لـ TRC-20 (secp256k1 مشترك)
 *
 * الخطوات:
 *  1. نحصل على nonce + gasPrice من الشبكة
 *  2. نبني RLP encoding للمعاملة
 *  3. نوقّع بـ EIP-155 (يشمل chainId في التوقيع)
 *  4. نبث عبر eth_sendRawTransaction
 */
object BscTransactionSigner {

    private val api get() = BscApiClient.create()
    private const val TAG = "BscSigner"

    // Gas limit قياسي لتحويل ERC-20/BEP-20
    private const val GAS_LIMIT_TOKEN = 65_000L
    // Gas limit لتحويل BNB عادي
    private const val GAS_LIMIT_BNB = 21_000L

    private val curveParams = SECNamedCurves.getByName("secp256k1")
    private val domainParams = ECDomainParameters(
        curveParams.curve, curveParams.g, curveParams.n, curveParams.h
    )

    // ─── عنوان BSC من المفتاح الخاص ───────────────────────

    /**
     * يشتق عنوان BSC (0x...) من المفتاح الخاص
     * نفس المفتاح الخاص المستخدم في TRC-20
     */
    fun getAddressFromPrivateKey(privateKeyHex: String): String {
        val privateKeyInt = BigInteger(privateKeyHex, 16)
        val publicKeyPoint = curveParams.g.multiply(privateKeyInt).normalize()

        // المفتاح العام غير المضغوط (64 بايت بدون البادئة 0x04)
        val xBytes = bigIntTo32Bytes(publicKeyPoint.xCoord.toBigInteger())
        val yBytes = bigIntTo32Bytes(publicKeyPoint.yCoord.toBigInteger())
        val pubKeyBytes = xBytes + yBytes

        // Keccak-256 على المفتاح العام، آخر 20 بايت = العنوان
        val hash = keccak256(pubKeyBytes)
        val addressBytes = hash.takeLast(20).toByteArray()
        return "0x" + bytesToHex(addressBytes)
    }

    // ─── جلب رصيد USDT BEP-20 ─────────────────────────────

    /**
     * يرجع رصيد USDT بـ BEP-20 كـ Double (مقسوماً على 10^18)
     * ملاحظة: USDT على BSC يستخدم 18 decimal (عكس TRC-20 الذي 6)
     */
    suspend fun getUsdtBalance(address: String): Result<Double> = withContext(Dispatchers.IO) {
        try {
            // balanceOf(address) = دالة ERC-20 القياسية
            // selector: keccak256("balanceOf(address)")[0..3] = 0x70a08231
            val paddedAddress = address.removePrefix("0x").padStart(64, '0')
            val data = "0x70a08231$paddedAddress"

            val callParams = mapOf(
                "to" to BscApiClient.USDT_CONTRACT_BEP20,
                "data" to data
            )

            val response = api.ethCall(
                JsonRpcRequest(
                    method = "eth_call",
                    params = listOf(callParams, "latest")
                )
            )

            if (response.error != null) {
                return@withContext Result.Error("خطأ في جلب الرصيد: ${response.error.message}")
            }

            val hex = response.result ?: "0x0"
            val raw = hex.hexToBigInteger()
            // USDT على BSC: 18 decimals
            val balance = raw.toBigDecimal().divide(java.math.BigDecimal.TEN.pow(18)).toDouble()
            Result.Success(balance)

        } catch (e: Exception) {
            Log.e(TAG, "خطأ في جلب رصيد USDT: ${e.message}", e)
            Result.Error("خطأ: ${e.message}")
        }
    }

    // ─── جلب رصيد BNB ──────────────────────────────────────

    suspend fun getBnbBalance(address: String): Result<Double> = withContext(Dispatchers.IO) {
        try {
            val response = api.getBalance(
                JsonRpcRequest(
                    method = "eth_getBalance",
                    params = listOf(address, "latest")
                )
            )

            if (response.error != null) {
                return@withContext Result.Error("خطأ: ${response.error.message}")
            }

            val hex = response.result ?: "0x0"
            val raw = hex.hexToBigInteger()
            // BNB: 18 decimals (Wei)
            val balance = raw.toBigDecimal().divide(java.math.BigDecimal.TEN.pow(18)).toDouble()
            Result.Success(balance)

        } catch (e: Exception) {
            Log.e(TAG, "خطأ في جلب رصيد BNB: ${e.message}", e)
            Result.Error("خطأ: ${e.message}")
        }
    }

    // ─── إرسال USDT BEP-20 ────────────────────────────────

    suspend fun sendUsdt(
        fromAddress: String,
        toAddress: String,
        amountUsdt: Double,
        privateKeyHex: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val nonce = getNonce(fromAddress) ?: return@withContext Result.Error("فشل في جلب nonce")
            val gasPrice = getGasPrice() ?: return@withContext Result.Error("فشل في جلب سعر الغاز")

            // بناء data لاستدعاء transfer(address,uint256)
            // selector: keccak256("transfer(address,uint256)")[0..3] = 0xa9059cbb
            val amountWei = BigInteger.valueOf((amountUsdt * 1e18).toLong())
            val paddedTo = toAddress.removePrefix("0x").padStart(64, '0')
            val paddedAmount = amountWei.toString(16).padStart(64, '0')
            val data = "0xa9059cbb$paddedTo$paddedAmount"

            val rawTx = buildAndSignTx(
                nonce = nonce,
                gasPrice = gasPrice,
                gasLimit = GAS_LIMIT_TOKEN,
                to = BscApiClient.USDT_CONTRACT_BEP20,
                value = BigInteger.ZERO,
                data = data,
                privateKeyHex = privateKeyHex
            )

            broadcast(rawTx)

        } catch (e: Exception) {
            Log.e(TAG, "خطأ في إرسال USDT: ${e.message}", e)
            Result.Error("خطأ: ${e.message}")
        }
    }

    // ─── إرسال BNB ────────────────────────────────────────

    suspend fun sendBnb(
        fromAddress: String,
        toAddress: String,
        amountBnb: Double,
        privateKeyHex: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val nonce = getNonce(fromAddress) ?: return@withContext Result.Error("فشل في جلب nonce")
            val gasPrice = getGasPrice() ?: return@withContext Result.Error("فشل في جلب سعر الغاز")

            val amountWei = BigInteger.valueOf((amountBnb * 1e18).toLong())

            val rawTx = buildAndSignTx(
                nonce = nonce,
                gasPrice = gasPrice,
                gasLimit = GAS_LIMIT_BNB,
                to = toAddress,
                value = amountWei,
                data = "",
                privateKeyHex = privateKeyHex
            )

            broadcast(rawTx)

        } catch (e: Exception) {
            Log.e(TAG, "خطأ في إرسال BNB: ${e.message}", e)
            Result.Error("خطأ: ${e.message}")
        }
    }

    // ─── Helpers داخلية ───────────────────────────────────

    private suspend fun getNonce(address: String): BigInteger? {
        return try {
            val response = api.getTransactionCount(
                JsonRpcRequest(
                    method = "eth_getTransactionCount",
                    params = listOf(address, "latest")
                )
            )
            response.result?.hexToBigInteger()
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في جلب nonce: ${e.message}")
            null
        }
    }

    private suspend fun getGasPrice(): BigInteger? {
        return try {
            val response = api.getGasPrice(
                JsonRpcRequest(method = "eth_gasPrice", params = emptyList())
            )
            response.result?.hexToBigInteger()
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في جلب gasPrice: ${e.message}")
            null
        }
    }

    private suspend fun broadcast(rawTx: String): Result<String> {
        val response = api.sendRawTransaction(
            JsonRpcRequest(
                method = "eth_sendRawTransaction",
                params = listOf(rawTx)
            )
        )
        return if (response.error != null) {
            Log.e(TAG, "فشل البث: ${response.error.message}")
            Result.Error("فشل الإرسال: ${response.error.message}")
        } else {
            val txHash = response.result ?: ""
            Log.i(TAG, "✅ تم الإرسال: $txHash")
            Result.Success(txHash)
        }
    }

    // ─── بناء المعاملة وتوقيعها (EIP-155) ────────────────

    private fun buildAndSignTx(
        nonce: BigInteger,
        gasPrice: BigInteger,
        gasLimit: Long,
        to: String,
        value: BigInteger,
        data: String,
        privateKeyHex: String
    ): String {
        val chainId = BscApiClient.CHAIN_ID

        // RLP encoding للتوقيع (يشمل chainId حسب EIP-155)
        val toBytes = hexToBytes(to.removePrefix("0x"))
        val dataBytes = if (data.isEmpty()) byteArrayOf() else hexToBytes(data.removePrefix("0x"))

        val rlpForSigning = encodeRlp(listOf(
            nonce,
            gasPrice,
            BigInteger.valueOf(gasLimit),
            toBytes,
            value,
            dataBytes,
            BigInteger.valueOf(chainId), // v = chainId
            BigInteger.ZERO,             // r = 0
            BigInteger.ZERO              // s = 0
        ))

        val txHash = keccak256(rlpForSigning)

        // التوقيع
        val privateKeyInt = BigInteger(privateKeyHex, 16)
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        signer.init(true, ECPrivateKeyParameters(privateKeyInt, domainParams))

        val sig = signer.generateSignature(txHash)
        var r = sig[0]
        var s = sig[1]

        // Canonical S (low-S)
        val halfOrder = domainParams.n.shiftRight(1)
        if (s > halfOrder) s = domainParams.n.subtract(s)

        val recId = findRecoveryId(txHash, r, s, privateKeyInt)

        // EIP-155: v = chainId * 2 + 35 + recId
        val v = BigInteger.valueOf(chainId * 2 + 35 + recId)

        // RLP النهائي مع التوقيع
        val signedRlp = encodeRlp(listOf(
            nonce,
            gasPrice,
            BigInteger.valueOf(gasLimit),
            toBytes,
            value,
            dataBytes,
            v,
            r,
            s
        ))

        return "0x" + bytesToHex(signedRlp)
    }

    // ─── RLP Encoding ──────────────────────────────────────

    /**
     * RLP encoding بسيط يدعم: BigInteger, ByteArray, List
     */
    private fun encodeRlp(items: List<Any>): ByteArray {
        val encodedItems = items.map { encodeRlpItem(it) }
        val payload = encodedItems.fold(byteArrayOf()) { acc, b -> acc + b }
        return encodeRlpLength(payload, 0xc0)
    }

    private fun encodeRlpItem(item: Any): ByteArray {
        return when (item) {
            is BigInteger -> {
                if (item == BigInteger.ZERO) byteArrayOf(0x80.toByte())
                else {
                    val bytes = item.toByteArray().let {
                        if (it[0] == 0.toByte()) it.drop(1).toByteArray() else it
                    }
                    encodeRlpLength(bytes, 0x80)
                }
            }
            is ByteArray -> {
                if (item.isEmpty()) byteArrayOf(0x80.toByte())
                else if (item.size == 1 && item[0].toInt() and 0xff < 0x80) item
                else encodeRlpLength(item, 0x80)
            }
            else -> throw IllegalArgumentException("نوع غير مدعوم في RLP: ${item::class}")
        }
    }

    private fun encodeRlpLength(data: ByteArray, offset: Int): ByteArray {
        return when {
            data.size == 1 && offset == 0x80 && data[0].toInt() and 0xff < 0x80 -> data
            data.size <= 55 -> byteArrayOf((offset + data.size).toByte()) + data
            else -> {
                val lenBytes = bigIntTo32Bytes(BigInteger.valueOf(data.size.toLong()))
                    .dropWhile { it == 0.toByte() }.toByteArray()
                byteArrayOf((offset + 55 + lenBytes.size).toByte()) + lenBytes + data
            }
        }
    }

    // ─── Crypto Helpers ────────────────────────────────────

    private fun keccak256(input: ByteArray): ByteArray {
        val digest = Keccak.Digest256()
        return digest.digest(input)
    }

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

    private fun recoverPublicKey(
        hash: ByteArray, r: BigInteger, s: BigInteger, recId: Int
    ): org.bouncycastle.math.ec.ECPoint? {
        val n = domainParams.n
        val curve = domainParams.curve
        val x = r.add(n.multiply(BigInteger.valueOf((recId / 2).toLong())))
        val prime = (curve as org.bouncycastle.math.ec.ECCurve.Fp).q
        if (x >= prime) return null

        val rPoint = decompressKey(x, recId and 1 == 1)
        if (!rPoint.multiply(n).isInfinity) return null

        val e = BigInteger(1, hash)
        val eInv = BigInteger.ZERO.subtract(e).mod(n)
        val rInv = r.modInverse(n)
        return domainParams.g
            .multiply(rInv.multiply(eInv).mod(n))
            .add(rPoint.multiply(rInv.multiply(s).mod(n)))
            .normalize()
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
            bytes.size > 32  -> bytes.drop(bytes.size - 32).toByteArray()
            else             -> ByteArray(32 - bytes.size) + bytes
        }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.length % 2 != 0) "0$hex" else hex
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
