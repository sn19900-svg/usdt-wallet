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
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * WalletManager - BIP39 + BIP44 صحيح متوافق مع Trust Wallet
 *
 * Derivation Paths:
 * Tron:     m/44'/195'/0'/0/0
 * Ethereum/BSC: m/44'/60'/0'/0/0
 * Solana:   m/44'/501'/0'/0'
 */
object WalletManager {

    private const val TAG = "WalletManager"
    private val curve: ECParameterSpec = ECNamedCurveTable.getParameterSpec("secp256k1")
    private var wordList: List<String>? = null

    fun init(context: Context) {
        if (wordList != null) return
        val words = mutableListOf<String>()
        context.assets.open("bip39_english.txt").use { stream ->
            BufferedReader(InputStreamReader(stream)).useLines { lines ->
                lines.forEach { if (it.isNotBlank()) words.add(it.trim()) }
            }
        }
        wordList = words
        Log.i(TAG, "✅ BIP39 wordlist loaded: ${words.size} words")
    }

    private fun words(): List<String> =
        wordList ?: throw IllegalStateException("WalletManager.init() لم يُستدعَ بعد")

    // ─── إنشاء محفظة جديدة ────────────────────────────────

    fun generateNewWallet(): WalletKeys {
        val entropy = ByteArray(16)
        SecureRandom().nextBytes(entropy)
        val mnemonicWords = entropyToMnemonic(entropy)
        return deriveWalletFromMnemonic(mnemonicWords)
    }

    // ─── استيراد من 12 كلمة ───────────────────────────────

    fun importFromMnemonic(inputWords: List<String>): WalletKeys? {
        return try {
            if (!validateMnemonic(inputWords)) {
                Log.e(TAG, "❌ كلمات غير صحيحة")
                return null
            }
            deriveWalletFromMnemonic(inputWords)
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في الاستيراد: ${e.message}")
            null
        }
    }

    // ─── BIP39: entropy → mnemonic ────────────────────────

    private fun entropyToMnemonic(entropy: ByteArray): List<String> {
        val list = words()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(entropy)
        val checksumBits = entropy.size * 8 / 32
        val bits = StringBuilder()
        for (b in entropy) bits.append(String.format("%8s", (b.toInt() and 0xFF).toString(2)).replace(' ', '0'))
        val sha256Bits = StringBuilder()
        for (b in sha256) sha256Bits.append(String.format("%8s", (b.toInt() and 0xFF).toString(2)).replace(' ', '0'))
        bits.append(sha256Bits.substring(0, checksumBits))
        val result = mutableListOf<String>()
        var i = 0
        while (i < bits.length) { result.add(list[bits.substring(i, i + 11).toInt(2)]); i += 11 }
        return result
    }

    private fun validateMnemonic(inputWords: List<String>): Boolean {
        if (inputWords.size != 12) return false
        val list = words()
        val indices = inputWords.map { w ->
            val idx = list.indexOf(w.trim().lowercase())
            if (idx < 0) return false
            idx
        }
        val bits = StringBuilder()
        for (idx in indices) bits.append(String.format("%11s", idx.toString(2)).replace(' ', '0'))
        val entropyBits = bits.substring(0, 128)
        val checksumBits = bits.substring(128)
        val entropyBytes = ByteArray(16)
        for (i in entropyBytes.indices)
            entropyBytes[i] = entropyBits.substring(i * 8, i * 8 + 8).toInt(2).toByte()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(entropyBytes)
        val sha256Bits = StringBuilder()
        for (b in sha256) sha256Bits.append(String.format("%8s", (b.toInt() and 0xFF).toString(2)).replace(' ', '0'))
        return checksumBits == sha256Bits.substring(0, 4)
    }

    // ─── BIP39: mnemonic → seed ───────────────────────────

    private fun mnemonicToSeed(mnemonicWords: List<String>, passphrase: String = ""): ByteArray {
        val mnemonic = mnemonicWords.joinToString(" ")
        val salt = "mnemonic$passphrase"
        val generator = PKCS5S2ParametersGenerator(SHA512Digest())
        generator.init(
            PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(mnemonic.toCharArray()),
            salt.toByteArray(Charsets.UTF_8),
            2048
        )
        return (generator.generateDerivedParameters(512) as KeyParameter).key
    }

    // ─── BIP32 HMAC-SHA512 ────────────────────────────────

