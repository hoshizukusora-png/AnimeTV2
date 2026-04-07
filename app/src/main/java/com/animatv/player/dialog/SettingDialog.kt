package com.animatv.player.dialog

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private val tabFragment = arrayOf(SettingSourcesFragment(), SettingAppFragment(), SettingAboutFragment())
    private val tabTitle = arrayOf(R.string.tab_sources, R.string.tab_app, R.string.tab_about)
    private var revertCountryId = ""
    private var isCancelled = true
    private var _binding: SettingDialogBinding? = null

    companion object {
        var isSourcesChanged = false
    }

    @Suppress("DEPRECATION")
    inner class FragmentAdapter(fragmentManager: FragmentManager?) :
        FragmentPagerAdapter(fragmentManager!!, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getItem(position: Int): Fragment = tabFragment[position]
        override fun getCount(): Int = tabFragment.size
        override fun getPageTitle(position: Int): CharSequence = getString(tabTitle[position])
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AppCompatDialog(requireContext(), R.style.SettingsDialogThemeOverlay).apply {
            setTitle(R.string.settings)
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onStart() {
        super.onStart()
        // Full screen dialog untuk layar TV
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // Fokus awal ke tab pertama saat dialog terbuka
        _binding?.settingTabLayout?.post {
            _binding?.settingCancelButton?.requestFocus()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = SettingDialogBinding.inflate(inflater, container, false)
        _binding = binding

        // init
        isSourcesChanged = false
        SettingAppFragment.launchAtBoot = preferences.launchAtBoot
        SettingAppFragment.playLastWatched = preferences.playLastWatched
        SettingAppFragment.sortFavorite = preferences.sortFavorite
        SettingAppFragment.sortCategory = preferences.sortCategory
        SettingAppFragment.sortChannel = preferences.sortChannel
        SettingAppFragment.optimizePrebuffer = preferences.optimizePrebuffer
        SettingAppFragment.reverseNavigation = preferences.reverseNavigation
        SettingSourcesFragment.sources = preferences.sources
        revertCountryId = preferences.countryId

        // view pager + tab layout
        binding.settingViewPager.adapter = FragmentAdapter(childFragmentManager)
        binding.settingTabLayout.setupWithViewPager(binding.settingViewPager)

        // TV: tab layout bisa difokus dengan remote
        binding.settingTabLayout.apply {
            isFocusable = true
            isFocusableInTouchMode = false
            // DPAD kiri/kanan ganti tab
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val next = binding.settingViewPager.currentItem + 1
                        if (next < tabFragment.size) {
                            binding.settingViewPager.currentItem = next
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        val prev = binding.settingViewPager.currentItem - 1
                        if (prev >= 0) {
                            binding.settingViewPager.currentItem = prev
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Turun ke konten ViewPager
                        binding.settingViewPager.requestFocus()
                        true
                    }
                    else -> false
                }
            }
        }

        // TV: navigasi antara tombol BATAL dan OKE
        binding.settingCancelButton.apply {
            isFocusable = true
            isFocusableInTouchMode = false
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { binding.settingOkButton.requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_UP    -> { binding.settingTabLayout.requestFocus(); true }
                    else -> false
                }
            }
            setOnClickListener { dismiss() }
        }

        binding.settingOkButton.apply {
            isFocusable = true
            isFocusableInTouchMode = false
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { binding.settingCancelButton.requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_UP   -> { binding.settingTabLayout.requestFocus(); true }
                    else -> false
                }
            }
            setOnClickListener {
                isCancelled = false
                preferences.launchAtBoot = SettingAppFragment.launchAtBoot
                preferences.playLastWatched = SettingAppFragment.playLastWatched
                preferences.sortFavorite = SettingAppFragment.sortFavorite
                preferences.sortCategory = SettingAppFragment.sortCategory
                preferences.sortChannel = SettingAppFragment.sortChannel
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
        }

        return binding.root
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
                .putExtra(MainActivity.MAIN_CALLBACK, MainActivity.UPDATE_PLAYLIST))
    }
}
