package com.animatv.player.extra

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.animatv.player.model.Category
import com.animatv.player.model.Channel
import com.animatv.player.model.DrmLicense
import com.animatv.player.model.Playlist

/**
 * Symphogear TV - channels.json converter
 * Mengkonversi format channels.json kustom ke format Playlist
 *
 * Format channels.json:
 * {
 *   "channels": [
 *     {
 *       "id": 100,
 *       "name": "RCTI",
 *       "cat": "nasional",
 *       "url": "https://...",
 *       "drm": false,
 *       "drmType": "Widevine",   // opsional: "Widevine" atau "ClearKey"
 *       "licUrl": "https://...", // opsional: URL lisensi Widevine, atau "keyid:key" untuk ClearKey
 *       "ua": "Mozilla/5.0 ..."  // opsional
 *     }
 *   ]
 * }
 */
object SymphogearJsonConverter {
    private const val TAG = "SymphogearConverter"

    private val CAT_NAMES = mapOf(
        "nasional"      to "Nasional",
        "berita"        to "Berita",
        "hiburan"       to "Hiburan",
        "olahraga"      to "Olahraga",
        "internasional" to "Internasional",
        "jepang"        to "Jepang",
        "vision"        to "Vision+",
        "indihome"      to "IndiHome",
        "custom"        to "Custom"
    )

    fun convert(jsonString: String): Playlist? {
        return try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            if (!root.has("channels")) return null

            val channelsArray: JsonArray = root.getAsJsonArray("channels")
            val playlist = Playlist()
            val categoryMap = LinkedHashMap<String, ArrayList<Channel>>()
            val drmMap = LinkedHashMap<String, String>()

            for (element in channelsArray) {
                val obj = element.asJsonObject
                val name    = obj.get("name")?.asString    ?: continue
                val url     = obj.get("url")?.asString     ?: continue
                val cat     = obj.get("cat")?.asString     ?: "nasional"
                val hasDrm  = obj.get("drm")?.asBoolean   ?: false
                val drmType = obj.get("drmType")?.asString ?: "ClearKey"
                val licUrl  = obj.get("licUrl")?.asString
                val ua      = obj.get("ua")?.asString
                val logo    = obj.get("logo")?.asString

                val channel = Channel()
                channel.name = name
                channel.streamUrl = if (!ua.isNullOrBlank()) "$url|user-agent=${ua}" else url
                channel.logo = logo

                if (hasDrm && !licUrl.isNullOrBlank()) {
                    val isWidevine = drmType.equals("Widevine", ignoreCase = true)
                    // "widevine_" prefix -> PlayerActivity pakai WIDEVINE_UUID
                    // "clearkey_" prefix -> PlayerActivity pakai CLEARKEY_UUID
                    val drmName = if (isWidevine) "widevine_${licUrl.hashCode()}"
                                  else             "clearkey_${licUrl.hashCode()}"
                    channel.drmName = drmName
                    if (!drmMap.containsKey(drmName)) drmMap[drmName] = licUrl
                }

                val catKey = cat.lowercase()
                if (!categoryMap.containsKey(catKey)) categoryMap[catKey] = ArrayList()
                categoryMap[catKey]?.add(channel)
            }

            val orderedCats = listOf("nasional","berita","hiburan","olahraga",
                "internasional","jepang","vision","indihome","custom")
            val categories = ArrayList<Category>()

            for (key in orderedCats) {
                if (categoryMap.containsKey(key)) {
                    val category = Category()
                    category.name = CAT_NAMES[key] ?: key.replaceFirstChar { it.uppercase() }
                    category.channels = categoryMap[key]
                    categories.add(category)
                }
            }
            for ((key, channels) in categoryMap) {
                if (!orderedCats.contains(key)) {
                    val category = Category()
                    category.name = key.replaceFirstChar { it.uppercase() }
                    category.channels = channels
                    categories.add(category)
                }
            }

            playlist.categories = categories

            val drmLicenses = ArrayList<DrmLicense>()
            for ((name, lic) in drmMap) {
                val drm = DrmLicense()
                drm.name = name
                drm.url = lic
                drmLicenses.add(drm)
            }
            playlist.drmLicenses = drmLicenses

            Log.d(TAG, "Converted ${channelsArray.size()} channels, ${categories.size} cats, ${drmLicenses.size} DRM")
            playlist

        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert Symphogear JSON: ${e.message}")
            null
        }
    }

    fun isSymphogearFormat(jsonString: String): Boolean {
        return try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            root.has("channels") && !root.has("categories")
        } catch (e: Exception) {
            false
        }
    }
}
