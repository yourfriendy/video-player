# LESSONS (each cost time once). One line each; `python3 brain/brain.py audit` flags lines whose files/symbols changed or vanished.
Add with: `python3 brain/brain.py learn "what went wrong and the fix" --ref path/or/symbol`
Format: `- [date] lesson — refs: path, symbol`
- [2026-11-27] Scrolling-capture screenshots: repeated blocks and clipped headers are stitching artifacts, not app bugs; check the code before "fixing" them — refs: video player/app/src/main/java/com/example/ui/components/AllFeaturesSheet.kt
- [2026-11-27] Design lint bans off-scale fontSize/radius and the words Retry/Any link in ui/**; use Ds.Type tokens and radius 4/8/12/16/20/24/28 — refs: video player/app/src/test/java/com/example/ui/DesignTokensLintTest.kt
