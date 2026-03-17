package com.animatv.player

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.animatv.player.extra.AdminManager
import com.animatv.player.extra.LicenseManager
import com.animatv.player.extra.Preferences

class AdminActivity : AppCompatActivity() {

    private val prefs = Preferences()

    // Feature names untuk UI
    private val featureLabels = mapOf(
        "toggle_sleep_timer"       to "Sleep Timer",
        "toggle_speed"             to "Playback Speed",
        "toggle_gesture"           to "Gesture Control",
        "toggle_double_tap"        to "Double Tap Rewind/Forward",
        "toggle_auto_quality"      to "Auto Quality",
        "toggle_mini_channel"      to "Mini Channel Panel",
        "toggle_anime_bg"          to "Anime Background",
        "toggle_anime_quote"       to "Quote of the Day",
        "toggle_sakura"            to "Sakura Effect",
        "toggle_anime_guide"       to "Anime Character Guide",
        "toggle_continue_watching" to "Continue Watching",
        "toggle_recently_watched"  to "Recently Watched",
        "toggle_channel_shortcut"  to "Channel Shortcut",
        "toggle_live_now"          to "Live Now Banner",
    )

    private val featureKeys = mapOf(
        "toggle_sleep_timer"       to "sleep_timer",
        "toggle_speed"             to "playback_speed",
        "toggle_gesture"           to "gesture_control",
        "toggle_double_tap"        to "double_tap",
        "toggle_auto_quality"      to "auto_quality",
        "toggle_mini_channel"      to "mini_channel",
        "toggle_anime_bg"          to "anime_background",
        "toggle_anime_quote"       to "anime_quote",
        "toggle_sakura"            to "sakura_effect",
        "toggle_anime_guide"       to "anime_guide",
        "toggle_continue_watching" to "continue_watching",
        "toggle_recently_watched"  to "recently_watched",
        "toggle_channel_shortcut"  to "channel_shortcut",
        "toggle_live_now"          to "live_now_banner",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setContentView(R.layout.activity_admin)

        // Cek admin auth
        if (!AdminManager.isAdminUnlocked) {
            finish()
            return
        }

        setupHeader()
        setupFeatureToggles()
        setupRemoteConfig()
        setupAnnouncement()
        setupMaintenance()
        setupPlaylistOverride()
        setupDangerZone()
        setupLicenseManagement()
    }

