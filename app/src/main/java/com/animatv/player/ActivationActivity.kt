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

class ActivationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setContentView(R.layout.activity_activation)

        // Tampilkan device ID
        findViewById<TextView>(R.id.txt_device_id)?.text = LicenseManager.getDeviceId()

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
