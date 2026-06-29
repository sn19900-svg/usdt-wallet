package com.nabil.usdtwallet.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// ─── نموذج المحفظة ────────────────────────────────────────

data class WalletAccount(
    val id: String = System.currentTimeMillis().toString(),
    val name: String,                    // "محفظتي الأولى"
    val mnemonic: String,                // 12 كلمة مفصولة بمسافات
    val tronAddress: String,
    val tronPrivateKey: String,
    val bscAddress: String,
    val bscPrivateKey: String,
    val solanaAddress: String,
    val solanaPrivateKey: String,        // base58
    val ethereumAddress: String = "",
    val ethereumPrivateKey: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
)

// ─── MultiWalletStorage ───────────────────────────────────

object MultiWalletStorage {

    private const val PREF_NAME    = "multi_wallet_prefs"
    private const val KEY_WALLETS  = "wallets"
    private const val KEY_ACTIVE   = "active_wallet_id"
    private val gson = Gson()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ─── جلب جميع المحافظ ─────────────────────────────────
    fun getAll(context: Context): List<WalletAccount> {
        val json = prefs(context).getString(KEY_WALLETS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<WalletAccount>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    // ─── جلب المحفظة النشطة ──────────────────────────────
    fun getActive(context: Context): WalletAccount? {
        val activeId = prefs(context).getString(KEY_ACTIVE, null)
        val all = getAll(context)
        return if (activeId != null) all.find { it.id == activeId } else all.firstOrNull()
    }

    // ─── حفظ محفظة جديدة أو تحديث موجودة ─────────────────
    fun save(context: Context, wallet: WalletAccount) {
        val list = getAll(context).toMutableList()
        val idx = list.indexOfFirst { it.id == wallet.id }
        if (idx >= 0) list[idx] = wallet else list.add(wallet)
        prefs(context).edit().putString(KEY_WALLETS, gson.toJson(list)).apply()
    }

    // ─── تعيين المحفظة النشطة ────────────────────────────
    fun setActive(context: Context, id: String) {
        prefs(context).edit().putString(KEY_ACTIVE, id).apply()
    }

    // ─── حذف محفظة ────────────────────────────────────────
    fun delete(context: Context, id: String) {
        val list = getAll(context).filter { it.id != id }
        prefs(context).edit().putString(KEY_WALLETS, gson.toJson(list)).apply()
        // إذا كانت النشطة هي المحذوفة، نشط الأولى
        val activeId = prefs(context).getString(KEY_ACTIVE, null)
        if (activeId == id) {
            setActive(context, list.firstOrNull()?.id ?: "")
        }
    }

    // ─── تغيير اسم المحفظة ────────────────────────────────
    fun rename(context: Context, id: String, newName: String) {
        val wallet = getAll(context).find { it.id == id } ?: return
        save(context, wallet.copy(name = newName))
    }

    // ─── تحويل من SecureStorage القديم ────────────────────
    fun migrateFromLegacy(context: Context, secureStorage: SecureStorage): Boolean {
        if (!secureStorage.isWalletCreated()) return false
        if (getAll(context).isNotEmpty()) return false // سبق الترحيل

        val mnemonic = secureStorage.getMnemonic()?.joinToString(" ") ?: return false
        val wallet = WalletAccount(
            name           = "محفظتي الرئيسية",
            mnemonic       = mnemonic,
            tronAddress    = secureStorage.getAddress() ?: "",
            tronPrivateKey = secureStorage.getPrivateKey() ?: "",
            bscAddress     = secureStorage.getBscAddress() ?: "",
            bscPrivateKey  = secureStorage.getBscPrivateKey() ?: "",
            solanaAddress  = "",
            solanaPrivateKey = ""
        )
        save(context, wallet)
        setActive(context, wallet.id)
        return true
    }

    fun hasWallets(context: Context) = getAll(context).isNotEmpty()
}
