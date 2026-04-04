package com.animatv.player.extra

import android.content.Context
import android.content.SharedPreferences
import com.animatv.player.App
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Mengelola urutan dan visibilitas kategori di sidebar.
 * Urutan disimpan di SharedPreferences — bisa diubah dari Admin Panel.
 */
object CategoryOrderManager {

    private const val PREF_NAME = "animatv_category_order"
    private const val KEY_ORDER = "category_order"
    private const val KEY_HIDDEN = "category_hidden"

    private val prefs: SharedPreferences by lazy {
        App.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /** Simpan urutan kategori (list nama kategori) */
    fun saveOrder(orderedNames: List<String>) {
        prefs.edit().putString(KEY_ORDER, Gson().toJson(orderedNames)).apply()
    }

    /** Ambil urutan kategori yang tersimpan */
    fun getOrder(): List<String> {
        val json = prefs.getString(KEY_ORDER, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) { emptyList() }
    }

    /** Simpan daftar kategori yang disembunyikan */
    fun saveHidden(hiddenNames: Set<String>) {
        prefs.edit().putString(KEY_HIDDEN, Gson().toJson(hiddenNames.toList())).apply()
    }

    /** Ambil daftar kategori yang disembunyikan */
    fun getHidden(): Set<String> {
        val json = prefs.getString(KEY_HIDDEN, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val list: List<String> = Gson().fromJson(json, type)
            list.toSet()
        } catch (e: Exception) { emptySet() }
    }

    /**
     * Urutkan categories berdasarkan urutan yang tersimpan.
     * Kategori yang tidak ada di urutan tersimpan diletakkan di akhir.
     * Kategori yang disembunyikan tidak ditampilkan.
     */
    fun <T> applySavedOrder(
        categories: List<T>,
        getName: (T) -> String
    ): List<T> {
        val savedOrder = getOrder()
        val hidden = getHidden()

        // Filter kategori yang tidak disembunyikan
        val visible = categories.filter { cat ->
            !hidden.any { it.equals(getName(cat), ignoreCase = true) }
        }

        if (savedOrder.isEmpty()) return visible

        // Urutkan berdasarkan savedOrder
        val ordered = mutableListOf<T>()
        for (name in savedOrder) {
            val found = visible.firstOrNull {
                getName(it).equals(name, ignoreCase = true)
            }
            if (found != null) ordered.add(found)
        }
        // Tambah kategori yang belum ada di savedOrder di akhir
        for (cat in visible) {
            if (ordered.none { getName(it).equals(getName(cat), ignoreCase = true) }) {
                ordered.add(cat)
            }
        }
        return ordered
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
