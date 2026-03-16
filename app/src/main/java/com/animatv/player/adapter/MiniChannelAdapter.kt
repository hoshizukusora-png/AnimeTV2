package com.animatv.player.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.animatv.player.R
import com.animatv.player.model.Channel

class MiniChannelAdapter(
    private val channels: List<Channel>,
    private val onChannelClick: (Int) -> Unit
) : RecyclerView.Adapter<MiniChannelAdapter.ViewHolder>() {

    private var activeIndex: Int = 0
    private lateinit var context: Context

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.mini_channel_root)
        val imgLogo: ImageView = view.findViewById(R.id.mini_img_logo)
        val txtLogo: TextView = view.findViewById(R.id.mini_txt_logo)
        val txtName: TextView = view.findViewById(R.id.mini_channel_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_mini_channel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = channels[position]

        // Nama channel
        holder.txtName.text = channel.name

        // Highlight channel aktif
        val isActive = position == activeIndex
        holder.root.setBackgroundResource(
            if (isActive) R.drawable.mini_channel_active_bg
            else R.drawable.mini_channel_bg
        )
        holder.txtName.setTextColor(
            if (isActive) 0xFFE91E8C.toInt()
            else 0xCCFFFFFF.toInt()
        )

        // Load logo
        val logoUrl = channel.logo
        if (!logoUrl.isNullOrBlank()) {
            holder.imgLogo.visibility = View.VISIBLE
            holder.txtLogo.visibility = View.GONE
            Glide.with(context)
                .load(logoUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.color.transparent)
                .error(android.R.color.transparent)
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        holder.imgLogo.visibility = View.GONE
                        holder.txtLogo.visibility = View.VISIBLE
                        holder.txtLogo.text = channel.name?.take(1)?.uppercase() ?: "?"
                        return false
                    }
                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        holder.imgLogo.visibility = View.VISIBLE
                        holder.txtLogo.visibility = View.GONE
                        return false
                    }
                })
                .into(holder.imgLogo)
        } else {
            holder.imgLogo.visibility = View.GONE
            holder.txtLogo.visibility = View.VISIBLE
            holder.txtLogo.text = channel.name?.take(1)?.uppercase() ?: "?"
        }

        // Klik pindah channel
        holder.root.setOnClickListener {
            val prev = activeIndex
            activeIndex = holder.adapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(activeIndex)
            onChannelClick(activeIndex)
        }
    }

    override fun getItemCount() = channels.size

    fun setActiveChannel(index: Int) {
        val prev = activeIndex
        activeIndex = index
        notifyItemChanged(prev)
        notifyItemChanged(activeIndex)
    }

    fun getActiveIndex() = activeIndex
}
