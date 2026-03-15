package com.animatv.player.extension

import com.animatv.player.App
import com.animatv.player.R
import com.animatv.player.model.Category
import com.animatv.player.model.Channel

fun Category?.isFavorite(): Boolean {
    return this?.name == App.context.getString(R.string.favorite_channel)
}

fun ArrayList<Category>?.addFavorite(channels: ArrayList<Channel>) {
    val title = App.context.getString(R.string.favorite_channel)
    this?.add(0, Category().apply {
        this.name = title
        this.channels = channels
    })
}