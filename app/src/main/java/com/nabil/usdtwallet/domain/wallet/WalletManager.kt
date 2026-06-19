package com.nabil.usdtwallet.domain.wallet

import android.content.Context
import android.util.Log
import org.bouncycastle.crypto.PBEParametersGenerator
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECParameterSpec
import org.bouncycastle.math.ec.ECPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * مدير المحفظة - ينشئ ويدير مفاتيح Tron
 * تطبيق يدوي كامل لـ BIP39 (بدون مكتبات خارجية ثقيلة أو معطّلة)
 * + اشتقاق حتمي لمسار Tron عبر HMAC-SHA512
 */
object WalletManager {

    private const val TAG = "WalletManager"
    private val curve: ECParameterSpec = ECNamedCurveTable.getParameterSpec("secp256k1")

    private var wordList: List<String>? = null

    /**
     * يجب استدعاء هذه مرة عند بدء التطبيق لتحميل قائمة الكلمات الإنجليزية الرسمية BIP39
     */
    fun init(context: Context) {
        if (wordList != null) return
        val words = mutableListOf<String>()
        context.assets.open("bip39_english.txt").use { stream ->
            BufferedReader(InputStreamReader(stream)).useLines { lines ->
                lines.forEach { if (it.isNotBlank()) words.add(it.trim()) }
            }
        }
        wordList = words
    }

    private fun words(): List<String> =
        wordList ?: throw IllegalStateException("WalletManager.init() لم يُستدعَ بعد")

    /**
     * إنشاء محفظة جديدة - يولّد 12 كلمة عشوائية وفق معيار BIP39
     */
    fun generateNewWallet(): WalletKeys {
        val entropy = ByteArray(16) // 128 bit = 12 كلمة
        SecureRandom().nextBytes(entropy)
        val mnemonicWords = entropyToMnemonic(entropy)
        return deriveWalletFromMnemonic(mnemonicWords)
    }

    /**
     * استيراد محفظة من 12 كلمة
     */
    fun importFromMnemonic(inputWords: List<String>): WalletKeys? {
        return try {
            if (!validateMnemonic(inputWords)) {
                Log.e(TAG, "كلمات غير صحيحة")
                return null
            }
            deriveWalletFromMnemonic(inputWords)
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في الاستيراد: ${e.message}")
            null
        }
    }

    // ─── BIP39: entropy → mnemonic ───────────────────────────

    private fun entropyToMnemonic(entropy: ByteArray): List<String> {
        val list = words()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(entropy)
        // checksum = أول (entropy.size*8 / 32) بت من sha256
        val checksumBits = entropy.size * 8 / 32

        val bits = StringBuilder()
        for (b in entropy) {
            bits.append(String.format("%8s", (b.toInt() and 0xFF).toString(2)).replace(' ', '0'))
        }
        val sha256Bits = StringBuilder()
        for (b in sha256) {
            sha256Bits.append(String.format("%8s", (b.toInt() and 0xFF).toString(2)).replace(' ', '0'))
        }
        bits.append(sha256Bits.substring(0, checksumBits))

        val mnemonicWords = mutableListOf<String>()
        var i = 0
        while (i < bits.length) {
            val index = bits.substring(i, i + 11).toInt(2)
            mnemonicWords.add(list[index])
            i += 11
        }
        return mnemonicWords
    }

    /**
     * التحقق من صحة الكلمات (checksum BIP39)
     */
    private fun validateMnemonic(inputWords: List<String>): Boolean {
        if (inputWords.size != 12) return false
        val list = words()
        val indices = inputWords.map { w ->
            val idx = list.indexOf(w.trim().lowercase())
            if (idx < 0) return false
            idx
        }

        val bits = StringBuilder()
        for (idx in indices) {
            bits.append(String.format("%11s", idx.toString(2)).replace(' ', '0'))
        }

        val entropyBits = bits.substring(0, 128)
        val checksumBits = bits.substring(128)

        val entropyBytes = ByteArray(16)
        for (i in entropyBytes.indices) {
            entropyBytes[i] = entropyBits.substring(i * 8, i * 8 + 8).toInt(2).toByte()
        }

        val sha256 = MessageDigest.getInstance("SHA-256").digest(entropyBytes)
        val sha256Bits = StringBuilder()
        for (b in sha256) {
            sha256Bits.append(String.format("%8s", (b.toInt() and 0xFF).toString(2)).replace(' ', '0'))
        }
        val expectedChecksum = sha256Bits.substring(0, 4)

        return checksumBits == expectedChecksum
    }

