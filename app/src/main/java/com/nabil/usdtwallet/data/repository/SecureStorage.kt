package com.nabil.usdtwallet.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * تخزين آمن ومشفر للمفاتيح الخاصة
 * يستخدم AES-256 عبر EncryptedSharedPreferences
 */
class SecureStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "wallet_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_MNEMONIC = "wallet_mnemonic"
        private const val KEY_ADDRESS = "wallet_address"
        private const val KEY_PRIVATE_KEY = "wallet_private_key"
        private const val KEY_WALLET_CREATED = "wallet_created"
    }

    fun saveWallet(mnemonic: List<String>, privateKey: String, address: String) {
        prefs.edit()
            .putString(KEY_MNEMONIC, mnemonic.joinToString(" "))
            .putString(KEY_PRIVATE_KEY, privateKey)
            .putString(KEY_ADDRESS, address)
            .putBoolean(KEY_WALLET_CREATED, true)
            .apply()
    }

    fun getMnemonic(): List<String>? {
        val raw = prefs.getString(KEY_MNEMONIC, null) ?: return null
        return raw.split(" ")
    }

    fun getAddress(): String? = prefs.getString(KEY_ADDRESS, null)

    fun getPrivateKey(): String? = prefs.getString(KEY_PRIVATE_KEY, null)

    fun isWalletCreated(): Boolean = prefs.getBoolean(KEY_WALLET_CREATED, false)

    fun deleteWallet() {
        prefs.edit()
            .remove(KEY_MNEMONIC)
            .remove(KEY_PRIVATE_KEY)
            .remove(KEY_ADDRESS)
            .putBoolean(KEY_WALLET_CREATED, false)
            .apply()
    }
}
