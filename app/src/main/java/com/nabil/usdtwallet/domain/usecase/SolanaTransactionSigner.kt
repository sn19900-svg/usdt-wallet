package com.nabil.usdtwallet.domain.usecase

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nabil.usdtwallet.data.repository.Result
import android.util.Base64
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * إرسال USDT SPL على Solana
 * يستخدم Solana JSON-RPC
 */
object SolanaTransactionSigner {

    private const val TAG = "SolanaSigner"
    const val USDT_MINT = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"

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
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
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

    // جلب رصيد SOL
    suspend fun getSolBalance(address: String): Double = withContext(Dispatchers.IO) {
        try {
            val r = rpcCall("getBalance", listOf(address))
            val lamports = r?.getJSONObject("result")?.getLong("value") ?: 0L
            lamports / 1_000_000_000.0
        } catch (e: Exception) { 0.0 }
    }

    // جلب رصيد USDT SPL
    suspend fun getUsdtBalance(address: String): Double = withContext(Dispatchers.IO) {
        try {
            val params = listOf(
                address,
                mapOf("mint" to USDT_MINT),
                mapOf("encoding" to "jsonParsed")
            )
            val r = rpcCall("getTokenAccountsByOwner", params)
            val accounts = r?.getJSONObject("result")?.getJSONArray("value") ?: return@withContext 0.0
            var total = 0.0
            for (i in 0 until accounts.length()) {
                val tokenAmount = accounts.getJSONObject(i)
                    .getJSONObject("account")
                    .getJSONObject("data")
                    .getJSONObject("parsed")
                    .getJSONObject("info")
                    .getJSONObject("tokenAmount")
                total += tokenAmount.optDouble("uiAmount", 0.0)
            }
            total
        } catch (e: Exception) {
            Log.e(TAG, "خطأ USDT SPL: ${e.message}")
            0.0
        }
    }

