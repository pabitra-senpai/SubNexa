<div align="center">

<img src="docs/icon.png" alt="SubNexa" width="96" />

# SubNexa

**Edit. Sync. Export.**

A simple, offline subtitle editor for Android.

[![Download APK](https://img.shields.io/badge/%E2%AC%87%20Download%20APK-E11D48?style=for-the-badge&logo=android&logoColor=white)](https://github.com/pabitra-senpai/SubNexa/releases/latest/download/SubNexa.apk)

![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-09090B?logo=android&logoColor=E11D48)
![Works Offline](https://img.shields.io/badge/Works-Offline-09090B)
![No ads](https://img.shields.io/badge/No%20ads-No%20tracking-09090B)

</div>

---

## What it does

Open a video, load a subtitle file, fix it while the video plays, and export a clean copy.

SubNexa edits **subtitles only**. Your video is just a reference and is never changed.

## Features

- ▶️ **Play** local videos with speed control and fullscreen
- 📥 **Import** SRT and VTT files, with Bengali, Hindi and English shown correctly
- ✏️ **Edit** text and timing, and add, delete, duplicate, split or merge lines
- ⏱️ **Sync** all subtitles earlier or later in one tap
- 🎨 **Style** the size, color, background and position of subtitles live
- 💾 **Export** as SRT or VTT to any folder you choose
- 🔁 **Resume** where you left off, with autosave

## Install

1. Tap **Download APK** above (or open [Releases](https://github.com/pabitra-senpai/SubNexa/releases/latest)).
2. Open the downloaded `SubNexa.apk` on your phone.
3. Allow installing from this source if Android asks.

Works on Android 8.0 and newer.

## Privacy

No account. Works offline. No ads. No tracking. The app asks for no personal data and only opens the files you pick. The internet is used only when you tap "Check for Updates", and an update is installed only after you confirm it in Android.

## Build it yourself

You need JDK 17 and the Android SDK. The Gradle wrapper downloads Gradle 9.3.1 automatically.

```bash
git clone https://github.com/pabitra-senpai/SubNexa.git
cd SubNexa
./gradlew assembleDebug      # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest  # run unit tests
```

Every push to `main` is built, tested and released automatically by GitHub Actions.

## Release signing

Release APKs are signed with your own private key, never a debug key. Keep this key safe: if you lose it, existing users cannot update the app without uninstalling it first.

1. Create a keystore once (needs JDK):

   ```bash
   keytool -genkeypair -v -keystore release.jks -alias upload \
     -keyalg RSA -keysize 4096 -validity 10000
   base64 -w 0 release.jks > release.keystore.base64
   ```

2. In GitHub go to **Settings > Secrets and variables > Actions** and add:

   | Secret | Value |
   | --- | --- |
   | `KEYSTORE_BASE64` | contents of `release.keystore.base64` |
   | `STORE_PASSWORD` | keystore password |
   | `KEY_PASSWORD` | key password |
   | `KEY_ALIAS` | optional, defaults to `upload` |

3. Back up `release.jks` and the passwords somewhere private. Never commit them (they are in `.gitignore`).

For a local signed build, put `release.jks` in the project root and set `STORE_PASSWORD` and `KEY_PASSWORD`, then run `./gradlew assembleRelease`.

## Versioning

CI sets the version automatically. `versionCode` is the build time in minutes since the Unix epoch, so it always increases. `versionName` is `1.0.<run number>` for `main` builds, or the tag name for `v*` tags (for example `v1.2.0` gives `1.2.0`).

## Built with

Kotlin · Jetpack Compose · Material 3 · Media3 ExoPlayer

## License

Released under the [MIT License](LICENSE). Bundled fonts are listed in [THIRD_PARTY_FONTS.md](THIRD_PARTY_FONTS.md).

## Feedback

Found a bug or have an idea? [Open an issue](https://github.com/pabitra-senpai/SubNexa/issues).
