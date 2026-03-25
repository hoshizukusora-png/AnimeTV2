# 📺 AnimeTV - DASH + DRM

Aplikasi IPTV bertema Anime untuk Android TV & HP Android, dengan dukungan penuh **MPEG-DASH** dan **DRM** (ClearKey & Widevine).

---

## ✨ Fitur

- 🎌 **UI Anime tema dark navy** - persis seperti screenshot
- 📡 **DASH + DRM** - support ClearKey dan Widevine DRM
- 🔴 **Live Channels** - streaming langsung dari server SymphogearTV
- 📂 **Side Menu** - navigasi Home, Favorites, TV Guide, Settings, Exit
- ⭐ **Favorites** - simpan channel favorit
- 📅 **TV Guide** - jadwal program
- ⚙️ **Settings** - konfigurasi sumber playlist dan player
- 🔍 **Search** - cari channel dengan mudah
- 🎬 **Video Player** - kontrol playback lengkap + badge DASH+DRM
- 📴 **Offline Mode** - cache playlist untuk nonton offline

---

## 📡 Server Channel

Channel diambil secara otomatis dari:
```
https://raw.githubusercontent.com/aurorasekai15-hub/SymphogearTV-Native/main/channels.json
```

Format channel JSON:
```json
{
  "channels": [
    {
      "id": 1,
      "name": "Anime Live 1",
      "cat": "jepang",
      "url": "https://example.com/stream.mpd",
      "drm": true,
      "drmType": "ClearKey",
      "licUrl": "kid:key",
      "logo": "https://example.com/logo.png"
    }
  ]
}
```

---

## 🔐 Sistem DASH + DRM

| Tipe DRM | Dukungan |
|----------|----------|
| **ClearKey** | ✅ Full support |
| **Widevine L1** | ✅ Full support |
| **Widevine L3** | ✅ Full support |
| **HLS** | ✅ Full support |
| **MPEG-DASH** | ✅ Full support |

---

## 🚀 Build dengan GitHub Actions

1. **Fork** repository ini ke akun GitHub kamu
2. Push ke branch `main`
3. GitHub Actions otomatis build APK
4. Download APK dari tab **Actions → Artifacts** atau **Releases**

### Manual trigger:
- Buka tab **Actions**
- Pilih workflow **Build AnimeTV APK**
- Klik **Run workflow**

---

## 📱 Persyaratan

- Android **4.4+** (API 19+)
- Internet untuk streaming live
- Widevine L1/L3 untuk channel DRM

---

## ⚙️ Konfigurasi

Edit `app/src/main/res/values/strings.xml` untuk mengubah server:

```xml
<!-- URL server channel utama -->
<string name="iptv_playlist">https://raw.githubusercontent.com/.../channels.json</string>
```

---

## 🏗️ Build Manual

```bash
# Clone repository
git clone https://github.com/YOUR_USERNAME/AnimeTV.git
cd AnimeTV

# Build APK
./gradlew assembleRelease
```

---

## 📄 Lisensi

Open Source - bebas digunakan dan dimodifikasi.

---

*Made with ❤️ for Anime fans*