    // ─── BIP39: mnemonic → seed (PBKDF2-HMAC-SHA512) ─────────

    private fun mnemonicToSeed(mnemonicWords: List<String>, passphrase: String = ""): ByteArray {
        val mnemonic = mnemonicWords.joinToString(" ")
        val salt = "mnemonic$passphrase"

        val generator = PKCS5S2ParametersGenerator(SHA512Digest())
        generator.init(
            PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(mnemonic.toCharArray()),
            salt.toByteArray(Charsets.UTF_8),
            2048
        )
        val key = generator.generateDerivedParameters(512) as KeyParameter
        return key.key
    }

    // ─── اشتقاق المفاتيح من الـ seed ──────────────────────────

    private fun deriveWalletFromMnemonic(mnemonicWords: List<String>): WalletKeys {
        val seed = mnemonicToSeed(mnemonicWords)

        val privateKeyBytes = hmacSha512(seed, "tron-account-0".toByteArray()).copyOfRange(0, 32)

        val privKeyInt = BigInteger(1, privateKeyBytes).mod(curve.n.subtract(BigInteger.ONE)).add(BigInteger.ONE)
        val privateKeyHex = privKeyInt.toString(16).padStart(64, '0')

        val publicPoint: ECPoint = curve.g.multiply(privKeyInt).normalize()
        val pubKeyBytes = publicPoint.getEncoded(false) // uncompressed, 65 bytes

        val address = publicKeyToTronAddress(pubKeyBytes)

        return WalletKeys(
            mnemonic = mnemonicWords,
            privateKeyHex = privateKeyHex,
            address = address
        )
    }

    private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA512")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA512"))
        return mac.doFinal(data)
    }

    /**
     * تحويل المفتاح العام لعنوان Tron (Base58Check)
     */
    private fun publicKeyToTronAddress(publicKey: ByteArray): String {
        val pubKeyNoPrefix = if (publicKey.size == 65) publicKey.drop(1).toByteArray() else publicKey
        val keccak = keccak256(pubKeyNoPrefix)

        val addressBytes = ByteArray(21)
        addressBytes[0] = 0x41.toByte()
        System.arraycopy(keccak, 12, addressBytes, 1, 20)

        return base58CheckEncode(addressBytes)
    }

    fun keccak256(input: ByteArray): ByteArray {
        val digest = org.bouncycastle.jcajce.provider.digest.Keccak.Digest256()
        return digest.digest(input)
    }

    private fun base58CheckEncode(input: ByteArray): String {
        val checksum = doublesha256(input).take(4).toByteArray()
        val payload = input + checksum
        return encodeBase58(payload)
    }

    private fun doublesha256(input: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(md.digest(input))
    }

    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    private fun encodeBase58(input: ByteArray): String {
        var num = BigInteger(1, input)
        val sb = StringBuilder()
        val base = BigInteger.valueOf(58)
        while (num > BigInteger.ZERO) {
            val (quotient, remainder) = num.divideAndRemainder(base)
            sb.append(BASE58_ALPHABET[remainder.toInt()])
            num = quotient
        }
        for (byte in input) {
            if (byte == 0.toByte()) sb.append(BASE58_ALPHABET[0]) else break
        }
        return sb.reverse().toString()
    }

    fun isValidTronAddress(address: String): Boolean {
        return address.startsWith("T") && address.length == 34
    }
}

data class WalletKeys(
    val mnemonic: List<String>,
    val privateKeyHex: String,
    val address: String
)
