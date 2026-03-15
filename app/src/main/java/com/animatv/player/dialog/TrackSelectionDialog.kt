package com.animatv.player.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.material.tabs.TabLayout
import com.animatv.player.R

/**
 * TrackSelectionDialog - kompatibel ExoPlayer 2.18.2
 * Menggunakan DefaultTrackSelector untuk pilih track video/audio/subtitle
 */
@Suppress("DEPRECATION")
class TrackSelectionDialog : DialogFragment() {

    private var trackSelector: DefaultTrackSelector? = null
    private lateinit var onDismissListener: DialogInterface.OnDismissListener

    companion object {
        fun willHaveContent(trackSelector: DefaultTrackSelector?): Boolean {
            val mappedTrackInfo = trackSelector?.currentMappedTrackInfo ?: return false
            for (i in 0 until mappedTrackInfo.rendererCount) {
                val trackGroups = mappedTrackInfo.getTrackGroups(i)
                if (trackGroups.length > 0) return true
            }
            return false
        }

        fun createForTrackSelector(
            trackSelector: DefaultTrackSelector,
            onDismissListener: DialogInterface.OnDismissListener
        ): TrackSelectionDialog {
            return TrackSelectionDialog().apply {
                this.trackSelector = trackSelector
                this.onDismissListener = onDismissListener
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AppCompatDialog(requireContext(), R.style.TrackSelectionDialogThemeOverlay).apply {
            setTitle(R.string.track_selection_title)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (::onDismissListener.isInitialized) onDismissListener.onDismiss(dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val dialogView = inflater.inflate(R.layout.track_selection_dialog, container, false)

        // Tab layout dan ViewPager (kosong untuk saat ini - track switching via ExoPlayer default)
        dialogView.findViewById<TabLayout>(R.id.track_selection_dialog_tab_layout)?.visibility = View.GONE

        dialogView.findViewById<Button>(R.id.track_selection_dialog_cancel_button)?.setOnClickListener {
            dismiss()
        }

        dialogView.findViewById<Button>(R.id.track_selection_dialog_ok_button)?.setOnClickListener {
            // Reset track selector to auto
            trackSelector?.let { ts ->
                val builder = ts.parameters.buildUpon()
                for (i in 0 until (ts.currentMappedTrackInfo?.rendererCount ?: 0)) {
                    builder.clearSelectionOverrides(i).setRendererDisabled(i, false)
                }
                ts.setParameters(builder)
            }
            dismiss()
        }

        return dialogView
    }

    init {
        retainInstance = true
    }
}
