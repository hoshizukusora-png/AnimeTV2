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
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride
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
        mappedTrackInfo: MappedTrackInfo,
        initialParameters: DefaultTrackSelector.Parameters,
        allowAdaptiveSelections: Boolean,
        allowMultipleOverrides: Boolean,
        onClickListener: DialogInterface.OnClickListener,
        onDismissListener: DialogInterface.OnDismissListener
    ) {
        this.titleId = titleId
        this.onClickListener = onClickListener
        this.onDismissListener = onDismissListener
        for (i in 0 until mappedTrackInfo.rendererCount) {
            if (showTabForRenderer(mappedTrackInfo, i)) {
                val trackType = mappedTrackInfo.getRendererType(i)
                val trackGroupArray = mappedTrackInfo.getTrackGroups(i)
                val tabFragment = TrackSelectionViewFragment()
                tabFragment.init(
                    mappedTrackInfo, i,
                    initialParameters.getRendererDisabled(i),
                    initialParameters.getSelectionOverride(i, trackGroupArray),
                    allowAdaptiveSelections, allowMultipleOverrides
                )
                tabFragments.put(i, tabFragment)
                tabTrackTypes.add(trackType)
            }
        }
    }

    fun getIsDisabled(rendererIndex: Int): Boolean {
        val rendererView = tabFragments[rendererIndex]
        return rendererView != null && rendererView.isDisabled
    }

    fun getOverrides(rendererIndex: Int): List<SelectionOverride> {
        val rendererView = tabFragments[rendererIndex]
        return if (rendererView == null) emptyList() else rendererView.overrides!!
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
        private var mappedTrackInfo: MappedTrackInfo? = null
        private var rendererIndex = 0
        private var allowAdaptiveSelections = false
        private var allowMultipleOverrides = false
        var isDisabled = false
        var overrides: List<SelectionOverride>? = null

        fun init(
            mappedTrackInfo: MappedTrackInfo?,
            rendererIndex: Int,
            initialIsDisabled: Boolean,
            initialOverride: SelectionOverride?,
            allowAdaptiveSelections: Boolean,
            allowMultipleOverrides: Boolean
        ) {
            this.mappedTrackInfo = mappedTrackInfo
            this.rendererIndex = rendererIndex
            isDisabled = initialIsDisabled
            overrides = initialOverride?.let { listOf(it) } ?: emptyList()
            this.allowAdaptiveSelections = allowAdaptiveSelections
            this.allowMultipleOverrides = allowMultipleOverrides
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val rootView = inflater.inflate(R.layout.exo_track_selection_dialog, container, false)
            val trackSelectionView: TrackSelectionView = rootView.findViewById(R.id.exo_track_selection_view)
            trackSelectionView.setShowDisableOption(true)
            trackSelectionView.setAllowMultipleOverrides(allowMultipleOverrides)
            trackSelectionView.setAllowAdaptiveSelections(allowAdaptiveSelections)
            trackSelectionView.init(mappedTrackInfo!!, rendererIndex, isDisabled, overrides!!, null, this)
            return rootView
        }

        override fun onTrackSelectionChanged(isDisabled: Boolean, overrides: List<SelectionOverride>) {
            this.isDisabled = isDisabled
            this.overrides = overrides
        }

        init { retainInstance = true }
    }

    companion object {
        fun willHaveContent(trackSelector: DefaultTrackSelector?): Boolean {
            val mappedTrackInfo = trackSelector?.currentMappedTrackInfo ?: return false
            return willHaveContent(mappedTrackInfo)
        }

        private fun willHaveContent(mappedTrackInfo: MappedTrackInfo): Boolean {
            for (i in 0 until mappedTrackInfo.rendererCount) {
                if (showTabForRenderer(mappedTrackInfo, i)) return true
            }
            return false
        }

        fun createForTrackSelector(
            trackSelector: DefaultTrackSelector?,
            onDismissListener: DialogInterface.OnDismissListener
        ): TrackSelectionDialog {
            val mappedTrackInfo = checkNotNull(trackSelector!!.currentMappedTrackInfo)
            val dialog = TrackSelectionDialog()
            val parameters = trackSelector.parameters
            dialog.init(
                R.string.track_selection_title, mappedTrackInfo, parameters,
                allowAdaptiveSelections = true, allowMultipleOverrides = false,
                onClickListener = { _, _ ->
                    val builder = parameters.buildUpon()
                    for (i in 0 until mappedTrackInfo.rendererCount) {
                        builder.clearSelectionOverrides(i).setRendererDisabled(i, dialog.getIsDisabled(i))
                        val overrides = dialog.getOverrides(i)
                        if (overrides.isNotEmpty()) {
                            builder.setSelectionOverride(i, mappedTrackInfo.getTrackGroups(i), overrides[0])
                        }
                    }
                    trackSelector.setParameters(builder)
                },
                onDismissListener = onDismissListener
            )
            return dialog
        }

        private fun showTabForRenderer(mappedTrackInfo: MappedTrackInfo, rendererIndex: Int): Boolean {
            val trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex)
            if (trackGroupArray.length == 0) return false
            val trackType = mappedTrackInfo.getRendererType(rendererIndex)
            return trackType == C.TRACK_TYPE_VIDEO || trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_TEXT
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
