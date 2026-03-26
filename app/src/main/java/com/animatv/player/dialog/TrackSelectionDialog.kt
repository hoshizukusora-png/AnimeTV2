/*
 * Copyright (C) 2019 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0
 *
 * Rewritten for ExoPlayer 2.17.x API compatibility
 * (Tracks / TrackSelectionOverride tidak ada di 2.17.x)
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
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
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

            val tabFragment = TrackSelectionViewFragment()
            tabFragment.initFragment(
                mappedTrackInfo,
                i,
                parameters.getRendererDisabled(i),
                parameters.getSelectionOverride(i, trackGroupArray)
            )
            tabFragments.put(i, tabFragment)
            tabTrackTypes.add(trackType)
        }
    }

    fun getIsDisabled(rendererIndex: Int): Boolean {
        return tabFragments[rendererIndex]?.isDisabled ?: false
    }

    fun getOverride(rendererIndex: Int): DefaultTrackSelector.SelectionOverride? {
        return tabFragments[rendererIndex]?.override
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val dialogView = inflater.inflate(R.layout.track_selection_dialog, container, false)

        val viewPager = dialogView.findViewById<ViewPager>(R.id.track_selection_dialog_view_pager)
        viewPager.adapter = FragmentAdapter(childFragmentManager)

        val tabLayout = dialogView.findViewById<TabLayout>(R.id.track_selection_dialog_tab_layout)
        tabLayout.setupWithViewPager(viewPager)
        tabLayout.visibility = if (tabFragments.size() > 1) View.VISIBLE else View.GONE

        dialogView.findViewById<Button>(R.id.track_selection_dialog_cancel_button)
            .setOnClickListener { dismiss() }

        dialogView.findViewById<Button>(R.id.track_selection_dialog_ok_button)
            .setOnClickListener {
                onClickListener.onClick(dialog, DialogInterface.BUTTON_POSITIVE)
                dismiss()
            }

        return dialogView
    }

    private inner class FragmentAdapter(fragmentManager: FragmentManager) :
        FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getItem(position: Int): Fragment = tabFragments.valueAt(position)
        override fun getCount(): Int = tabFragments.size()
        override fun getPageTitle(position: Int): CharSequence =
            getTrackTypeString(resources, tabTrackTypes[position])
    }

    class TrackSelectionViewFragment : Fragment(), TrackSelectionListener {

        private lateinit var mappedTrackInfo: MappedTrackInfo
        private var rendererIndex = 0

        var isDisabled = false
        var override: DefaultTrackSelector.SelectionOverride? = null

        fun initFragment(
            mappedTrackInfo: MappedTrackInfo,
            rendererIndex: Int,
            initialIsDisabled: Boolean,
            initialOverride: DefaultTrackSelector.SelectionOverride?
        ) {
            this.mappedTrackInfo = mappedTrackInfo
            this.rendererIndex = rendererIndex
            this.isDisabled = initialIsDisabled
            this.override = initialOverride
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val rootView = inflater.inflate(
                com.google.android.exoplayer2.R.layout.exo_track_selection_dialog,
                container, false
            )
            val trackSelectionView: TrackSelectionView =
                rootView.findViewById(com.google.android.exoplayer2.R.id.exo_track_selection_view)

            trackSelectionView.setShowDisableOption(true)
            trackSelectionView.setAllowMultipleOverrides(false)
            trackSelectionView.setAllowAdaptiveSelections(true)

            // ExoPlayer 2.17.x API
            trackSelectionView.init(
                mappedTrackInfo,
                rendererIndex,
                isDisabled,
                if (override != null) listOf(override!!) else emptyList(),
                null,
                this
            )
            return rootView
        }

        // ExoPlayer 2.17.x TrackSelectionListener signature
        override fun onTrackSelectionChanged(
            isDisabled: Boolean,
            overrides: MutableList<DefaultTrackSelector.SelectionOverride>
        ) {
            this.isDisabled = isDisabled
            this.override = overrides.firstOrNull()
        }

        init {
            @Suppress("DEPRECATION")
            retainInstance = true
        }
    }

    companion object {

        fun willHaveContent(trackSelector: DefaultTrackSelector?): Boolean {
            val mappedTrackInfo = trackSelector?.currentMappedTrackInfo ?: return false
            for (i in 0 until mappedTrackInfo.rendererCount) {
                val trackType = mappedTrackInfo.getRendererType(i)
                if ((trackType == C.TRACK_TYPE_VIDEO ||
                            trackType == C.TRACK_TYPE_AUDIO ||
                            trackType == C.TRACK_TYPE_TEXT) &&
                    mappedTrackInfo.getTrackGroups(i).length > 0
                ) return true
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
                titleId = R.string.track_selection_title,
                trackSelector = trackSelector,
                onClickListener = { _, _ ->
                    val mappedTrackInfo =
                        trackSelector.currentMappedTrackInfo ?: return@init
                    val builder = trackSelector.parameters.buildUpon()
                    for (i in 0 until mappedTrackInfo.rendererCount) {
                        val trackGroups = mappedTrackInfo.getTrackGroups(i)
                        builder.setRendererDisabled(i, dialog.getIsDisabled(i))
                        val ov = dialog.getOverride(i)
                        if (ov != null) {
                            builder.setSelectionOverride(i, trackGroups, ov)
                        } else {
                            builder.clearSelectionOverrides(i)
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
                C.TRACK_TYPE_VIDEO -> resources.getString(
                    com.google.android.exoplayer2.R.string.exo_track_selection_title_video
                )
                C.TRACK_TYPE_AUDIO -> resources.getString(
                    com.google.android.exoplayer2.R.string.exo_track_selection_title_audio
                )
                C.TRACK_TYPE_TEXT -> resources.getString(
                    com.google.android.exoplayer2.R.string.exo_track_selection_title_text
                )
                else -> throw IllegalArgumentException("Unknown track type: $trackType")
            }
        }
    }

    init {
        @Suppress("DEPRECATION")
        retainInstance = true
    }
}