    /**
     * إرسال SOL
     * ملاحظة: إرسال كامل مع توقيع Ed25519
     */
    suspend fun sendSol(
        fromAddress: String,
        toAddress: String,
        amountSol: Double,
        privateKeyBase58: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. جلب recent blockhash
            val bhResult = rpcCall("getLatestBlockhash", listOf(mapOf("commitment" to "finalized")))
            val blockhash = bhResult?.getJSONObject("result")
                ?.getJSONObject("value")?.getString("blockhash")
                ?: return@withContext Result.Error("فشل جلب blockhash")

            // 2. بناء المعاملة
            val lamports = (amountSol * 1_000_000_000).toLong()
            val privKeyBytes = decodeBase58(privateKeyBase58).take(32).toByteArray()
            val privParams = Ed25519PrivateKeyParameters(privKeyBytes, 0)
            val pubKeyBytes = privParams.generatePublicKey().encoded

            val tx = buildSolTransfer(
                fromPubKey = pubKeyBytes,
                toPubKey   = decodeBase58(toAddress).toByteArray(),
                lamports   = lamports,
                blockhash  = decodeBase58(blockhash).toByteArray(),
                privKey    = privParams
            )

            // 3. إرسال
            val encoded = encodeBase64(tx)
            val sendResult = rpcCall("sendTransaction", listOf(encoded, mapOf("encoding" to "base64")))
            val txSig = sendResult?.optString("result") ?: ""
            if (txSig.isEmpty()) {
                val err = sendResult?.optJSONObject("error")?.optString("message") ?: "خطأ مجهول"
                return@withContext Result.Error("فشل الإرسال: $err")
            }
            Log.i(TAG, "✅ SOL إرسال: $txSig")
            Result.Success(txSig)
        } catch (e: Exception) {
            Log.e(TAG, "خطأ إرسال SOL: ${e.message}")
            Result.Error("خطأ: ${e.message}")
        }
    }

    /**
     * إرسال USDT SPL Token عبر SPL Token Program
     * يفترض أن المستقبل لديه بالفعل Associated Token Account لـ USDT
     * (شائع جداً لأن أغلب محافظ Solana تنشئه تلقائياً عند أول استلام)
     */
    suspend fun sendUsdtSpl(
        fromAddress: String,
        toAddress: String,
        amount: Double,
        privateKeyBase58: String
    ): Result<String> = withContext(Dispatchers.IO) {
        // ملاحظة أمان: إرسال SPL Token يتطلب اشتقاق PDA دقيق جداً (Associated Token Account)
        // أي خطأ في الاشتقاق يعني إرسال لعنوان خاطئ وفقدان الأموال نهائياً
        // لذلك حالياً نوقف هذه الميزة حتى تُختبر بمبالغ صغيرة على mainnet أولاً
        Result.Error(
            "إرسال USDT على Solana غير مفعّل حالياً لأسباب أمان (دقة اشتقاق العنوان). " +
            "استخدم SOL مباشرة، أو Tron/BSC/Ethereum لإرسال USDT."
        )
    }

    // ─── SPL Token Program: بناء معاملة تحويل ─────────────

    private val SPL_TOKEN_PROGRAM = decodeBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA").toByteArray()
    private val ASSOCIATED_TOKEN_PROGRAM = decodeBase58("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL").toByteArray()
    private val SYSTEM_PROGRAM_ID = ByteArray(32)

    private fun findAssociatedTokenAddress(owner: ByteArray, mint: ByteArray): ByteArray {
        // PDA derivation: sha256(owner + token_program + mint + associated_program) مع bump
        for (bump in 255 downTo 0) {
            try {
                val seeds = owner + SPL_TOKEN_PROGRAM + mint + byteArrayOf(bump.toByte())
                val hash = java.security.MessageDigest.getInstance("SHA-256").digest(seeds + ASSOCIATED_TOKEN_PROGRAM)
                // تحقق أنه off-curve (مبسط: نقبل أول نتيجة صالحة)
                return hash
            } catch (_: Exception) {}
        }
        return ByteArray(32)
    }

    private fun buildSplTransfer(
        fromAta: ByteArray, toAta: ByteArray, owner: ByteArray,
        amount: Long, blockhash: ByteArray, privKey: Ed25519PrivateKeyParameters
    ): ByteArray {
        // SPL Token Transfer instruction: [3][amount as u64 little-endian]
        val instructionData = ByteArray(9).apply {
            this[0] = 3 // Transfer instruction
            var v = amount
            for (i in 1..8) { this[i] = (v and 0xFF).toByte(); v = v shr 8 }
        }

        val buf = mutableListOf<Byte>()
        buf.add(1); buf.add(0); buf.add(1) // header: 1 signer, 0 ro-signed, 1 ro-unsigned
        buf.add(4) // account count: fromAta, toAta, owner, tokenProgram
        buf.addAll(fromAta.toList())
        buf.addAll(toAta.toList())
        buf.addAll(owner.toList())
        buf.addAll(SPL_TOKEN_PROGRAM.toList())
        buf.addAll(blockhash.toList())
        buf.add(1) // instruction count
        buf.add(3) // program index (tokenProgram = index 3)
        buf.add(3) // accounts used: 3
        buf.add(0); buf.add(1); buf.add(2) // fromAta, toAta, owner indices
        buf.add(instructionData.size.toByte())
        buf.addAll(instructionData.toList())
        val message = buf.toByteArray()

        val signer = org.bouncycastle.crypto.signers.Ed25519Signer()
        signer.init(true, privKey)
        signer.update(message, 0, message.size)
        val signature = signer.generateSignature()

        return byteArrayOf(1) + signature + message
    }

    private fun encodeBase58(bytes: ByteArray): String {
        var num = java.math.BigInteger(1, bytes)
        val sb = StringBuilder()
        val base = java.math.BigInteger.valueOf(58)
        while (num > java.math.BigInteger.ZERO) {
            val (q, r) = num.divideAndRemainder(base)
            sb.append(BASE58[r.toInt()])
            num = q
        }
        for (b in bytes) { if (b == 0.toByte()) sb.append(BASE58[0]) else break }
        return sb.reverse().toString()
    }

    // ─── بناء معاملة SOL بسيطة ───────────────────────────

    private fun buildSolTransfer(
        fromPubKey: ByteArray,
        toPubKey: ByteArray,
        lamports: Long,
        blockhash: ByteArray,
        privKey: Ed25519PrivateKeyParameters
    ): ByteArray {
        // System Program Transfer Instruction
        val SYSTEM_PROGRAM = ByteArray(32) // 11111...
        val instructionData = ByteArray(12).apply {
            // transfer instruction index = 2
            this[0] = 2; this[1] = 0; this[2] = 0; this[3] = 0
            // lamports (little-endian)
            var v = lamports
            for (i in 4..11) { this[i] = (v and 0xFF).toByte(); v = v shr 8 }
        }

        // Message
        val message = buildMessage(
            fromPubKey = fromPubKey,
            toPubKey   = toPubKey,
            systemProg = SYSTEM_PROGRAM,
            blockhash  = blockhash,
            data       = instructionData
        )

        // توقيع Ed25519
        val signer = org.bouncycastle.crypto.signers.Ed25519Signer()
        signer.init(true, privKey)
        signer.update(message, 0, message.size)
        val signature = signer.generateSignature()

        // التجميع: [1 sig count][sig 64 bytes][message]
        return byteArrayOf(1) + signature + message
    }

    private fun buildMessage(
        fromPubKey: ByteArray,
        toPubKey: ByteArray,
        systemProg: ByteArray,
        blockhash: ByteArray,
        data: ByteArray
    ): ByteArray {
        val buf = mutableListOf<Byte>()
        // Header: [1 signer, 0 readonly signed, 1 readonly unsigned]
        buf.add(1); buf.add(0); buf.add(1)
        // Account count = 3
        buf.add(3)
        buf.addAll(fromPubKey.toList())
        buf.addAll(toPubKey.toList())
        buf.addAll(systemProg.toList())
        // Recent blockhash
        buf.addAll(blockhash.toList())
        // Instruction count = 1
        buf.add(1)
        // Instruction: program index=2, accounts=[0,1], data
        buf.add(2)
        buf.add(2); buf.add(0); buf.add(1) // 2 accounts
        buf.add(data.size.toByte())
        buf.addAll(data.toList())
        return buf.toByteArray()
    }

    // ─── Base58 ──────────────────────────────────────────

    private val BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    private fun decodeBase58(input: String): List<Byte> {
        var num = java.math.BigInteger.ZERO
        val base = java.math.BigInteger.valueOf(58)
        for (c in input) {
            num = num.multiply(base).add(java.math.BigInteger.valueOf(BASE58.indexOf(c).toLong()))
        }
        val bytes = num.toByteArray().dropWhile { it == 0.toByte() }.toMutableList()
        val leadingZeros = input.takeWhile { it == '1' }.length
        repeat(leadingZeros) { bytes.add(0, 0) }
        return bytes
    }

    private fun encodeBase64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)
}
