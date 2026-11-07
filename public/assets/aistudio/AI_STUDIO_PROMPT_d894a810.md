# Google AI Studio prompt: build SubNexa

Paste everything below this line into Google AI Studio as one message.

---

You are a senior Android engineer. Build the COMPLETE, compilable source code of a native Android app named **SubNexa** (an offline subtitle editor) following the specification below exactly. I will copy your files into a GitHub repository and GitHub Actions will build the APK, so the project must compile on the first try with no manual fixes.

## OUTPUT RULES (very important)
1. Output EVERY file in FULL. Never output partial snippets, never write "rest unchanged" or "...".
2. For each file print a heading with the exact repository path (for example `app/src/main/java/com/subnexa/editor/MainActivity.kt`), followed by exactly one code block with the complete file content.
3. Order: Gradle and config files, manifest, resources, data layer, domain and parsers, ViewModels, UI (theme, components, screens), MainActivity.
4. If you run out of output space, stop after a COMPLETE file and end with the line `CONTINUE FROM: <next file path>`. I will reply "continue" and you resume from that file.
5. Do NOT output binary files: no `gradle-wrapper.jar`, no fonts, no PNG or WebP images. The launcher icon is vector XML (given at the end).
6. Do not write explanations between files. After the last file, print a plain file tree of everything you produced.
7. Do not invent features outside the specification. Do not create fake or non-functional controls.

## BUILD REQUIREMENTS (the CI depends on these)
- Repository root is the Gradle root. Files at root: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`. One module: `app`.
- Do NOT use the Gradle wrapper. CI runs `gradle assembleDebug` with Gradle 8.9 and JDK 17.
- Use plain version strings inside the `build.gradle.kts` files. No version catalog.
- Plugins and versions: Android Gradle Plugin 8.7.0, Kotlin 2.0.20 with the `org.jetbrains.kotlin.plugin.compose` plugin, and `org.jetbrains.kotlin.plugin.serialization` 2.0.20. Do NOT use KSP, kapt, Room, Hilt or Dagger.
- `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`, `applicationId = "com.subnexa.editor"`, `namespace = "com.subnexa.editor"`, versionCode 1, versionName "1.0.0". Java and Kotlin JVM target 17. Debug build only matters; `isMinifyEnabled = false` for release too.
- `gradle.properties` must contain `android.useAndroidX=true`, `kotlin.code.style=official`, and `org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8`.
- Dependencies (use these versions): Compose BOM 2024.09.00 (`ui`, `ui-graphics`, `ui-tooling-preview`, `material3`, `material-icons-extended`), `androidx.activity:activity-compose:1.9.2`, `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6`, `androidx.lifecycle:lifecycle-runtime-compose:2.8.6`, `androidx.navigation:navigation-compose:2.8.0`, `androidx.media3:media3-exoplayer:1.4.1`, `androidx.media3:media3-ui:1.4.1`, `androidx.datastore:datastore-preferences:1.1.1`, `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2`, `androidx.documentfile:documentfile:1.0.1`, `androidx.core:core-ktx:1.13.1`, `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1`.
- Base package: `com.subnexa.editor`. Use sub-packages such as `data`, `domain`, `ui`, `ui.theme`, `ui.home`, `ui.editor`, `ui.settings`.
- Manifest: no permissions at all. A single `MainActivity` (exported, launcher) with `android:windowSoftInputMode="adjustResize"` and `android:configChanges="orientation|screenSize|keyboardHidden|screenLayout|smallestScreenSize|uiMode"`. Use `enableEdgeToEdge()`. Application label "SubNexa". Icon: `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.
- Theme in `res/values/themes.xml` must extend `android:Theme.Material.NoActionBar` (framework theme, no Material Components dependency), with window background `#09090B` and dark status and navigation bar colors.
- Use only stable, well-known APIs. Double check every import, every referenced resource and every dependency before output. Avoid experimental APIs, or add the required `@OptIn` annotation.
- Player: ExoPlayer inside `AndroidView { PlayerView }` with `useController = false` and the subtitle view hidden. Disable the text track type through `trackSelectionParameters`. Release the player in `onCleared` or `DisposableEffect`.
- Subtitle overlay: pure Compose drawn over the video. Poll `player.currentPosition` about every 40 ms while playing and update on seek. Use binary search on the sorted list to find the active entry.
- Parsing and file IO run on `Dispatchers.IO`. Wrap risky IO in try/catch and surface errors as snackbars with plain messages.
- Keep code clean and readable with MVVM: ViewModels expose `StateFlow` UI state.

