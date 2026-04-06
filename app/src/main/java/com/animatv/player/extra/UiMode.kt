package com.animatv.player.extra

import android.app.UiModeManager
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import com.animatv.player.App

class UiMode {
    private val context = App.context

    fun isTelevision(): Boolean {
        // [1] Cara resmi: UI mode type
        val manager = context.getSystemService(AppCompatActivity.UI_MODE_SERVICE) as UiModeManager
        if (manager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true

        // [2] Feature leanback – hampir semua Android TV & TV Box modern
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)) return true

        // [3] TV box yang tidak declare leanback: cek tidak ada touchscreen
        // (remote-only device = tidak punya touchscreen)
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) return true

        return false
    }
}