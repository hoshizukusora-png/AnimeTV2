package com.animatv.player.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.animatv.player.BR
import com.animatv.player.R
import com.animatv.player.databinding.ItemCategoryBinding
import com.animatv.player.extension.*
import com.animatv.player.extra.Preferences
import com.animatv.player.model.Category
import com.animatv.player.model.Playlist
import kotlin.math.round

class CategoryAdapter(private val categories: ArrayList<Category>?) :
    RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {
    lateinit var context: Context

    // Index kategori yang sedang ditampilkan (-1 = tampilkan semua)
    private var selectedCatIndex: Int = 0

    // List yang benar-benar ditampilkan (1 kategori atau semua)
    private val displayList: ArrayList<Category>
        get() {
            return if (selectedCatIndex >= 0 && selectedCatIndex < (categories?.size ?: 0)) {
                val item = categories?.get(selectedCatIndex)
                if (item != null) arrayListOf(item) else categories ?: arrayListOf()
            } else {
                categories ?: arrayListOf()
            }
        }

    class ViewHolder(var itemCatBinding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(itemCatBinding.root) {
        fun bind(obj: Any?) {
            itemCatBinding.setVariable(BR.catModel, obj)
            itemCatBinding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val binding: ItemCategoryBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context), R.layout.item_category, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val category: Category? = displayList.getOrNull(position)
        val catIndexInFull = categories?.indexOf(category) ?: position
        val chCount = category?.channels?.size ?: 0

        val isFav = category.isFavorite() && catIndexInFull == 0
        viewHolder.itemCatBinding.chAdapter = ChannelAdapter(category?.channels, catIndexInFull, isFav)
        viewHolder.itemCatBinding.rvChannels.layoutManager =
            androidx.recyclerview.widget.GridLayoutManager(context, 5)

        try {
            viewHolder.itemCatBinding.txtCount?.text = chCount.toString()
        } catch (e: Exception) { }

        viewHolder.bind(category)
    }

    override fun getItemCount(): Int = displayList.size

    // Tampilkan hanya 1 kategori berdasarkan index di full list
    fun showCategory(index: Int) {
        selectedCatIndex = index
        notifyDataSetChanged()
    }

    fun clear() {
        val size = itemCount
        categories?.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun insertOrUpdateFavorite() {
        val fav = Playlist.favorites
        if (Preferences().sortFavorite) fav.sort()
        if (categories?.get(0)?.isFavorite() == false) {
            val lastCount = categories.size
            categories.addFavorite(fav.channels)
            notifyDataSetChanged()
        } else {
            categories?.get(0)?.channels = fav.channels
            notifyDataSetChanged()
        }
    }

    fun removeFavorite() {
        if (categories?.get(0)?.isFavorite() == true) {
            categories.removeAt(0)
            notifyDataSetChanged()
        }
    }
}
