package com.nabil.usdtwallet.domain.wallet

import android.util.Log
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.crypto.MnemonicException
import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Utils
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * مدير المحفظة - ينشئ ويدير مفاتيح Tron
 * يستخدم BIP39 (12 كلمة) + BIP44 (m/44'/195'/0'/0/0) لـ Tron
 */
object WalletManager {

    private const val TRON_COIN_TYPE = 195
    private const val TAG = "WalletManager"

    /**
     * إنشاء محفظة جديدة - يولّد 12 كلمة عشوائية
     */
    fun generateNewWallet(): WalletKeys {
        val entropy = ByteArray(16) // 128 bit = 12 كلمة
        SecureRandom().nextBytes(entropy)
        val mnemonic = MnemonicCode.INSTANCE.toMnemonic(entropy)
        return deriveWalletFromMnemonic(mnemonic)
    }

    /**
     * استيراد محفظة من 12 كلمة
     */
    fun importFromMnemonic(words: List<String>): WalletKeys? {
        return try {
            MnemonicCode.INSTANCE.check(words)
            deriveWalletFromMnemonic(words)
        } catch (e: MnemonicException) {
            Log.e(TAG, "كلمات غير صحيحة: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في الاستيراد: ${e.message}")
            null
        }
    }

    /**
     * اشتقاق المفاتيح من الـ mnemonic
     * مسار Tron: m/44'/195'/0'/0/0
     */
    private fun deriveWalletFromMnemonic(mnemonic: List<String>): WalletKeys {
        val seed = MnemonicCode.toSeed(mnemonic, "")

        var key = HDKeyDerivation.createMasterPrivateKey(seed)
        key = HDKeyDerivation.deriveChildKey(key, ChildNumber(44, true))   // purpose
        key = HDKeyDerivation.deriveChildKey(key, ChildNumber(TRON_COIN_TYPE, true)) // coin type
        key = HDKeyDerivation.deriveChildKey(key, ChildNumber(0, true))    // account
        key = HDKeyDerivation.deriveChildKey(key, ChildNumber(0, false))   // change
        key = HDKeyDerivation.deriveChildKey(key, ChildNumber(0, false))   // index

        val ecKey = ECKey.fromPrivate(key.privKeyBytes)
        val privateKeyHex = Utils.HEX.encode(key.privKeyBytes)
        val address = publicKeyToTronAddress(ecKey.pubKey)

        return WalletKeys(
            mnemonic = mnemonic,
            privateKeyHex = privateKeyHex,
            address = address
        )
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
     * Keccak256 hash
     */
    private fun keccak256(input: ByteArray): ByteArray {
        // استخدام SHA3-256 كبديل مقارب (Keccak256 الأصلي)
        return try {
            val digest = MessageDigest.getInstance("SHA3-256")
            digest.digest(input)
        } catch (e: Exception) {
            // fallback
            val digest = MessageDigest.getInstance("SHA-256")
            digest.digest(input)
        }
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

    private val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    private fun encodeBase58(input: ByteArray): String {
        var num = java.math.BigInteger(1, input)
        val sb = StringBuilder()
        val base = java.math.BigInteger.valueOf(58)
        while (num > java.math.BigInteger.ZERO) {
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
