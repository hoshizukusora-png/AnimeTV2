package com.animatv.player.adapter

import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.animatv.player.R
import com.animatv.player.model.Category

class SidebarAdapter(
    private val categories: ArrayList<Category>?,
    private val onItemClick: (Category, Int) -> Unit
) : RecyclerView.Adapter<SidebarAdapter.ViewHolder>() {

    private var selectedPosition = 0

    // Icon teks pendek per kategori (tanpa emoji - pakai simbol ASCII/unicode safe)
    private val catIcons = mapOf(
        "nasional"      to "TV",
        "berita"        to "NWS",
        "hiburan"       to "ENT",
        "olahraga"      to "SPT",
        "internasional" to "INT",
        "jepang"        to "JPN",
        "vision+"       to "VIS",
        "vision"        to "VIS",
        "indihome"      to "IND",
        "custom"        to "CST",
        "favorit"       to "\u2605",
        "favorite"      to "\u2605"
    )

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val accent: View = view.findViewById(R.id.sidebar_accent)
        val icon: TextView = view.findViewById(R.id.sidebar_icon)
        val name: TextView = view.findViewById(R.id.sidebar_name)
        val count: TextView = view.findViewById(R.id.sidebar_count)
        val root: View = view.findViewById(R.id.sidebar_item_root)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sidebar, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cat = categories?.get(position) ?: return
        val catName = cat.name ?: ""
        val key = catName.lowercase().trim()

        val icon = catIcons.entries.firstOrNull { key.contains(it.key) }?.value ?: "CH"
        holder.icon.text = icon
        holder.name.text = catName

        val count = cat.channels?.size ?: 0
        holder.count.text = if (count > 99) "99+" else count.toString()

        val isSelected = position == selectedPosition
        holder.accent.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
        holder.root.setBackgroundResource(
            if (isSelected) R.drawable.sidebar_item_selected_bg
            else android.R.color.transparent
        )
        holder.name.setTextColor(
            if (isSelected) ContextCompat.getColor(holder.root.context, R.color.color_primary)
            else 0xE0F0E6FF.toInt()
        )

        holder.root.setOnClickListener {
            val prev = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selectedPosition)
            onItemClick(cat, selectedPosition)
        }
    }

    override fun getItemCount() = categories?.size ?: 0

    fun selectCategory(position: Int) {
        val prev = selectedPosition
        selectedPosition = position
        notifyItemChanged(prev)
        notifyItemChanged(selectedPosition)
    }
}
