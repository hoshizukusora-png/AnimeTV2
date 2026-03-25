/*
 * Copyright (C) 2019 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0
 */
package com.animatv.player.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.content.res.Resources
import android.os.Bundle
import android.util.SparseArray
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
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.TrackSelectionView
import com.google.android.exoplayer2.ui.TrackSelectionView.TrackSelectionListener
import com.google.android.material.tabs.TabLayout
import com.animatv.player.R

@Suppress("DEPRECATION")
class TrackSelectionDialog : DialogFragment() {
    private val tabFragments: SparseArray<TrackSelectionViewFragment> = SparseArray()
    private val tabTrackTypes: ArrayList<Int> = ArrayList()
    private var titleId = 0
    private lateinit var onClickListener: DialogInterface.OnClickListener
    private lateinit var onDismissListener: DialogInterface.OnDismissListener

    private fun init(
        titleId: Int,
        trackSelector: DefaultTrackSelector,
        onClickListener: DialogInterface.OnClickListener,
        onDismissListener: DialogInterface.OnDismissListener
    ) {
        this.titleId = titleId
        this.onClickListener = onClickListener
        this.onDismissListener = onDismissListener

        val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return
        val parameters = trackSelector.parameters

        for (i in 0 until mappedTrackInfo.rendererCount) {
            val trackType = mappedTrackInfo.getRendererType(i)
            if (trackType != C.TRACK_TYPE_VIDEO &&
                trackType != C.TRACK_TYPE_AUDIO &&
                trackType != C.TRACK_TYPE_TEXT) continue

            val trackGroupArray = mappedTrackInfo.getTrackGroups(i)
            if (trackGroupArray.length == 0) continue

            // Build List<Tracks.Group> from TrackGroupArray for new ExoPlayer 2.18.x API
            val trackGroupList = mutableListOf<Tracks.Group>()
            for (g in 0 until trackGroupArray.length) {
                val group = trackGroupArray[g]
                val supported = IntArray(group.length) { C.FORMAT_HANDLED }
                val selected = BooleanArray(group.length) { false }
                trackGroupList.add(Tracks.Group(group, false, supported, selected))
            }

            val overridesMap: Map<TrackGroup, TrackSelectionOverride> =
                parameters.overrides.filter { (tg, _) ->
                    (0 until trackGroupArray.length).any { trackGroupArray[it] == tg }
                }

            val tabFragment = TrackSelectionViewFragment()
            tabFragment.initFragment(
                trackGroupList,
                parameters.getRendererDisabled(i),
                overridesMap
            )
            tabFragments.put(i, tabFragment)
            tabTrackTypes.add(trackType)
        }
    }

    fun getIsDisabled(rendererIndex: Int): Boolean {
        val rendererView = tabFragments[rendererIndex]
        return rendererView != null && rendererView.isDisabled
    }

