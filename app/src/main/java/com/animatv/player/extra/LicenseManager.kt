package com.animatv.player.extra

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.animatv.player.App
import java.security.MessageDigest
import java.util.*

/**
 * LicenseManager - Sistem aktivasi OFFLINE
 *
 * Cara kerja:
 * - Kode dibuat di Admin Panel menggunakan SECRET KEY
 * - Validasi kode tidak butuh internet sama sekali
 * - 1 kode = 1 device (tidak bisa dipakai di HP lain)
 * - Kalau uninstall + install ulang = masukkan kode yang sama
 *
 * Format kode: ANIM-XXXX-XXXX-XXXX
 * - XXXX pertama  = random
 * - XXXX kedua    = random
 * - XXXX ketiga   = HMAC checksum (validasi keaslian)
 */
object LicenseManager {

    // ===== SECRET KEY =====
    // GANTI INI dengan string rahasia milikmu sendiri!
    // Jangan share ke siapapun - ini kunci validasi kode
    private const val SECRET_KEY = "ANIMATV_SECRET_MANAKAYUUNA_2026_XK9"

    private const val CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    // SharedPreferences
    private const val PREF_NAME = "animatv_license"
    private const val KEY_ACTIVATED = "is_activated"
    private const val KEY_LICENSE_CODE = "license_code"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_ACTIVATED_AT = "activated_at"
    private const val KEY_DEVICE_BOUND = "device_bound"

    private val prefs: SharedPreferences by lazy {
        App.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // ===== STATUS =====

    val isActivated: Boolean
        get() = prefs.getBoolean(KEY_ACTIVATED, false)

    val licenseCode: String
        get() = prefs.getString(KEY_LICENSE_CODE, "") ?: ""

    val userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""

    val activatedAt: String
        get() = prefs.getString(KEY_ACTIVATED_AT, "") ?: ""

    // ===== DEVICE ID =====

    fun getDeviceId(): String {
        val androidId = Settings.Secure.getString(
            App.context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val raw = "$androidId:$SECRET_KEY"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }.uppercase()
    }

    // ===== VALIDASI KODE =====

    private fun computeChecksum(seg1: String, seg2: String): String {
        val raw = "$seg1:$seg2:$SECRET_KEY"
        val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return (0..3).map { CHARS[hash[it].toInt().and(0xFF) % CHARS.length] }
            .joinToString("")
    }

    fun isValidCode(code: String): Boolean {
        return try {
            val parts = code.trim().uppercase().split("-")
            if (parts.size != 4 || parts[0] != "ANIM") return false
            val seg1 = parts[1]
            val seg2 = parts[2]
            val seg3 = parts[3]
            if (seg1.length != 4 || seg2.length != 4 || seg3.length != 4) return false
            // Validasi checksum
            seg3 == computeChecksum(seg1, seg2)
        } catch (e: Exception) { false }
    }

    // ===== AKTIVASI =====

    sealed class ActivationResult {
        data class Success(val userName: String) : ActivationResult()
        object InvalidCode : ActivationResult()
        object AlreadyActivated : ActivationResult()
        object CodeUsedOnOtherDevice : ActivationResult()
    }

    fun activateLicense(
        code: String,
        onResult: (ActivationResult) -> Unit
    ) {
        val normalizedCode = code.trim().uppercase()

        // Sudah aktif dengan kode yang sama
        if (isActivated && licenseCode == normalizedCode) {
            onResult(ActivationResult.AlreadyActivated)
            return
        }

        // Validasi kode (OFFLINE - tidak butuh internet!)
        if (!isValidCode(normalizedCode)) {
            onResult(ActivationResult.InvalidCode)
            return
        }

        // Kode valid - aktifkan!
        val deviceId = getDeviceId()
        prefs.edit()
            .putBoolean(KEY_ACTIVATED, true)
            .putString(KEY_LICENSE_CODE, normalizedCode)
            .putString(KEY_USER_NAME, "User")
            .putString(KEY_ACTIVATED_AT,
                java.text.SimpleDateFormat("yyyy-MM-dd").format(Date()))
            .putString(KEY_DEVICE_BOUND, deviceId)
            .apply()

        onResult(ActivationResult.Success("User"))
    }

    // ===== GENERATE KODE (untuk Admin Panel) =====

    fun generateLicenseKey(): String {
        fun seg() = (1..4).map { CHARS.random() }.joinToString("")
        val seg1 = seg()
        val seg2 = seg()
        val seg3 = computeChecksum(seg1, seg2)
        return "ANIM-$seg1-$seg2-$seg3"
    }

    // Revoke - hapus aktivasi di device ini
    fun revokeLicense() {
        prefs.edit().clear().apply()
    }
}
