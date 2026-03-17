package com.animatv.player.extra

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.animatv.player.App
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import android.util.Base64

/**
 * LicenseManager - sistem aktivasi per device
 *
 * Flow:
 * 1. User input kode
 * 2. App ambil license_keys.json dari GitHub
 * 3. Cek kode valid? Device ID cocok?
 * 4. Kalau OK → simpan lokal → app aktif
 * 5. Kalau kode dipakai device lain → tolak
 */
object LicenseManager {

    // GitHub config
    private const val GITHUB_OWNER = "manakayuuna123-dot"
    private const val GITHUB_REPO = "AnimeTV"
    private const val LICENSE_FILE = "config/license_keys.json"
    private const val LICENSE_URL =
        "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/$LICENSE_FILE"
    private const val GITHUB_API_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/contents/$LICENSE_FILE"

    // SharedPreferences
    private const val PREF_NAME = "animatv_license"
    private const val KEY_ACTIVATED = "is_activated"
    private const val KEY_LICENSE_CODE = "license_code"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_ACTIVATED_AT = "activated_at"

    private val prefs: SharedPreferences by lazy {
        App.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // ===== STATUS AKTIVASI =====

    val isActivated: Boolean
        get() = prefs.getBoolean(KEY_ACTIVATED, false)

    val licenseCode: String
        get() = prefs.getString(KEY_LICENSE_CODE, "") ?: ""

    val userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""

    val activatedAt: String
        get() = prefs.getString(KEY_ACTIVATED_AT, "") ?: ""

    // ===== DEVICE ID =====
    // Device ID unik per HP - tidak berubah walau reinstall app

    fun getDeviceId(): String {
        val androidId = Settings.Secure.getString(
            App.context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        // Hash untuk keamanan + tambah app salt
        val salt = "animatv_${GITHUB_OWNER}"
        val raw = "$androidId:$salt"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }.uppercase()
    }

    // ===== AKTIVASI =====

    sealed class ActivationResult {
        data class Success(val userName: String) : ActivationResult()
        data class AlreadyUsed(val deviceHint: String) : ActivationResult()
        object InvalidCode : ActivationResult()
        object NetworkError : ActivationResult()
        object AlreadyActivated : ActivationResult()
    }

    fun activateLicense(
        code: String,
        onResult: (ActivationResult) -> Unit
    ) {
        // Kalau sudah aktif dengan kode yang sama, langsung OK
        if (isActivated && licenseCode == code.trim().uppercase()) {
            onResult(ActivationResult.AlreadyActivated)
            return
        }

        Thread {
            try {
                // 1. Ambil database lisensi dari GitHub
                val json = fetchLicenseDb() ?: run {
                    App.runOnUiThread { onResult(ActivationResult.NetworkError) }
                    return@Thread
                }

                val db = Gson().fromJson(json, JsonObject::class.java)
                val keys = db.getAsJsonObject("keys") ?: JsonObject()
                val normalizedCode = code.trim().uppercase()

                // 2. Cek kode ada tidak
                if (!keys.has(normalizedCode)) {
                    App.runOnUiThread { onResult(ActivationResult.InvalidCode) }
                    return@Thread
                }

                val keyData = keys.getAsJsonObject(normalizedCode)
                val status = keyData.get("status")?.asString ?: "available"
                val existingDeviceId = keyData.get("deviceId")?.asString ?: ""
                val myDeviceId = getDeviceId()

                when {
                    // Kode sudah dipakai device LAIN
                    status == "activated" && existingDeviceId != myDeviceId -> {
                        val hint = existingDeviceId.take(4) + "****"
                        App.runOnUiThread {
                            onResult(ActivationResult.AlreadyUsed(hint))
                        }
                    }

                    // Kode tersedia ATAU device yang sama (reinstall)
                    status == "available" || existingDeviceId == myDeviceId -> {
                        val uName = keyData.get("userName")?.asString ?: "User"
                        val adminToken = getAdminToken()

                        // Update database di GitHub
                        val updated = updateLicenseDb(
                            db, normalizedCode, myDeviceId, adminToken
                        )

                        if (updated) {
                            // Simpan aktivasi lokal
                            prefs.edit()
                                .putBoolean(KEY_ACTIVATED, true)
                                .putString(KEY_LICENSE_CODE, normalizedCode)
                                .putString(KEY_USER_NAME, uName)
                                .putString(KEY_ACTIVATED_AT,
                                    java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date()))
                                .apply()

                            App.runOnUiThread { onResult(ActivationResult.Success(uName)) }
                        } else {
                            App.runOnUiThread { onResult(ActivationResult.NetworkError) }
                        }
                    }

                    else -> App.runOnUiThread { onResult(ActivationResult.InvalidCode) }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                App.runOnUiThread { onResult(ActivationResult.NetworkError) }
            }
        }.start()
    }

    // ===== ADMIN: GENERATE KODE =====

    data class GenerateResult(
        val success: Boolean,
        val code: String = "",
        val error: String = ""
    )

    fun generateLicenseKey(
        userName: String,
        adminToken: String,
        onResult: (GenerateResult) -> Unit
    ) {
        Thread {
            try {
                // Generate kode unik: ANIM-XXXX-XXXX-XXXX
                val code = generateUniqueCode()

                // Ambil DB saat ini
                val json = fetchLicenseDb() ?: run {
                    App.runOnUiThread {
                        onResult(GenerateResult(false, error = "Gagal ambil database"))
                    }
                    return@Thread
                }

                val db = Gson().fromJson(json, JsonObject::class.java)
                val keys = db.getAsJsonObject("keys") ?: JsonObject()

                // Tambah kode baru
                val newKey = JsonObject().apply {
                    addProperty("status", "available")
                    addProperty("deviceId", "")
                    addProperty("userName", userName)
                    addProperty("createdAt",
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date()))
                    addProperty("activatedAt", "")
                }
                keys.add(code, newKey)
                db.add("keys", keys)

                // Simpan ke GitHub
                val dbJson = Gson().toJson(db)
                val saved = pushToGitHub(dbJson, adminToken,
                    "Add license key: $code for $userName")

                App.runOnUiThread {
                    if (saved) onResult(GenerateResult(true, code))
                    else onResult(GenerateResult(false, error = "Gagal simpan ke GitHub"))
                }

            } catch (e: Exception) {
                App.runOnUiThread {
                    onResult(GenerateResult(false, error = e.message ?: "Error"))
                }
            }
        }.start()
    }

