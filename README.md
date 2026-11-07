<div align="center">

# SubNexa

**Edit. Sync. Export.**

An offline-first Android subtitle editor. Open a local video, import an SRT or VTT file, edit and sync every line while it plays, then export a clean subtitle file.

<br/>

<a href="https://github.com/pabitra-senpai/SubNexa/releases/latest/download/SubNexa.apk">
  <img src="https://img.shields.io/badge/%E2%AC%87%20Download%20APK-E11D48?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" height="48" />
</a>

<br/><br/>

<a href="https://github.com/pabitra-senpai/SubNexa/releases"><img src="https://img.shields.io/badge/All%20releases-18181B?style=for-the-badge&logo=github&logoColor=white" alt="Releases" /></a>
<a href="https://github.com/pabitra-senpai/SubNexa/issues"><img src="https://img.shields.io/badge/Report%20a%20bug-18181B?style=for-the-badge&logo=githubactions&logoColor=white" alt="Issues" /></a>
<a href="https://github.com/pabitra-senpai/SubNexa/actions"><img src="https://img.shields.io/badge/Build%20status-18181B?style=for-the-badge&logo=githubactions&logoColor=white" alt="Actions" /></a>

<br/><br/>

![Platform](https://img.shields.io/badge/Android-8.0%2B-09090B?style=flat-square&logo=android&logoColor=E11D48)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-09090B?style=flat-square&logo=kotlin&logoColor=E11D48)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-09090B?style=flat-square&logo=jetpackcompose&logoColor=E11D48)
![Offline](https://img.shields.io/badge/Offline-100%25-09090B?style=flat-square)
![No ads](https://img.shields.io/badge/No%20ads-No%20tracking-09090B?style=flat-square)

[![Build APK](https://github.com/pabitra-senpai/SubNexa/actions/workflows/build-apk.yml/badge.svg)](https://github.com/pabitra-senpai/SubNexa/actions/workflows/build-apk.yml)

</div>

---

## What is SubNexa?

SubNexa is a **subtitle editor, not a video editor**. Your video is only a playback reference. SubNexa never modifies, converts or exports video, and it never uploads anything.

## Features

| | |
|---|---|
| **Play** | Open any local video through the Android file picker (MP4, MKV, MOV, WebM, depending on your device codecs). |
| **Import** | Load SRT or VTT files. Bengali, Hindi and English display correctly, with automatic encoding detection. |
| **Edit** | Change text and timestamps, add, delete, duplicate, split and merge entries while the video plays. |
| **Sync** | Shift every subtitle by +/- 0.5 s, 1 s or any custom offset, or fine-tune a single line. |
| **Style** | Live subtitle appearance: size, color, background and position. |
| **Export** | Save as SRT or VTT with your own filename and folder. |
| **Resume** | Recent projects remember your video, subtitles, position and settings. |

## How to download & install APK from GitHub

1. Click the **Download APK** button above or go to [Releases](https://github.com/pabitra-senpai/SubNexa/releases).
2. Download `SubNexa.apk` on your Android phone.
3. Tap the file to install (allow "Install from unknown sources" if prompted by Android).

## Build from source

The APK is built automatically by GitHub Actions on every push to `main`.

```bash
gradle assembleDebug
```

Requires JDK 17 and Gradle 9.3.1. The APK is created in `app/build/outputs/apk/debug/SubNexa.apk`.

To publish a numbered release, push a git tag like `v1.0.0`.

## Privacy

No account, no internet, zero permissions. SubNexa only reads the files you pick via the Storage Access Framework and writes only the subtitle file you export.