## SPECIFICATION
# SubNexa — Product and UI/UX Specification (v2)

SubNexa is an offline-first Android **subtitle editor**. It is NOT a video editor and NOT a general video player.
Tagline: **Edit. Sync. Export.**

Users open a local video (playback reference only), import an external SRT or VTT file, preview subtitles over the video, edit text and timestamps, synchronize, and export the edited subtitle file. The original video is never modified.

## 1. Out of scope (do not build)
Video export, rendering, encoding, conversion, trimming, cutting, merging, compression. No accounts, no server, no internet, no analytics, no ads, no dashboards, no statistics, no extra screens. ASS/SSA support is out of scope for v1.

## 2. Functional requirements

### Video
- Open through the system file picker (Storage Access Framework, `OpenDocument`, MIME `video/*`). Request no broad storage permissions. Persist read access with `takePersistableUriPermission`.
- Playback via AndroidX Media3 ExoPlayer. MP4, MKV, MOV and WebM wherever the device codecs allow. Show a clear error for unsupported files.
- Disable embedded text tracks in the player. All subtitles come from SubNexa's own Compose overlay.
- Controls: play/pause, seek bar, `current / duration`, small fullscreen toggle, small playback speed chip (cycles 0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x).
- Remember playback position per project.

### Subtitle import and parsing
- SRT and VTT, picked through SAF (`OpenDocument`; accept `*/*` because many devices label subtitles as `application/octet-stream`).
- Encoding detection: honor a UTF-8, UTF-16LE or UTF-16BE BOM. Otherwise try a strict UTF-8 decode; if it fails, fall back to Windows-1252. Bengali, Hindi and English must display correctly.
- Handle CRLF, LF and CR line endings, blank-line variations, missing or wrong index numbers, and timestamps with either comma or dot milliseconds. Skip malformed blocks instead of failing the whole file, and report how many were skipped.
- VTT: ignore the `WEBVTT` header, `NOTE`, `STYLE` and `REGION` blocks, cue identifiers and cue settings after the end timestamp. Accept `MM:SS.mmm` as well as `HH:MM:SS.mmm`.
- Parse off the main thread. The list must stay smooth with 5,000+ entries (use `LazyColumn` with stable keys).
- Keep raw text in the data model. The overlay strips simple tags such as `<i>`, `<b>`, `<u>`, `<font>` and `<c.x>` for display.

### Subtitle model
`SubtitleEntry(id: Long, startMs: Long, endMs: Long, text: String)`. The list is always sorted by `startMs`. Display numbers are the 1-based list index and are renumbered automatically.

### Editing
- Edit text, start and end. Add. Delete (with Undo snackbar). Duplicate. Split at the playhead. Merge with next entry.
- Duplicate, Split and Merge live in a long-press menu on a row. There is no manual drag-reordering; changing the time re-sorts the list.
- Validation: start >= 0; end > start; timestamps must match `HH:MM:SS.mmm`. Block saving with an inline error. Overlap with a neighbor shows a non-blocking warning.
- Preview: edits apply to the overlay immediately.

### Synchronization
- Global: shift every entry by a signed offset (quick chips -1s, -0.5s, +0.5s, +1s plus a custom seconds field accepting decimals). Clamp start at 0 and keep end > start.
- Individual: edit one entry's start and end, with "Set to now" actions.

### Subtitle appearance
Font size, text color, background (None, Dim, Solid) and vertical position (Top, Middle, Bottom). Live preview in the sheet. Defaults live in Settings. Per-project overrides persist.

