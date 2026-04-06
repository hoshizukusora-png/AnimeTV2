package com.animatv.player.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
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
import com.animatv.player.model.PlayData
import com.animatv.player.model.Playlist

class SearchAdapter (val channels: ArrayList<Channel>, private val listdata: ArrayList<PlayData>) :
    RecyclerView.Adapter<SearchAdapter.ViewHolder>(), Filterable, ChannelClickListener {
    lateinit var context: Context
    var listChannel = ArrayList<Channel>()
    var listPlayData = ArrayList<PlayData>()

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
            LayoutInflater.from(context),R.layout.item_channel,parent,false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val channel = listChannel[position]
        val playdata = listPlayData[position]

        viewHolder.bind(channel)
        viewHolder.itemChBinding.catId = playdata.catId
        viewHolder.itemChBinding.chId = playdata.chId
        viewHolder.itemChBinding.clickListener = this
        viewHolder.itemChBinding.btnPlay.setOnFocusChangeListener { v, hasFocus ->
            v.startAnimation(hasFocus)
            if (hasFocus) {
                v.setBackgroundResource(R.drawable.channel_card_focused_bg)
            } else {
                v.setBackgroundResource(R.drawable.channel_card_bg)
            }
        }
        viewHolder.itemChBinding.btnPlay.setOnKeyListener { v, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                 keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                 keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                this.onClicked(channel, playdata.catId, playdata.chId)
                true
            } else false
        }
    }

    override fun getItemCount(): Int {
        return listChannel.size
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence): FilterResults {
                if (constraint.isEmpty()) {
                    listChannel = channels
                    listPlayData = listdata
                } else {
                    val listCh = ArrayList<Channel>()
                    val listDt = ArrayList<PlayData>()
                    for (id in channels.indices) {
                        if (channels[id].name?.contains(constraint.toString(), true) == true) {
                            listCh.add(channels[id])
                            listDt.add(listdata[id])
                        }
                    }
                    listChannel = listCh
                    listPlayData = listDt
                }
                return FilterResults().apply {
                    values = listChannel
                    count = listChannel.size
                }
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun publishResults(charSequence: CharSequence, filterResults: FilterResults) {
                listChannel = objToArrayList(filterResults.values)
                notifyDataSetChanged()
            }

            private fun objToArrayList(obj: Any?): ArrayList<Channel> {
                return if (obj is ArrayList<*>) ArrayList(obj.filterIsInstance<Channel>())
                    else ArrayList()
            }
        }
    }

    override fun onClicked(ch: Channel, catId: Int, chId: Int) {
        val intent = Intent(context, PlayerActivity::class.java)
        intent.putExtra(PlayData.VALUE, PlayData(catId, chId))
        context.startActivity(intent)
    }

    override fun onLongClicked(ch: Channel, catId: Int, chId: Int): Boolean {
        val fav = Playlist.favorites
        val result = fav.insert(ch)
        if (result) {
            LocalBroadcastManager.getInstance(context).sendBroadcast(
                Intent(MainActivity.MAIN_CALLBACK)
                    .putExtra(MainActivity.MAIN_CALLBACK, MainActivity.INSERT_FAVORITE))
            fav.save()
        }

        val message = if (result) String.format(context.getString(R.string.added_into_favorite), ch.name)
        else String.format(context.getString(R.string.already_in_favorite), ch.name)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

        return true
    }
}