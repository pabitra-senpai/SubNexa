# Follow-up prompts for Google AI Studio

Use these after the main prompt (`AI_STUDIO_PROMPT.md`).

## 1. When the answer stops in the middle
```
continue
```

## 2. After all files are generated (always do this)
```
List every file you generated as a tree. Then audit the whole project against Gradle 8.9, AGP 8.7.0 and Kotlin 2.0.20: check every import, every referenced resource (strings, drawables, themes, mipmaps), every dependency in app/build.gradle.kts, and the AndroidManifest. Fix every problem you find. Output only the files you changed, each one complete, with its exact path.
```

## 3. When GitHub Actions fails
Open the failed run, copy the red error lines (about 30 lines around "error:" or "FAILED"), then send:
```
The GitHub Actions build failed with this log:

<paste the log here>

Find the root cause. Output ONLY the corrected files, each one complete, with its exact path. Do not change anything unrelated.
```

## 4. When the app installs but something is wrong
```
SubNexa installs, but this is wrong: <describe what you did and what happened>.
Expected behavior: <what should happen>.
Find the cause and output ONLY the corrected files, each one complete, with exact paths.
```

## 5. Feature check
```
Verify the code against docs section 2 and section 5 of the SubNexa specification. For each feature and each screen, answer Implemented or Missing. Then implement everything that is missing and output only the new or changed files, each one complete.
```

## 6. UI polish
```
Compare the UI code with the SubNexa design system: background #09090B, surface #18181B, accent #E11D48 used sparingly (one red-filled button per view), hairline 0.5dp borders, 12dp cards, 20dp sheet corners. Fix every mismatch and output only the changed files, each one complete.
```