    private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(key, "HmacSHA512"))
        return mac.doFinal(data)
    }

    // ─── BIP32: Master Key من الـ seed ───────────────────

    private data class ExtendedKey(val key: ByteArray, val chainCode: ByteArray)

    private fun masterKey(seed: ByteArray): ExtendedKey {
        val I = hmacSha512("Bitcoin seed".toByteArray(), seed)
        return ExtendedKey(I.copyOfRange(0, 32), I.copyOfRange(32, 64))
    }

    // ─── BIP32: Child Key Derivation ─────────────────────

    private fun deriveChild(parent: ExtendedKey, index: Long): ExtendedKey {
        val hardened = index >= 0x80000000L
        val data = if (hardened) {
            byteArrayOf(0x00) + parent.key + indexToBytes(index)
        } else {
            val pubKey = privateKeyToPublicKeyCompressed(BigInteger(1, parent.key))
            pubKey + indexToBytes(index)
        }
        val I = hmacSha512(parent.chainCode, data)
        val IL = I.copyOfRange(0, 32)
        val IR = I.copyOfRange(32, 64)

        val curveN = curve.n
        val childKey = (BigInteger(1, IL).add(BigInteger(1, parent.key))).mod(curveN)
        return ExtendedKey(bigIntTo32Bytes(childKey), IR)
    }

    private fun deriveChildFromPath(seed: ByteArray, path: List<Long>): ExtendedKey {
        var current = masterKey(seed)
        for (index in path) current = deriveChild(current, index)
        return current
    }

    // ─── الاشتقاق الحقيقي BIP44 متوافق مع Trust Wallet ──

    private fun deriveWalletFromMnemonic(mnemonicWords: List<String>): WalletKeys {
        val seed = mnemonicToSeed(mnemonicWords)

        // Tron: m/44'/195'/0'/0/0
        val tronPath = listOf(
            0x80000000L + 44,   // 44'
            0x80000000L + 195,  // 195' (Tron coin type)
            0x80000000L + 0,    // 0'
            0L,                  // 0
            0L                   // 0
        )
        val tronKey = deriveChildFromPath(seed, tronPath)
        val tronPrivHex = bigIntTo32Bytes(BigInteger(1, tronKey.key)).toHex()
        val tronAddress = privateKeyToTronAddress(tronPrivHex)

        Log.i(TAG, "✅ Tron BIP44 m/44'/195'/0'/0/0")
        Log.i(TAG, "   Address: $tronAddress")

        return WalletKeys(
            mnemonic      = mnemonicWords,
            privateKeyHex = tronPrivHex,
            address       = tronAddress
        )
    }

    // ─── BSC/ETH address من نفس الـ seed (m/44'/60'/0'/0/0) ─

    fun deriveBscAddress(mnemonicWords: List<String>): String {
        val seed = mnemonicToSeed(mnemonicWords)
        // BSC/ETH: m/44'/60'/0'/0/0
        val ethPath = listOf(
            0x80000000L + 44,
            0x80000000L + 60,  // 60' (Ethereum coin type = BSC أيضاً)
            0x80000000L + 0,
            0L,
            0L
        )
        val ethKey = deriveChildFromPath(seed, ethPath)
        val privKeyInt = BigInteger(1, ethKey.key)
        val pubKeyPoint = curve.g.multiply(privKeyInt).normalize()
        val xBytes = bigIntTo32Bytes(pubKeyPoint.xCoord.toBigInteger())
        val yBytes = bigIntTo32Bytes(pubKeyPoint.yCoord.toBigInteger())
        val pubKeyBytes = xBytes + yBytes
        val hash = keccak256(pubKeyBytes)
        return "0x" + hash.takeLast(20).toByteArray().toHex()
    }

    // ─── BSC private key ──────────────────────────────────

    fun deriveBscPrivateKey(mnemonicWords: List<String>): String {
        val seed = mnemonicToSeed(mnemonicWords)
        val ethPath = listOf(
            0x80000000L + 44,
            0x80000000L + 60,
            0x80000000L + 0,
            0L,
            0L
        )
        val ethKey = deriveChildFromPath(seed, ethPath)
        return bigIntTo32Bytes(BigInteger(1, ethKey.key)).toHex()
    }

    // ─── Tron Address من Private Key ─────────────────────

    private fun privateKeyToTronAddress(privateKeyHex: String): String {
        val privKeyInt = BigInteger(privateKeyHex, 16)
        val pubKeyPoint = curve.g.multiply(privKeyInt).normalize()
        val pubKeyBytes = pubKeyPoint.getEncoded(false) // uncompressed 65 bytes
        return publicKeyToTronAddress(pubKeyBytes)
    }

    private fun publicKeyToTronAddress(publicKey: ByteArray): String {
        val pubKeyNoPrefix = if (publicKey.size == 65) publicKey.drop(1).toByteArray() else publicKey
        val keccak = keccak256(pubKeyNoPrefix)
        val addressBytes = ByteArray(21)
        addressBytes[0] = 0x41.toByte()
        System.arraycopy(keccak, 12, addressBytes, 1, 20)
        return base58CheckEncode(addressBytes)
    }

    private fun privateKeyToPublicKeyCompressed(privKey: BigInteger): ByteArray {
        val point = curve.g.multiply(privKey).normalize()
        return point.getEncoded(true) // compressed 33 bytes
    }

    fun keccak256(input: ByteArray): ByteArray {
        val digest = org.bouncycastle.jcajce.provider.digest.Keccak.Digest256()
        return digest.digest(input)
    }

    private fun base58CheckEncode(input: ByteArray): String {
        val checksum = doublesha256(input).take(4).toByteArray()
        return encodeBase58(input + checksum)
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
            val (q, r) = num.divideAndRemainder(base)
            sb.append(BASE58_ALPHABET[r.toInt()])
            num = q
        }
        for (byte in input) { if (byte == 0.toByte()) sb.append(BASE58_ALPHABET[0]) else break }
        return sb.reverse().toString()
    }

    fun isValidTronAddress(address: String) = address.startsWith("T") && address.length == 34

    // ─── Helpers ──────────────────────────────────────────

    private fun indexToBytes(index: Long): ByteArray = byteArrayOf(
        ((index shr 24) and 0xFF).toByte(),
        ((index shr 16) and 0xFF).toByte(),
        ((index shr 8)  and 0xFF).toByte(),
        (index and 0xFF).toByte()
    )

    private fun bigIntTo32Bytes(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return when {
            bytes.size == 32 -> bytes
            bytes.size > 32  -> bytes.drop(bytes.size - 32).toByteArray()
            else             -> ByteArray(32 - bytes.size) + bytes
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}

data class WalletKeys(
    val mnemonic: List<String>,
    val privateKeyHex: String,
    val address: String
)
