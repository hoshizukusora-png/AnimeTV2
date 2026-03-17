package com.animatv.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.ybq.android.spinkit.SpinKitView
import com.animatv.player.extra.LicenseManager
import com.animatv.player.extra.AdminManager

class ActivationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setContentView(R.layout.activity_activation)

        // Tampilkan device ID
        findViewById<TextView>(R.id.txt_device_id)?.text = LicenseManager.getDeviceId()

        // Tap "ANIME" text 7x untuk buka Admin Panel
        var tapCount = 0
        var lastTapTime = 0L
        findViewById<android.widget.TextView>(R.id.txt_anime_logo)?.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTime > 3000) tapCount = 0
            tapCount++
            lastTapTime = now
            if (tapCount >= 7) {
                tapCount = 0
                showAdminLoginFromActivation()
            }
        }

        // Tombol aktivasi
        findViewById<Button>(R.id.btn_activate)?.setOnClickListener {
            val code = findViewById<android.widget.EditText>(R.id.et_license_code)
                ?.text?.toString()?.trim() ?: ""

            if (code.length < 10) {
                showStatus("Masukkan kode aktivasi yang valid", isError = true)
                return@setOnClickListener
            }

            startActivation(code)
        }
    }


    private fun showAdminLoginFromActivation() {
        if (AdminManager.isAdminUnlocked) {
            startActivity(android.content.Intent(this, AdminActivity::class.java))
            return
        }
        val input = android.widget.EditText(this).apply {
            hint = "Kode admin"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(40, 20, 40, 20)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Admin Panel")
            .setView(input)
            .setPositiveButton("Masuk") { _, _ ->
                if (AdminManager.tryUnlockAdmin(input.text.toString())) {
                    startActivity(android.content.Intent(this, AdminActivity::class.java))
                } else {
                    android.widget.Toast.makeText(this, "Kode salah!", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun startActivation(code: String) {
        val loading = findViewById<SpinKitView>(R.id.loading_activation)
        val btnActivate = findViewById<Button>(R.id.btn_activate)

        loading?.visibility = View.VISIBLE
        btnActivate?.isEnabled = false
        showStatus("Memverifikasi kode...", isError = false)

        LicenseManager.activateLicense(code) { result ->
            loading?.visibility = View.GONE
            btnActivate?.isEnabled = true

            when (result) {
                is LicenseManager.ActivationResult.Success -> {
                    showStatus("Aktivasi berhasil! Selamat datang, ${result.userName}!", isError = false)
                    // Tunggu 2 detik lalu masuk ke app
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        goToMain()
                    }, 2000)
                }

                is LicenseManager.ActivationResult.AlreadyActivated -> {
                    showStatus("Sudah aktif! Membuka aplikasi...", isError = false)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        goToMain()
                    }, 1000)
                }

                is LicenseManager.ActivationResult.AlreadyUsed -> {
                    showStatus(
                        "Kode ini sudah digunakan perangkat lain (${result.deviceHint}).\n" +
                        "Beli kode baru atau hubungi admin.",
                        isError = true
                    )
                }

                is LicenseManager.ActivationResult.InvalidCode -> {
                    showStatus(
                        "Kode tidak valid atau tidak ditemukan.\n" +
                        "Pastikan kode diketik dengan benar.",
                        isError = true
                    )
                }

                is LicenseManager.ActivationResult.NetworkError -> {
                    showStatus(
                        "Gagal terhubung ke server.\n" +
                        "Periksa koneksi internet dan coba lagi.",
                        isError = true
                    )
                }
            }
        }
    }

    private fun showStatus(message: String, isError: Boolean) {
        val statusTxt = findViewById<TextView>(R.id.txt_activation_status)
        statusTxt?.visibility = View.VISIBLE
        statusTxt?.text = message
        statusTxt?.setTextColor(
            if (isError) 0xFFFF5252.toInt()
            else 0xFF4CAF50.toInt()
        )
    }

    private fun goToMain() {
        startActivity(Intent(this, SplashActivity::class.java))
        finish()
    }

    // Tidak bisa back dari layar aktivasi
    override fun onBackPressed() {
        // Do nothing - harus aktivasi dulu
    }
}
