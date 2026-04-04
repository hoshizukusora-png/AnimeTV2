package com.animatv.player.model

/**
 * Model untuk konfigurasi menu dropdown sidebar
 * Setiap menu punya id, label tampilan, dan list sub-kategori
 */
data class MenuConfig(
    val id: String,           // ID unik menu, misal "sport"
    val label: String,        // Label tampil, misal "SPORT"
    val icon: String = "CH",  // Icon singkat
    val subCategories: MutableList<String> = mutableListOf() // nama-nama kategori di bawah menu ini
)