    private fun setupHeader() {
        val config = AdminManager.getConfig()
        findViewById<TextView>(R.id.txt_admin_version)?.text =
            "Config v${config.configVersion}"

        findViewById<Button>(R.id.btn_admin_logout)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Keluar Admin Mode")
                .setMessage("Yakin mau keluar dari Admin Panel?")
                .setPositiveButton("Keluar") { _, _ ->
                    AdminManager.lockAdmin()
                    finish()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun setupFeatureToggles() {
        val config = AdminManager.getConfig()

        // Setup tiap toggle
        featureLabels.forEach { (viewId, label) ->
            val resId = resources.getIdentifier(viewId, "id", packageName)
            val toggleView = findViewById<View>(resId) ?: return@forEach
            val featureKey = featureKeys[viewId] ?: return@forEach

            // Set label
            toggleView.findViewById<TextView>(R.id.txt_feature_name)?.text = label

            // Set state dari config
            val switch = toggleView.findViewById<SwitchCompat>(R.id.switch_feature)
            switch?.isChecked = AdminManager.isFeatureEnabled(featureKey)
        }

        // Tombol simpan
        findViewById<Button>(R.id.btn_save_features)?.setOnClickListener {
            saveFeatureChanges()
        }
    }

    private fun saveFeatureChanges() {
        // Baca semua toggle state dan buat JSON baru untuk di-push ke GitHub
        val sb = StringBuilder("{\n")
        sb.append("  \"configVersion\": ${AdminManager.getConfig().configVersion + 1},\n")
        sb.append("  \"lastUpdated\": \"${java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())}\",\n")

        featureKeys.forEach { (viewId, featureKey) ->
            val resId = resources.getIdentifier(viewId, "id", packageName)
            val toggleView = findViewById<View>(resId) ?: return@forEach
            val isEnabled = toggleView.findViewById<SwitchCompat>(R.id.switch_feature)?.isChecked ?: true

            val jsonKey = when (featureKey) {
                "sleep_timer" -> "featureSleepTimer"
                "playback_speed" -> "featurePlaybackSpeed"
                "gesture_control" -> "featureGestureControl"
                "double_tap" -> "featureDoubleTap"
                "auto_quality" -> "featureAutoQuality"
                "mini_channel" -> "featureMiniChannelPanel"
                "anime_background" -> "featureAnimeBackground"
                "anime_quote" -> "featureAnimeQuote"
                "sakura_effect" -> "featureSakuraEffect"
                "anime_guide" -> "featureAnimeGuide"
                "continue_watching" -> "featureContinueWatching"
                "recently_watched" -> "featureRecentlyWatched"
                "channel_shortcut" -> "featureChannelShortcut"
                "live_now_banner" -> "featureLiveNowBanner"
                else -> featureKey
            }
            sb.append("  \"$jsonKey\": $isEnabled,\n")
        }
        sb.append("  \"maintenanceMode\": false\n}")

        // Tampilkan JSON yang perlu di-update ke GitHub
        AlertDialog.Builder(this)
            .setTitle("Update Config")
            .setMessage("Salin JSON ini ke file config/features.json di GitHub:\n\n${sb}")
            .setPositiveButton("Salin") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("config", sb.toString()))
                toast("JSON berhasil disalin!")
                // Clear cache supaya app ambil config baru
                AdminManager.clearConfigCache()
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun setupRemoteConfig() {
        val statusTxt = findViewById<TextView>(R.id.txt_config_status)

        val config = AdminManager.getConfig()
        statusTxt?.text = "Config v${config.configVersion} | Update: ${config.lastUpdated}"

        findViewById<Button>(R.id.btn_fetch_config)?.setOnClickListener {
            statusTxt?.text = "Mengambil config dari GitHub..."
            AdminManager.clearConfigCache()
            AdminManager.fetchConfigAsync { newConfig ->
                statusTxt?.text = "Config v${newConfig.configVersion} | Update: ${newConfig.lastUpdated} [OK]"
                toast("Config berhasil diperbarui!")
                setupFeatureToggles() // refresh toggles
            }
        }

        findViewById<Button>(R.id.btn_clear_cache)?.setOnClickListener {
            AdminManager.clearConfigCache()
            toast("Cache config dihapus")
            statusTxt?.text = "Cache dihapus - akan fetch ulang saat dibuka"
        }
    }

    private fun setupAnnouncement() {
        val config = AdminManager.getConfig()
        val etAnnouncement = findViewById<EditText>(R.id.et_announcement)
        val switchAnn = findViewById<SwitchCompat>(R.id.switch_announcement)

        etAnnouncement?.setText(config.appAnnouncement)
        switchAnn?.isChecked = config.announcementEnabled

        findViewById<Button>(R.id.btn_save_announcement)?.setOnClickListener {
            val msg = etAnnouncement?.text?.toString() ?: ""
            val enabled = switchAnn?.isChecked ?: false
            toast("Pengumuman disimpan! Update config/features.json di GitHub dengan:\n\"appAnnouncement\": \"$msg\",\n\"announcementEnabled\": $enabled")
        }
    }

    private fun setupMaintenance() {
        val config = AdminManager.getConfig()
        val switchMaint = findViewById<SwitchCompat>(R.id.switch_maintenance)
        val etMsg = findViewById<EditText>(R.id.et_maintenance_msg)

        switchMaint?.isChecked = config.maintenanceMode
        etMsg?.setText(config.maintenanceMessage)

        findViewById<Button>(R.id.btn_save_maintenance)?.setOnClickListener {
            val isOn = switchMaint?.isChecked ?: false
            val msg = etMsg?.text?.toString() ?: ""

            if (isOn) {
                AlertDialog.Builder(this)
                    .setTitle("Aktifkan Maintenance?")
                    .setMessage("Semua user tidak bisa streaming sampai maintenance dimatikan!")
                    .setPositiveButton("Aktifkan") { _, _ ->
                        toast("Update config/features.json:\n\"maintenanceMode\": true,\n\"maintenanceMessage\": \"$msg\"")
                    }
                    .setNegativeButton("Batal") { _, _ -> switchMaint?.isChecked = false }
                    .show()
            } else {
                toast("Maintenance OFF. Update config/features.json:\n\"maintenanceMode\": false")
            }
        }
    }

    private fun setupPlaylistOverride() {
        val config = AdminManager.getConfig()
        val etPlaylist = findViewById<EditText>(R.id.et_playlist_url)
        val etBackup = findViewById<EditText>(R.id.et_backup_url)

        etPlaylist?.setText(config.playlistUrl)
        etBackup?.setText(config.backupPlaylistUrl)

        findViewById<Button>(R.id.btn_save_playlist)?.setOnClickListener {
            val url = etPlaylist?.text?.toString() ?: ""
            val backup = etBackup?.text?.toString() ?: ""
            toast("Update config/features.json:\n\"playlistUrl\": \"$url\",\n\"backupPlaylistUrl\": \"$backup\"")
        }
    }

    private fun setupDangerZone() {
        findViewById<Button>(R.id.btn_reset_app)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Semua Setting?")
                .setMessage("Ini akan menghapus semua setting, favorit, dan riwayat tontonan semua user!")
                .setPositiveButton("Reset") { _, _ ->
                    // Reset SharedPreferences
                    val sp = getSharedPreferences("animatv_admin", MODE_PRIVATE)
                    sp.edit().clear().apply()
                    toast("Setting admin di-reset")
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        findViewById<Button>(R.id.btn_force_update)?.setOnClickListener {
            AdminManager.clearConfigCache()
            toast("Cache dihapus - app akan fetch playlist terbaru")
        }
    }


    private fun setupLicenseManagement() {
        // Load saved token
        val savedToken = getSharedPreferences("animatv_admin", MODE_PRIVATE)
            .getString("github_token", "") ?: ""
        findViewById<android.widget.EditText>(R.id.et_github_token)?.setText(savedToken)

        // Generate kode lisensi baru
        findViewById<android.widget.Button>(R.id.btn_generate_key)?.setOnClickListener {
            val token = saveAndGetToken()
            val buyerName = findViewById<android.widget.EditText>(R.id.et_buyer_name)
                ?.text?.toString()?.trim() ?: ""

            if (token.isBlank()) {
                toast("Masukkan GitHub Token dulu!")
                return@setOnClickListener
            }
            if (buyerName.isBlank()) {
                toast("Masukkan nama pembeli!")
                return@setOnClickListener
            }

            val resultTxt = findViewById<android.widget.TextView>(R.id.txt_generated_key)
            resultTxt?.text = "Generating..."
            resultTxt?.visibility = android.view.View.VISIBLE

            LicenseManager.generateLicenseKey(buyerName, token) { result ->
                if (result.success) {
                    resultTxt?.text = result.code
                    resultTxt?.visibility = android.view.View.VISIBLE
                    // Copy otomatis ke clipboard
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("license", result.code))
                    toast("Kode: ${result.code}
(Disalin otomatis!)")
                } else {
                    resultTxt?.text = "Error: ${result.error}"
                    toast("Gagal: ${result.error}")
                }
            }
        }

        // Lihat semua lisensi
        findViewById<android.widget.Button>(R.id.btn_view_licenses)?.setOnClickListener {
            val token = saveAndGetToken()
            if (token.isBlank()) {
                toast("Masukkan GitHub Token dulu!")
                return@setOnClickListener
            }

            toast("Mengambil data lisensi...")
            LicenseManager.getAllLicenses(token) { keys ->
                if (keys == null) {
                    toast("Gagal ambil data lisensi")
                    return@getAllLicenses
                }

                val sb = StringBuilder()
                var total = 0; var active = 0; var available = 0
                keys.entrySet().forEach { entry ->
                    total++
                    val data = entry.value.asJsonObject
                    val status = data.get("status")?.asString ?: "?"
                    val name = data.get("userName")?.asString ?: "?"
                    val deviceId = data.get("deviceId")?.asString ?: ""
                    val activatedAt = data.get("activatedAt")?.asString ?: ""
                    when (status) {
                        "activated" -> active++
                        "available" -> available++
                    }
                    sb.append("${entry.key}
")
                    sb.append("  Nama: $name
")
                    sb.append("  Status: $status
")
                    if (deviceId.isNotBlank()) sb.append("  Device: ${deviceId.take(8)}...
")
                    if (activatedAt.isNotBlank()) sb.append("  Aktif: $activatedAt
")
                    sb.append("
")
                }

                val summary = "Total: $total | Aktif: $active | Tersedia: $available

$sb"
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Semua Lisensi")
                    .setMessage(summary)
                    .setPositiveButton("Salin") { _, _ ->
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("licenses", summary))
                        toast("Data disalin!")
                    }
                    .setNegativeButton("Tutup", null)
                    .show()
            }
        }

        // Revoke/cabut lisensi
        findViewById<android.widget.Button>(R.id.btn_revoke_key)?.setOnClickListener {
            val token = saveAndGetToken()
            val code = findViewById<android.widget.EditText>(R.id.et_revoke_code)
                ?.text?.toString()?.trim()?.uppercase() ?: ""

            if (token.isBlank()) { toast("Masukkan GitHub Token!"); return@setOnClickListener }
            if (code.isBlank()) { toast("Masukkan kode yang mau dicabut!"); return@setOnClickListener }

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Cabut Lisensi?")
                .setMessage("Kode $code akan dinonaktifkan. User tidak bisa login lagi sampai beli kode baru.")
                .setPositiveButton("Cabut") { _, _ ->
                    LicenseManager.revokeLicense(code, token) { success ->
                        if (success) toast("Lisensi $code berhasil dicabut!")
                        else toast("Gagal mencabut lisensi")
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun saveAndGetToken(): String {
        val token = findViewById<android.widget.EditText>(R.id.et_github_token)
            ?.text?.toString()?.trim() ?: ""
        if (token.isNotBlank()) {
            getSharedPreferences("animatv_admin", MODE_PRIVATE)
                .edit().putString("github_token", token).apply()
        }
        return token
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
