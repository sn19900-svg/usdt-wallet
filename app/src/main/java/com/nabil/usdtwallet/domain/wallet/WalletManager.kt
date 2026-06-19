package com.nabil.usdtwallet.domain.wallet

import android.util.Log
import io.github.novacrypto.bip39.MnemonicGenerator
import io.github.novacrypto.bip39.SeedCalculator
import io.github.novacrypto.bip39.wordlists.English
import io.github.novacrypto.bip39.MnemonicValidator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECParameterSpec
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * مدير المحفظة - ينشئ ويدير مفاتيح Tron
 * يستخدم BIP39 (12 كلمة) + اشتقاق مبسّط حتمي لمسار Tron عبر HKDF
 * مكتبات خفيفة بدل bitcoinj الثقيلة لتقليل حجم classes.dex
 */
object WalletManager {

    private const val TAG = "WalletManager"
    private val curve: ECParameterSpec = ECNamedCurveTable.getParameterSpec("secp256k1")

    /**
     * إنشاء محفظة جديدة - يولّد 12 كلمة عشوائية
     */
    fun generateNewWallet(): WalletKeys {
        val entropy = ByteArray(16) // 128 bit = 12 كلمة
        SecureRandom().nextBytes(entropy)

        val sb = StringBuilder()
        MnemonicGenerator(English.INSTANCE).createMnemonic(entropy) { sb.append(it) }
        val mnemonicWords = sb.toString().trim().split(" ")

        return deriveWalletFromMnemonic(mnemonicWords)
    }

    /**
     * استيراد محفظة من 12 كلمة
     */
    fun importFromMnemonic(words: List<String>): WalletKeys? {
        return try {
            val phrase = words.joinToString(" ")
            MnemonicValidator.ofWordList(English.INSTANCE).validate(phrase)
            deriveWalletFromMnemonic(words)
        } catch (e: Exception) {
            Log.e(TAG, "كلمات غير صحيحة: ${e.message}")
            null
        }
    }

    /**
     * اشتقاق المفاتيح من الـ mnemonic
     * نستخدم seed BIP39 القياسي، ثم اشتقاق حتمي مخصص لمسار Tron
     * عبر HKDF(masterSeed) لإنتاج private key ثابت وقابل لإعادة الاشتقاق دائماً
     */
    private fun deriveWalletFromMnemonic(mnemonic: List<String>): WalletKeys {
        val phrase = mnemonic.joinToString(" ")
        val seed = SeedCalculator()
            .withWordsFromWordList(English.INSTANCE)
            .calculateSeed(phrase, "")

        // اشتقاق مفتاح خاص حتمي بطول 32 بايت عبر HKDF (يعتمد فقط على الـ seed)
        val privateKeyBytes = hkdfDerive(seed, "tron-account-0".toByteArray(), 32)

        // نضمن أن المفتاح ضمن نطاق منحنى secp256k1 الصحيح
        val privKeyInt = BigInteger(1, privateKeyBytes).mod(curve.n.subtract(BigInteger.ONE)).add(BigInteger.ONE)
        val privateKeyHex = privKeyInt.toString(16).padStart(64, '0')

        // اشتقاق المفتاح العام من المفتاح الخاص عبر ضرب نقطة المنحنى
        val publicPoint: ECPoint = curve.g.multiply(privKeyInt).normalize()
        val pubKeyBytes = publicPoint.getEncoded(false) // uncompressed, 65 bytes (0x04 + X + Y)

        val address = publicKeyToTronAddress(pubKeyBytes)

        return WalletKeys(
            mnemonic = mnemonic,
            privateKeyHex = privateKeyHex,
            address = address
        )
    }

    /**
     * HKDF بسيط لاشتقاق مفتاح ثابت من seed
     */
    private fun hkdfDerive(seed: ByteArray, info: ByteArray, length: Int): ByteArray {
        val hkdf = HKDFBytesGenerator(org.bouncycastle.crypto.digests.SHA512Digest())
        hkdf.init(HKDFParameters(seed, null, info))
        val output = ByteArray(length)
        hkdf.generateBytes(output, 0, length)
        return output
    }

    /**
     * تحويل المفتاح العام لعنوان Tron (Base58Check)
     */
    private fun publicKeyToTronAddress(publicKey: ByteArray): String {
        // Keccak256 على المفتاح العام (بدون البادئة 04)
        val pubKeyNoPrefix = if (publicKey.size == 65) publicKey.drop(1).toByteArray() else publicKey
        val keccak = keccak256(pubKeyNoPrefix)

        // آخر 20 بايت + بادئة Tron (0x41)
        val addressBytes = ByteArray(21)
        addressBytes[0] = 0x41.toByte()
        System.arraycopy(keccak, 12, addressBytes, 1, 20)

        return base58CheckEncode(addressBytes)
    }

    /**
     * Keccak256 hash (عبر BouncyCastle)
     */
    fun keccak256(input: ByteArray): ByteArray {
        val digest = org.bouncycastle.jcajce.provider.digest.Keccak.Digest256()
        return digest.digest(input)
    }

    /**
     * Base58Check encoding
     */
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

    /**
     * التحقق من صحة عنوان Tron
     */
    fun isValidTronAddress(address: String): Boolean {
        return address.startsWith("T") && address.length == 34
    }
}

data class WalletKeys(
    val mnemonic: List<String>,
    val privateKeyHex: String,
    val address: String
)
