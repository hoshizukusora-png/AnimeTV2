package com.animatv.player.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.animatv.player.R
import com.animatv.player.databinding.SettingAppFragmentBinding
import com.animatv.player.extra.LocaleHelper

class SettingAppFragment : Fragment() {
    companion object {
        var launchAtBoot = false
        var playLastWatched = false
        var sortFavorite = false
        var sortCategory = false
        var sortChannel = true
        var optimizePrebuffer = true
        var reverseNavigation = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = SettingAppFragmentBinding.inflate(inflater, container, false)

        // Language selector setup
        val languageCodes = arrayOf("in", "en", "ja", "ms")
        val languageNames = arrayOf(
            getString(R.string.language_id),
            getString(R.string.language_en),
            getString(R.string.language_ja),
            getString(R.string.language_ms)
        )

        val currentCode = LocaleHelper.getLanguageCode(requireContext())
        val currentIndex = languageCodes.indexOf(currentCode).takeIf { it >= 0 } ?: 0
        binding.textCurrentLanguage.text = languageNames[currentIndex]

        binding.languageSelector.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.select_language))
                .setSingleChoiceItems(languageNames, currentIndex) { dialog, which ->
                    val selectedCode = languageCodes[which]
                    if (selectedCode != currentCode) {
                        LocaleHelper.saveLanguageCode(requireContext(), selectedCode)
                        dialog.dismiss()
                        // Restart app to apply language
                        val intent = requireActivity().packageManager
                            .getLaunchIntentForPackage(requireActivity().packageName)
                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        requireActivity().finishAffinity()
                        startActivity(intent)
                    } else {
                        dialog.dismiss()
                    }
                }
                .setNegativeButton(getString(R.string.cancel_button_label), null)
                .show()
        }

        binding.launchAtBoot.apply {
            isChecked = launchAtBoot
            setOnClickListener { launchAtBoot = isChecked }
        }

        binding.openLastWatched.apply {
            isChecked = playLastWatched
            setOnClickListener { playLastWatched = isChecked }
        }

        binding.sortFavorite.apply {
            isChecked = sortFavorite
            setOnClickListener {
                sortFavorite = isChecked
                SettingDialog.isSourcesChanged = true
            }
        }

        binding.sortCategory.apply {
            isChecked = sortCategory
            setOnClickListener {
                sortCategory = isChecked
                SettingDialog.isSourcesChanged = true
            }
        }

        binding.sortChannel.apply {
            isChecked = sortChannel
            setOnClickListener {
                sortChannel = isChecked
                SettingDialog.isSourcesChanged = true
            }
        }

        binding.optimizePrebuffer.apply {
            isChecked = optimizePrebuffer
            setOnClickListener { optimizePrebuffer = isChecked }
        }

        binding.reverseNavigation.apply {
            isChecked = reverseNavigation
            setOnClickListener { reverseNavigation = isChecked }
        }

        return binding.root
    }
}