    fun getAllLicenses(
        adminToken: String,
        onResult: (JsonObject?) -> Unit
    ) {
        Thread {
            try {
                val json = fetchLicenseDb() ?: run {
                    App.runOnUiThread { onResult(null) }
                    return@Thread
                }
                val db = Gson().fromJson(json, JsonObject::class.java)
                App.runOnUiThread { onResult(db.getAsJsonObject("keys")) }
            } catch (e: Exception) {
                App.runOnUiThread { onResult(null) }
            }
        }.start()
    }

    fun revokeLicense(
        code: String,
        adminToken: String,
        onResult: (Boolean) -> Unit
    ) {
        Thread {
            try {
                val json = fetchLicenseDb() ?: run {
                    App.runOnUiThread { onResult(false) }
                    return@Thread
                }
                val db = Gson().fromJson(json, JsonObject::class.java)
                val keys = db.getAsJsonObject("keys") ?: run {
                    App.runOnUiThread { onResult(false) }
                    return@Thread
                }

                if (keys.has(code)) {
                    val keyData = keys.getAsJsonObject(code)
                    keyData.addProperty("status", "revoked")
                    keyData.addProperty("deviceId", "")
                    keys.add(code, keyData)
                    db.add("keys", keys)

                    val saved = pushToGitHub(
                        Gson().toJson(db), adminToken, "Revoke license: $code"
                    )
                    App.runOnUiThread { onResult(saved) }
                } else {
                    App.runOnUiThread { onResult(false) }
                }
            } catch (e: Exception) {
                App.runOnUiThread { onResult(false) }
            }
        }.start()
    }

    // ===== PRIVATE HELPERS =====

    private fun fetchLicenseDb(): String? {
        return try {
            val request = Request.Builder().url(LICENSE_URL).build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) response.body()?.string() else null
        } catch (e: Exception) { null }
    }

    private fun getFileSha(token: String): String? {
        return try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Authorization", "token $token")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = Gson().fromJson(response.body()?.string(), JsonObject::class.java)
                json.get("sha")?.asString
            } else null
        } catch (e: Exception) { null }
    }

    private fun pushToGitHub(content: String, token: String, message: String): Boolean {
        return try {
            val sha = getFileSha(token) ?: return false
            val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
            val body = """{"message":"$message","content":"$encoded","sha":"$sha"}"""
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Authorization", "token $token")
                .header("Accept", "application/vnd.github.v3+json")
                .put(RequestBody.create(MediaType.parse("application/json"), body))
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) { false }
    }

    private fun updateLicenseDb(
        db: JsonObject,
        code: String,
        deviceId: String,
        token: String
    ): Boolean {
        val keys = db.getAsJsonObject("keys") ?: return false
        val keyData = keys.getAsJsonObject(code) ?: return false
        keyData.addProperty("status", "activated")
        keyData.addProperty("deviceId", deviceId)
        keyData.addProperty("activatedAt",
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date()))
        keys.add(code, keyData)
        db.add("keys", keys)
        return pushToGitHub(Gson().toJson(db), token, "Activate license: $code")
    }

    private fun generateUniqueCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        fun seg() = (1..4).map { chars.random() }.joinToString("")
        return "ANIM-${seg()}-${seg()}-${seg()}"
    }

    private fun getAdminToken(): String {
        return App.context
            .getSharedPreferences("animatv_admin", Context.MODE_PRIVATE)
            .getString("github_token", "") ?: ""
    }
}