### Export
- SRT: numbered from 1 with no gaps, timestamps `HH:MM:SS,mmm`, blank line between entries.
- VTT: `WEBVTT` header, blank line, timestamps `HH:MM:SS.mmm`.
- UTF-8 without BOM. Custom filename, extension added automatically. Save folder chosen with `OpenDocumentTree` and persisted. If the file exists, add a numeric suffix instead of overwriting.
- Exported files can be re-imported.

### Projects and persistence
- A project stores: video URI and display name, subtitle source URI and name (if any), subtitle entries, last playback position, last selected entry, appearance overrides, last edited time.
- Recent Projects card: video name, subtitle count, last edited time ("Edited 2 hours ago"). Swipe left to delete the saved project only, with an Undo snackbar. Never delete user files.
- Autosave with a short debounce (about 1 second) and when leaving the editor or the app goes to background.
- Store projects as JSON files in app-private storage (index file plus one file per project). Store app settings in DataStore Preferences. Do not use Room, KSP or kapt.

## 3. Design system
- Background `#09090B`, surface `#18181B`, border `#27272A`, input border `#3F3F46`, accent `#E11D48`, primary text `#FAFAFA`, secondary text `#A1A1AA`, danger fill `#7F1D1D`.
- Always dark (AMOLED). A Theme setting exists but v1 ships Dark only, shown as a read-only row value.
- Font: system sans (Roboto). Timestamps use monospace. No binary font files.
- Shapes: 12dp cards, 10dp buttons, 20dp top corners on sheets, 0.5dp hairline borders, no shadows.
- Icons: minimal outline icons (Material Icons Extended outlined set is fine).
- Accent discipline: at most one red-filled button per view; red also marks the active subtitle row and seek progress.
- Portrait first. Support small screens. Edge-to-edge with correct insets.

## 4. Navigation
Home -> Editor -> (bottom sheets: Edit, Global Sync, Export, Appearance). Home -> Settings. Back always returns to Home from the Editor. Use Navigation Compose with routes `home`, `editor/{projectId}`, `settings`.

Home -> Open video: pick a video, create a project, open the Editor.
Home -> Import subtitle: pick a subtitle file, then pick its video, create a project, open the Editor.
Home -> Recent project: open the Editor restored to its saved position.

## 5. Screens

### Home
SubNexa wordmark and tagline "Edit. Sync. Export.", Settings icon top right. Two buttons: **Open video** (the single red button) and **Import subtitle**. "Recent projects" list of cards. No stats, dashboards or categories. Empty recent list shows a short invitation line.

### Editor (most important)
Top to bottom:
1. Compact top bar: back arrow and video name.
2. Video player (16:9). Overlay: subtitle text. Top right on the video: Subtitle appearance icon and fullscreen icon. Bottom of the video: progress bar, play/pause, `00:12:04.250 / 00:24:10.000`, speed chip.
3. Four equal actions in a row: Import, Add, Sync, Export.
4. Count header ("128 subtitles") and a scrollable list. Each row: number, text, `start -> end` (monospace), edit icon, delete icon. The row at the playhead has a faint red tint and a red left border. Tapping a row seeks to its start. Auto-scroll to the active row during playback unless the user is scrolling.
5. Fullscreen: only a secondary mode. Player fills the screen with immersive system bars and landscape; back or the toggle exits to the same editor state.

### Edit sheet (bottom sheet)
Title "Edit subtitle N" (or "New subtitle"). Multiline text field. Start field with a "Set to now" button. End field with a "Set to now" button. Cancel and Save (Save is the single red button). Timestamps shown as `HH:MM:SS.mmm`.
- Opening the sheet pauses playback.
- Add opens this sheet directly. Start defaults to the current playback position. End defaults to start + 2000 ms (editable).
- "Set to now" copies the live player position into the field and does not resume playback.
- Keyboard open: the player shrinks to a mini player (about 62dp tall) pinned at the top with play/pause, timestamp and a one-line subtitle preview. The player is never fully hidden. The sheet must remain scrollable and the Save button reachable.
- After Save or Cancel, playback stays paused; after Save the list scrolls to the saved row.

