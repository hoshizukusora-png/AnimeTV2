package com.animatv.player.dialog

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.animatv.player.MainActivity
import com.animatv.player.R
import com.animatv.player.databinding.SettingDialogBinding
import com.animatv.player.extra.Preferences

class SettingDialog : DialogFragment() {

    val preferences = Preferences()
    private val tabFragment = arrayOf(
        SettingSourcesFragment(),
        SettingAppFragment(),
        SettingAboutFragment()
    )
    private var revertCountryId = ""
    private var isCancelled = true
    private var _binding: SettingDialogBinding? = null
    private val binding get() = _binding!!
    private var currentTab = 0

    companion object {
        var isSourcesChanged = false
    }

    inner class FragmentAdapter(fm: FragmentManager) :
        FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getItem(position: Int): Fragment = tabFragment[position]
        override fun getCount() = tabFragment.size
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AppCompatDialog(requireContext(), R.style.SettingsDialogThemeOverlay).apply {
            setTitle(R.string.settings)
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // Fokus awal ke tab pertama
        _binding?.tabPlaylist?.post {
            _binding?.tabPlaylist?.requestFocus()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SettingDialogBinding.inflate(inflater, container, false)

        // Init preferences
        isSourcesChanged = false
        SettingAppFragment.launchAtBoot      = preferences.launchAtBoot
        SettingAppFragment.playLastWatched   = preferences.playLastWatched
        SettingAppFragment.sortFavorite      = preferences.sortFavorite
        SettingAppFragment.sortCategory      = preferences.sortCategory
        SettingAppFragment.sortChannel       = preferences.sortChannel
        SettingAppFragment.optimizePrebuffer = preferences.optimizePrebuffer
        SettingAppFragment.reverseNavigation = preferences.reverseNavigation
        SettingSourcesFragment.sources       = preferences.sources
        revertCountryId = preferences.countryId

        // ViewPager (tidak pakai TabLayout bawaan — kita pakai tab custom)
        binding.settingViewPager.adapter = FragmentAdapter(childFragmentManager)
        binding.settingViewPager.offscreenPageLimit = 2  // semua fragment di-load

        // Sinkronisasi ViewPager → update visual tab saat swipe
        binding.settingViewPager.addOnPageChangeListener(object :
            androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                currentTab = position
                updateTabVisuals(position)
            }
        })

        // Setup 3 tab TextView
        setupTab(binding.tabPlaylist, 0)
        setupTab(binding.tabApp,      1)
        setupTab(binding.tabAbout,    2)

        // Tampilkan tab pertama sebagai aktif
        updateTabVisuals(0)

        // ── Tombol BATAL ──
        binding.settingCancelButton.apply {
            isFocusable = true
            isFocusableInTouchMode = false
            setOnClickListener { dismiss() }
            setOnFocusChangeListener { v, has ->
                (v as TextView).setTextColor(if (has) 0xFFE91E8C.toInt() else 0xFF9090BB.toInt())
            }
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        focusCurrentTab(); true
                    }
                    else -> false
                }
            }
        }

        // ── Tombol OKE ──
        binding.settingOkButton.apply {
            isFocusable = true
            isFocusableInTouchMode = false
            setOnFocusChangeListener { v, has ->
                (v as TextView).setTextColor(if (has) 0xFFFF69B4.toInt() else 0xFFE91E8C.toInt())
            }
            setOnClickListener { saveAndDismiss() }
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        focusCurrentTab(); true
                    }
                    else -> false
                }
            }
        }

        return binding.root
    }

    /** Setup satu tab: klik + remote navigasi */
    private fun setupTab(tab: TextView, index: Int) {
        tab.isFocusable = true
        tab.isFocusableInTouchMode = false

        // Highlight saat fokus dari remote
        tab.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                tab.setTextColor(0xFFFFFFFF.toInt())
            } else {
                updateTabVisuals(currentTab) // kembalikan warna normal
            }
        }

        // Klik (touch / ENTER remote)
        tab.setOnClickListener {
            selectTab(index)
        }

        // Navigasi remote di dalam tab bar
        tab.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                // ENTER / OK → pilih tab ini
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    selectTab(index)
                    true
                }
                // D-pad kiri → pindah ke tab sebelumnya
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (index > 0) getTabView(index - 1)?.requestFocus()
                    true
                }
                // D-pad kanan → pindah ke tab berikutnya
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (index < 2) getTabView(index + 1)?.requestFocus()
                    true
                }
                // D-pad bawah → masuk ke konten ViewPager
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    binding.settingViewPager.requestFocus()
                    // Coba masuk ke konten fragment
                    val frag = tabFragment.getOrNull(currentTab)
                    frag?.view?.requestFocus()
                    frag?.view?.findFocus()?.let { } ?: run {
                        // Fallback: fokus ke tombol BATAL
                        binding.settingCancelButton.requestFocus()
                    }
                    true
                }
                // D-pad atas → (sudah di paling atas, tidak kemana-mana)
                KeyEvent.KEYCODE_DPAD_UP -> true
                // BACK → tutup dialog
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE -> {
                    dismiss(); true
                }
                else -> false
            }
        }
    }

    /** Ganti tab aktif */
    private fun selectTab(index: Int) {
        currentTab = index
        binding.settingViewPager.currentItem = index
        updateTabVisuals(index)
        // Fokus tetap di tab yang dipilih agar user bisa navigasi ke kanan/kiri
        getTabView(index)?.requestFocus()
    }

    /** Update warna & background semua tab sesuai yang aktif */
    private fun updateTabVisuals(activeIndex: Int) {
        val tabs = listOf(binding.tabPlaylist, binding.tabApp, binding.tabAbout)
        tabs.forEachIndexed { i, tab ->
            if (i == activeIndex) {
                tab.setTextColor(0xFFE91E8C.toInt())
                tab.setBackgroundResource(R.drawable.tab_selected_bg)
            } else {
                tab.setTextColor(0xFF888888.toInt())
                tab.setBackgroundResource(R.drawable.tab_unselected_bg)
            }
        }
    }

    private fun getTabView(index: Int): TextView? = when (index) {
        0 -> _binding?.tabPlaylist
        1 -> _binding?.tabApp
        2 -> _binding?.tabAbout
        else -> null
    }

    private fun focusCurrentTab() {
        getTabView(currentTab)?.requestFocus()
    }

    private fun saveAndDismiss() {
        isCancelled = false
        preferences.launchAtBoot      = SettingAppFragment.launchAtBoot
        preferences.playLastWatched   = SettingAppFragment.playLastWatched
        preferences.sortFavorite      = SettingAppFragment.sortFavorite
        preferences.sortCategory      = SettingAppFragment.sortCategory
        preferences.sortChannel       = SettingAppFragment.sortChannel
        preferences.optimizePrebuffer = SettingAppFragment.optimizePrebuffer
        preferences.reverseNavigation = SettingAppFragment.reverseNavigation
        val sources = SettingSourcesFragment.sources
        if (sources?.filter { s -> s.active }?.size == 0) {
            sources[0].active = true
            Toast.makeText(context, R.string.warning_none_source_active, Toast.LENGTH_SHORT).show()
        }
        preferences.sources = sources
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        if (isCancelled) preferences.countryId = revertCountryId
        else if (isSourcesChanged) sendUpdatePlaylist(requireContext())
    }

    private fun sendUpdatePlaylist(context: Context) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(
            Intent(MainActivity.MAIN_CALLBACK)
                .putExtra(MainActivity.MAIN_CALLBACK, MainActivity.UPDATE_PLAYLIST)
        )
    }
}
