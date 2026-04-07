package com.animatv.player.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import com.animatv.player.R
import com.animatv.player.adapter.SearchAdapter
import com.animatv.player.databinding.SearchDialogBinding
import com.animatv.player.extension.isFavorite
import com.animatv.player.model.Channel
import com.animatv.player.model.PlayData
import com.animatv.player.model.Playlist

class SearchDialog : DialogFragment() {
    private var _binding : SearchDialogBinding? = null
    private val binding get() = _binding!!
    lateinit var searchAdapter: SearchAdapter

    override fun onStart() {
        super.onStart()
        val d = dialog ?: return
        // Full screen
        d.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // Munculkan keyboard virtual otomatis saat dialog terbuka (TV + HP)
        d.window!!.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        // Minta fokus ke input setelah layout siap
        _binding?.searchInput?.post {
            val input = _binding?.searchInput ?: return@post
            input.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = AppCompatDialog(requireContext(), R.style.SettingsDialogThemeOverlay)
        dialog.setTitle(R.string.search_channel)
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SearchDialogBinding.inflate(inflater, container, false)
        val dialogView = binding.root

        val channels = ArrayList<Channel>()
        val listdata = ArrayList<PlayData>()
        val playlist = Playlist.cached
        for (catId in playlist.categories.indices) {
            val cat = playlist.categories[catId]
            if (catId == 0 && cat.isFavorite()) continue
            val ch = cat.channels ?: continue
            for (chId in ch.indices) {
                channels.add(ch[chId])
                listdata.add(PlayData(catId, chId))
            }
        }

        // recycler view
        searchAdapter = SearchAdapter(channels, listdata)
        binding.searchAdapter = searchAdapter
        binding.searchList.layoutManager = GridLayoutManager(context, spanColumn())
        binding.searchList.isFocusable = true
        binding.searchList.isFocusableInTouchMode = false
        binding.searchList.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        binding.searchList.itemAnimator = null

        // edittext
        binding.searchInput.apply {
            isFocusableInTouchMode = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable) {
                    searchAdapter.filter.filter(s)
                    binding.searchList.visibility = if (s.isNotEmpty()) View.VISIBLE else View.GONE
                    binding.searchReset.visibility = if (s.isNotEmpty()) View.VISIBLE else View.GONE
                }
            })
            // TV: ENTER di keyboard virtual = selesai mengetik, fokus ke hasil
            setOnEditorActionListener { _, _, _ ->
                if (binding.searchList.visibility == View.VISIBLE) {
                    binding.searchList.requestFocus()
                    binding.searchList.getChildAt(0)?.requestFocus()
                }
                false
            }
            // TV: DPAD_DOWN dari input pindah ke hasil pencarian
            setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN &&
                    binding.searchList.visibility == View.VISIBLE) {
                    binding.searchList.requestFocus()
                    binding.searchList.getChildAt(0)?.requestFocus()
                    true
                } else false
            }
        }

        // button cleartext
        binding.searchReset.setOnClickListener {
            binding.searchInput.text?.clear()
            binding.searchInput.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
        }

        // button close
        binding.searchClose.apply {
            setOnClickListener { dismiss() }
            // TV: tombol TUTUP bisa difokus & diklik dengan remote
            isFocusable = true
            isFocusableInTouchMode = false
        }

        return dialogView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun spanColumn(): Int {
        val screenWidthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return (screenWidthDp / 150 + 0.5).toInt()
    }
}