    fun getOverrides(rendererIndex: Int): Map<TrackGroup, TrackSelectionOverride> {
        val rendererView = tabFragments[rendererIndex]
        return rendererView?.overrides ?: emptyMap()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AppCompatDialog(requireContext(), R.style.TrackSelectionDialogThemeOverlay).apply {
            setTitle(titleId)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener.onDismiss(dialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val dialogView = inflater.inflate(R.layout.track_selection_dialog, container, false)
        val viewPager = dialogView.findViewById<ViewPager>(R.id.track_selection_dialog_view_pager).apply {
            adapter = FragmentAdapter(childFragmentManager)
        }
        dialogView.findViewById<TabLayout>(R.id.track_selection_dialog_tab_layout).apply {
            setupWithViewPager(viewPager)
            visibility = if (tabFragments.size() > 1) View.VISIBLE else View.GONE
        }
        dialogView.findViewById<Button>(R.id.track_selection_dialog_cancel_button).apply {
            setOnClickListener { dismiss() }
        }
        dialogView.findViewById<Button>(R.id.track_selection_dialog_ok_button).apply {
            setOnClickListener {
                onClickListener.onClick(dialog, DialogInterface.BUTTON_POSITIVE)
                dismiss()
            }
        }
        return dialogView
    }

    private inner class FragmentAdapter(fragmentManager: FragmentManager?) :
        FragmentPagerAdapter(fragmentManager!!, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getItem(position: Int): Fragment = tabFragments.valueAt(position)
        override fun getCount(): Int = tabFragments.size()
        override fun getPageTitle(position: Int): CharSequence =
            getTrackTypeString(resources, tabTrackTypes[position])
    }

    class TrackSelectionViewFragment : Fragment(), TrackSelectionListener {
        private var trackGroups: List<Tracks.Group> = emptyList()
        var isDisabled = false
        var overrides: Map<TrackGroup, TrackSelectionOverride> = emptyMap()

        fun initFragment(
            trackGroups: List<Tracks.Group>,
            initialIsDisabled: Boolean,
            initialOverrides: Map<TrackGroup, TrackSelectionOverride>
        ) {
            this.trackGroups = trackGroups
            isDisabled = initialIsDisabled
            overrides = initialOverrides
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val rootView = inflater.inflate(R.layout.exo_track_selection_dialog, container, false)
            val trackSelectionView: TrackSelectionView = rootView.findViewById(R.id.exo_track_selection_view)
            trackSelectionView.setShowDisableOption(true)
            trackSelectionView.setAllowMultipleOverrides(false)
            trackSelectionView.setAllowAdaptiveSelections(true)
            trackSelectionView.init(trackGroups, isDisabled, overrides, null, this)
            return rootView
        }

        // Updated to match ExoPlayer 2.18.x TrackSelectionListener interface
        override fun onTrackSelectionChanged(
            isDisabled: Boolean,
            overrides: MutableMap<TrackGroup, TrackSelectionOverride>
        ) {
            this.isDisabled = isDisabled
            this.overrides = overrides
        }

        init { retainInstance = true }
    }

    companion object {
        fun willHaveContent(trackSelector: DefaultTrackSelector?): Boolean {
            val mappedTrackInfo = trackSelector?.currentMappedTrackInfo ?: return false
            for (i in 0 until mappedTrackInfo.rendererCount) {
                val trackType = mappedTrackInfo.getRendererType(i)
                if ((trackType == C.TRACK_TYPE_VIDEO ||
                    trackType == C.TRACK_TYPE_AUDIO ||
                    trackType == C.TRACK_TYPE_TEXT) &&
                    mappedTrackInfo.getTrackGroups(i).length > 0) return true
            }
            return false
        }

        fun createForTrackSelector(
            trackSelector: DefaultTrackSelector?,
            onDismissListener: DialogInterface.OnDismissListener
        ): TrackSelectionDialog {
            requireNotNull(trackSelector)
            val dialog = TrackSelectionDialog()
            dialog.init(
                R.string.track_selection_title,
                trackSelector,
                onClickListener = { _, _ ->
                    val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return@init
                    val builder = trackSelector.parameters.buildUpon()
                    for (i in 0 until mappedTrackInfo.rendererCount) {
                        builder.setRendererDisabled(i, dialog.getIsDisabled(i))
                        for ((_, override) in dialog.getOverrides(i)) {
                            builder.addOverride(override)
                        }
                    }
                    trackSelector.setParameters(builder)
                },
                onDismissListener = onDismissListener
            )
            return dialog
        }

        private fun getTrackTypeString(resources: Resources, trackType: Int): String {
            return when (trackType) {
                C.TRACK_TYPE_VIDEO -> resources.getString(R.string.exo_track_selection_title_video)
                C.TRACK_TYPE_AUDIO -> resources.getString(R.string.exo_track_selection_title_audio)
                C.TRACK_TYPE_TEXT -> resources.getString(R.string.exo_track_selection_title_text)
                else -> throw IllegalArgumentException()
            }
        }
    }

    init { retainInstance = true }
}
