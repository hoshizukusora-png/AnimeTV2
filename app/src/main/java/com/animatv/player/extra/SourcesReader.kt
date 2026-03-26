package com.animatv.player.extra

import com.animatv.player.extension.*
import com.animatv.player.model.Playlist
import com.animatv.player.model.Source
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import com.animatv.player.App.Companion.runOnUiThread
import com.animatv.player.extra.OfflineCache


class SourcesReader {
    private var sources: ArrayList<Source> = ArrayList()
    private var result: Result? = null

    interface Result {
        fun onProgress(source: String) {}
        fun onResponse(playlist: Playlist?) {}
        fun onError(source: String, error: String) {}
        fun onFinish() {}
    }

    fun set(sources: ArrayList<Source>?, result: Result): SourcesReader {
        sources?.let { this.sources.addAll(it) }
        this.result = result
        return this
    }

    fun process(useCache: Boolean) {
        // send onFinish when sources is empty
        if (sources.isEmpty()) {
            result?.onFinish(); return
        }

        // get first source & remove it
        val source = sources.first()
        sources.remove(source)

        // skip if not active
        if (!source.active) {
            process(useCache); return
        }

        // report progress
        result?.onProgress(source.path)

        // read local playlist
        if (!source.path.isLinkUrl()) {
            val playlist = PlaylistHelper().readFile(source.path.toFile())
            result?.onResponse(playlist)
            process(useCache); return
        }

        // get playlist from internet
        HttpClient(useCache)
            .create(source.path.toRequest())
            .enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        // Coba baca dari offline cache
                        val cached = OfflineCache.loadPlaylist()
                        if (cached != null) {
                            val playlist = cached.toPlaylist()
                            if (!playlist.isCategoriesEmpty()) {
                                result?.onResponse(playlist)
                                android.util.Log.d("SourcesReader", "Using offline cache")
                            } else {
                                result?.onError(source.path, e.message.toString())
                            }
                        } else {
                            result?.onError(source.path, e.message.toString())
                        }
                        process(useCache)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val content = response.body?.string()
                    runOnUiThread {
                        if (response.isSuccessful) {
                            if (!content.isNullOrBlank()) {
                                // Simpan ke offline cache saat berhasil download
                                OfflineCache.savePlaylist(content, source.path)
                                val playlist = content.toPlaylist()
                                if (!playlist.isCategoriesEmpty()) result?.onResponse(playlist)
                                else result?.onError(source.path, "parse error?")
                            } else result?.onError(source.path, "null content")
                        } else result?.onError(source.path, response.message)
                        // repeat until sources is empty
                        process(useCache)
                    }
                }
            })
    }
}