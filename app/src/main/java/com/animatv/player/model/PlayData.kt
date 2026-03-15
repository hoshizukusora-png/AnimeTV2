package com.animatv.player.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class PlayData(val catId: Int, val chId: Int) : Parcelable {
    companion object {
        const val VALUE: String = "PLAYER_PARCELABLE"
    }
}