### Global Sync sheet
Quick chips -1s, -0.5s, +0.5s, +1s that adjust the custom offset field, a custom offset field (seconds, signed decimal), helper line "Shifts all N subtitles. Start never goes below 00:00:00.000.", Cancel and Apply (red). Apply commits and the overlay updates immediately.

### Export sheet
Filename (no extension), SRT / VTT segmented control, save location row (opens folder picker), Export (red). Success shows a short snackbar "Saved as Episode 04.srt".

### Appearance sheet
Live preview strip, font size slider, text color swatches (white, yellow, cyan, green), background segmented control (None, Dim, Solid), position segmented control (Top, Middle, Bottom). Changes apply live and persist to the project.

### Empty state (no subtitles)
Icon, "No subtitles yet", helper line "Pause the video where a line starts, then add it.", and an **Add your first subtitle** button that opens the Edit sheet. Replaces the list area; the four actions stay visible.

### Settings
Theme (Dark), Default subtitle appearance, Default export format (SRT / VTT), About SubNexa, App version. Nothing else.

## 6. Timestamp rules
- Editing UI: `HH:MM:SS.mmm`.
- SRT export: `HH:MM:SS,mmm`.
- VTT export: `HH:MM:SS.mmm`.
- Import accepts both separators and normalizes to integer milliseconds.

## 7. App icon
Two rounded horizontal caption bars (long above short) plus a thin crimson cursor after the short bar. Three shapes. Primary: `#09090B` background, `#FAFAFA` bars, `#E11D48` cursor. Adaptive: solid `#09090B` background layer, foreground vector (shapes inside the 66dp safe zone of a 108dp canvas), and a monochrome layer using the same three shapes. The exact XML is included in the prompt and in `design-assets/icon/`.

## ICON FILES (create these files exactly as given, at these paths)

Path `app/src/main/res/drawable/ic_launcher_foreground.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FAFAFA"
        android:pathData="M32.5,40 H75.5 A4.5,4.5 0 0 1 75.5,49 H32.5 A4.5,4.5 0 0 1 32.5,40 Z" />
    <path
        android:fillColor="#FAFAFA"
        android:pathData="M32.5,55 H57.5 A4.5,4.5 0 0 1 57.5,64 H32.5 A4.5,4.5 0 0 1 32.5,55 Z" />
    <path
        android:fillColor="#E11D48"
        android:pathData="M69,50 A2,2 0 0 1 71,52 V67 A2,2 0 0 1 69,69 A2,2 0 0 1 67,67 V52 A2,2 0 0 1 69,50 Z" />
</vector>
```

Path `app/src/main/res/drawable/ic_launcher_monochrome.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M32.5,40 H75.5 A4.5,4.5 0 0 1 75.5,49 H32.5 A4.5,4.5 0 0 1 32.5,40 Z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M32.5,55 H57.5 A4.5,4.5 0 0 1 57.5,64 H32.5 A4.5,4.5 0 0 1 32.5,55 Z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M69,50 A2,2 0 0 1 71,52 V67 A2,2 0 0 1 69,69 A2,2 0 0 1 67,67 V52 A2,2 0 0 1 69,50 Z" />
</vector>
```

Path `app/src/main/res/values/ic_launcher_background.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#09090B</color>
</resources>
```

Path `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` with the same content as `ic_launcher.xml`.

## FINAL CHECKLIST (verify silently before you answer)
- Every feature in section 2 and every screen in section 5 is implemented and wired to real behavior.
- Project compiles with `gradle assembleDebug`: no missing imports, resources, strings, dependencies or files.
- No permissions, no network code, no video modification, no video export.
- SRT export uses comma milliseconds; VTT export uses dot milliseconds; the editor shows `HH:MM:SS.mmm`.
- Start now. Begin with the Gradle files.
