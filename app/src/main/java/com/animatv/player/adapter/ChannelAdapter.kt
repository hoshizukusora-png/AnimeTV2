package com.animatv.player.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import com.animatv.player.BR
import com.animatv.player.MainActivity
import com.animatv.player.PlayerActivity
import com.animatv.player.R
import com.animatv.player.databinding.ItemChannelBinding
import com.animatv.player.extension.*
import com.animatv.player.model.Channel
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.animatv.player.model.PlayData
import com.animatv.player.model.Playlist

interface ChannelClickListener {
    fun onClicked(ch: Channel, catId: Int, chId: Int)
    fun onLongClicked(ch: Channel, catId: Int, chId: Int): Boolean
}

class ChannelAdapter(val channels: ArrayList<Channel>?, private val catId: Int, private val isFav: Boolean) :
    RecyclerView.Adapter<ChannelAdapter.ViewHolder>(), ChannelClickListener {
    lateinit var context: Context

    class ViewHolder(var itemChBinding: ItemChannelBinding) :
        RecyclerView.ViewHolder(itemChBinding.root) {
        fun bind(obj: Any?) {
            itemChBinding.setVariable(BR.modelChannel, obj)
            itemChBinding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val binding: ItemChannelBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context), R.layout.item_channel, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val channel: Channel? = channels?.get(position)
        viewHolder.bind(channel)
        viewHolder.itemChBinding.catId = catId
        viewHolder.itemChBinding.chId = position
        viewHolder.itemChBinding.clickListener = this
        viewHolder.itemChBinding.btnPlay.apply {
            setOnFocusChangeListener { v, hasFocus ->
                v.startAnimation(hasFocus)
                // Highlight lebih jelas saat fokus dari remote TV
                if (hasFocus) {
                    v.setBackgroundResource(R.drawable.channel_card_focused_bg)
                } else {
                    v.setBackgroundResource(R.drawable.channel_card_bg)
                }
            }
            // TV Remote: ENTER/DPAD_CENTER trigger click
            setOnKeyListener { v, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                    (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                     keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                    this@ChannelAdapter.onClicked(channel ?: return@setOnKeyListener false, catId, position)
                    true
                } else false
            }
        }

        // Load logo channel dengan Glide
        // Kalau logo URL ada: tampilkan ImageView, sembunyikan TextView inisial
        // Kalau tidak ada: tampilkan TextView inisial, sembunyikan ImageView
        val imgLogo = viewHolder.itemChBinding.imgLogo
        val txtLogo = viewHolder.itemChBinding.txtLogo
        val logoUrl = channel?.logo
        if (!logoUrl.isNullOrBlank()) {
            Glide.with(context)
                .load(logoUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.color.transparent)
                .error(android.R.color.transparent)
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?, target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        isFirstResource: Boolean): Boolean {
                        imgLogo.visibility = View.GONE
                        txtLogo.visibility = View.VISIBLE
                        return false
                    }
                    override fun onResourceReady(resource: android.graphics.drawable.Drawable?,
                        model: Any?, target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource?, isFirstResource: Boolean): Boolean {
                        imgLogo.visibility = View.VISIBLE
                        txtLogo.visibility = View.GONE
                        return false
                    }
                })
                .into(imgLogo)
        } else {
            imgLogo.visibility = View.GONE
            txtLogo.visibility = View.VISIBLE
        }
    }

    override fun getItemCount(): Int {
        return channels?.size ?: 0
    }

    // FIXED: Cari catId & chId yang benar langsung dari Playlist.cached berdasarkan object Channel
    // Bug lama: catId dari adapter position, tapi setelah merge/sort/favorite posisinya beda
    //  PlayerActivity ambil channel yang salah  gagal diputar
    override fun onClicked(ch: Channel, catId: Int, chId: Int) {
        val realCatId = Playlist.cached.categories.indexOfFirst { cat ->
            cat.channels?.any { it === ch } == true
        }.let { if (it == -1) catId else it }

        val realChId = Playlist.cached.categories.getOrNull(realCatId)
            ?.channels?.indexOfFirst { it === ch }
            ?: chId

        val intent = Intent(context, PlayerActivity::class.java)
        intent.putExtra(PlayData.VALUE, PlayData(realCatId, realChId))
        context.startActivity(intent)
    }

    override fun onLongClicked(ch: Channel, catId: Int, chId: Int): Boolean {
        val fav = Playlist.favorites
        if (isFav) {
            channels?.remove(ch)
            fav.remove(ch)
            if (itemCount != 0) {
                notifyItemRemoved(chId)
                notifyItemRangeChanged(0, itemCount)
            } else sendBroadcast(false)
            Toast.makeText(context,
                String.format(context.getString(R.string.removed_from_favorite), ch.name),
                Toast.LENGTH_SHORT).show()
        } else {
            val result = fav.insert(ch)
            if (result) sendBroadcast(true)
            val message = if (result)
                String.format(context.getString(R.string.added_into_favorite), ch.name)
            else
                String.format(context.getString(R.string.already_in_favorite), ch.name)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        fav.save()
        return true
    }

    private fun sendBroadcast(isInserted: Boolean) {
        val callback = if (isInserted) MainActivity.INSERT_FAVORITE else MainActivity.REMOVE_FAVORITE
        LocalBroadcastManager.getInstance(context).sendBroadcast(
            Intent(MainActivity.MAIN_CALLBACK)
                .putExtra(MainActivity.MAIN_CALLBACK, callback))
    }
}
