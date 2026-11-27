# LESSONS (each cost time once). One line each; `python3 brain/brain.py audit` flags lines whose files/symbols changed or vanished.
Add with: `python3 brain/brain.py learn "what went wrong and the fix" --ref path/or/symbol`
Format: `- [date] lesson — refs: path, symbol`
- [2026-11-27] Scrolling-capture screenshots: repeated blocks and clipped headers are stitching artifacts, not app bugs; check the code before "fixing" them — refs: video player/app/src/main/java/com/example/ui/components/AllFeaturesSheet.kt
- [2026-11-27] Android lists an app under Open with only if the manifest has ACTION_VIEW + video/* (+DEFAULT) on an exported activity; nothing in app code reveals it is missing — refs: video player/app/src/main/AndroidManifest.xml
- [2026-11-27] Kotlin block comments nest: never write video/* inside a KDoc or // block (it opens a nested comment and breaks the build) — refs: video player/app/src/main/java/com/example/ExternalVideoActivity.kt
