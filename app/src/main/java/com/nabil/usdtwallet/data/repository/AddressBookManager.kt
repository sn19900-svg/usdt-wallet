package com.nabil.usdtwallet.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SavedAddress(
    val id: String = System.currentTimeMillis().toString(),
    val name: String,
    val address: String,
    val chain: String // "TRON" or "BSC"
)

object AddressBookManager {

    private const val PREF_NAME = "address_book"
    private const val KEY_ADDRESSES = "saved_addresses"
    private val gson = Gson()

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getAll(context: Context): List<SavedAddress> {
        val json = getPrefs(context).getString(KEY_ADDRESSES, null) ?: return emptyList()
        val type = object : TypeToken<List<SavedAddress>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }

    fun save(context: Context, address: SavedAddress) {
        val list = getAll(context).toMutableList()
        list.removeAll { it.id == address.id }
        list.add(0, address)
        getPrefs(context).edit()
            .putString(KEY_ADDRESSES, gson.toJson(list))
            .apply()
    }

    fun delete(context: Context, id: String) {
        val list = getAll(context).filter { it.id != id }
        getPrefs(context).edit()
            .putString(KEY_ADDRESSES, gson.toJson(list))
            .apply()
    }

    fun getForChain(context: Context, chain: String) =
        getAll(context).filter { it.chain == chain }
}
