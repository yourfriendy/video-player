# STRUCTURE.md - every file and what it does

One line per file: ``- `path` — what it does``. `python3 brain/brain.py structure` fails if a file is missing here.
Rule: add/rename/move/delete a file => update this file in the same change. Do not read it whole: `brain.py map DIR`.

### Added 2026-11-21 (replace every "describe me")
- `.github/workflows/build-apk.yml` — The pipeline: tests, app and engine build in parallel, then one publish to GitHub Releases (nightly / latest / build-<branch>); fails if the APK is not in its release.
- `.github/workflows/sign-registration-apk.yml` — Google's Android Developer Console asks you to prove you own the signing key by signing a dummy APK it gives you.
- `.gitignore` — .gitignore (describe me)
- `README.md` — ✨ Why OffScroll?
- `docs/AI_START_HERE.md` — AI start here (repo)
- `docs/PLAN_TEMPLATE.md` — PLAN: <name of the work>   (copy this file to docs/<NAME>_PLAN.md)
- `docs/UX-FIX-PLAN.md` — OffScroll UX and engineering fix plan
- `docs/VERIFY-WITH-GOOGLE.md` — Getting rid of the "unverified app" and scan prompts
- `video player/.env.example` — GEMINI_API_KEY: Required for Gemini AI API calls.
- `video player/.gitignore` — .gitignore (describe me)
- `video player/README.md` — OffScroll (Android project)
- `video player/app/.gitignore` — .gitignore (describe me)
- `video player/app/build.gradle.kts` — CI sets GITHUB_RUN_NUMBER so every build is a valid in-place update of the previous one
- `video player/app/proguard-rules.pro` — Add project specific ProGuard rules here.
- `video player/app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt` — Context of the app under test.
- `video player/app/src/main/AndroidManifest.xml` — Android 11+: lets OffScroll see whether the engine app is installed
- `video player/app/src/main/baseline-prof.txt` — Baseline profile (hand-written start-up rules).
- `video player/app/src/main/java/com/example/IgLoginActivity.kt` — IgLoginActivity (describe me)
- `video player/app/src/main/java/com/example/MainActivity.kt` — MainActivity (describe me)
- `video player/app/src/main/java/com/example/OffScrollApp.kt` — OffScrollApp (describe me)
- `video player/app/src/main/java/com/example/ShareDownloadActivity.kt` — Only on a fresh start; rotation must not start the same download again
- `video player/app/src/main/java/com/example/data/Diagnostics.kt` — Diagnostics (describe me)
- `video player/app/src/main/java/com/example/data/DiagnosticsReport.kt` — Builds the text behind Settings > Copy diagnostics. Every lookup is guarded: a report must never crash.
- `video player/app/src/main/java/com/example/data/download/AlertHealth.kt` — The one plain-language answer to "are my alerts working?".
- `video player/app/src/main/java/com/example/data/download/IgPageCapture.kt` — IgPageCapture (describe me)
- `video player/app/src/main/java/com/example/data/download/IgWebFetcher.kt` — IgWebFetcher (describe me)
- `video player/app/src/main/java/com/example/data/download/InstagramDownloader.kt` — An error with a message that is safe to show to the user as-is.
- `video player/app/src/main/java/com/example/data/download/InstagramParser.kt` — Finds the first Instagram link in arbitrary text (a pasted link or a "Share" message).
- `video player/app/src/main/java/com/example/data/download/InstagramProfileModels.kt` — A link from a profile's bio ("link in bio").
- `video player/app/src/main/java/com/example/data/download/InstagramStoryService.kt` — The saved Instagram session is missing or no longer valid: the user has to sign in again.
- `video player/app/src/main/java/com/example/data/download/PeopleStore.kt` — Someone the user wants quick access to (and optionally alerts for).
- `video player/app/src/main/java/com/example/data/download/StoryAlerts.kt` — StoryAlerts (describe me)
- `video player/app/src/main/java/com/example/data/download/StoryWatchService.kt` — StoryWatchService (describe me)
- `video player/app/src/main/java/com/example/data/local/AppDatabase.kt` — AppDatabase (describe me)
- `video player/app/src/main/java/com/example/data/local/SettingsManager.kt` — SettingsManager (describe me)
- `video player/app/src/main/java/com/example/data/local/dao/FavoriteDao.kt` — FavoriteDao (describe me)
- `video player/app/src/main/java/com/example/data/local/dao/PlaylistDao.kt` — PlaylistDao (describe me)
- `video player/app/src/main/java/com/example/data/local/dao/QueueDao.kt` — QueueDao (describe me)
- `video player/app/src/main/java/com/example/data/local/dao/VideoBookmarkDao.kt` — VideoBookmarkDao (describe me)
- `video player/app/src/main/java/com/example/data/local/dao/WatchHistoryDao.kt` — WatchHistoryDao (describe me)
- `video player/app/src/main/java/com/example/data/local/dao/WatchLaterDao.kt` — WatchLaterDao (describe me)
- `video player/app/src/main/java/com/example/data/local/entity/FavoriteVideo.kt` — FavoriteVideo (describe me)
- `video player/app/src/main/java/com/example/data/local/entity/PlaylistEntity.kt` — PlaylistEntity (describe me)
- `video player/app/src/main/java/com/example/data/local/entity/PlaylistVideoEntity.kt` — PlaylistVideoEntity (describe me)
- `video player/app/src/main/java/com/example/data/local/entity/QueueItemEntity.kt` — QueueItemEntity (describe me)
- `video player/app/src/main/java/com/example/data/local/entity/VideoBookmarkEntity.kt` — VideoBookmarkEntity (describe me)
- `video player/app/src/main/java/com/example/data/local/entity/WatchHistory.kt` — WatchHistory (describe me)
- `video player/app/src/main/java/com/example/data/local/entity/WatchLaterEntity.kt` — WatchLaterEntity (describe me)
- `video player/app/src/main/java/com/example/data/model/LocalVideo.kt` — LocalVideo (describe me)
- `video player/app/src/main/java/com/example/data/repository/MetadataExtractor.kt` — MetadataExtractor (describe me)
- `video player/app/src/main/java/com/example/data/repository/VideoRepository.kt` — VideoRepository (describe me)
- `video player/app/src/main/java/com/example/data/universal/UniversalDownloader.kt` — An error whose message is safe to show as-is. [suggestUpdate]: updating the engine may fix it.
- `video player/app/src/main/java/com/example/data/universal/UniversalModels.kt` — Everything in this file is plain Kotlin (no Android, no yt-dlp classes) so it can be unit tested.
- `video player/app/src/main/java/com/example/data/universal/engine/EngineClient.kt` — EngineClient (describe me)
- `video player/app/src/main/java/com/example/data/universal/engine/EngineInfoParser.kt` — Turns the engine's lookup answer (JSON) into the [MediaInfo] the screens show. Plain Kotlin + org.json, so it is unit tested.
- `video player/app/src/main/java/com/example/data/universal/engine/EngineManager.kt` — EngineManager (describe me)
- `video player/app/src/main/java/com/example/data/universal/engine/EngineProtocol.kt` — app -> engine (Message.what). Message.arg1 is a request id the engine echoes back.
- `video player/app/src/main/java/com/example/data/update/AppUpdateService.kt` — AppUpdateService (describe me)
- `video player/app/src/main/java/com/example/data/update/UpdateModels.kt` — One published build on GitHub (the repo's "latest" release).
- `video player/app/src/main/java/com/example/service/DownloadKeepAliveService.kt` — DownloadKeepAliveService (describe me)
- `video player/app/src/main/java/com/example/service/ShareDownloads.kt` — ShareDownloads (describe me)
- `video player/app/src/main/java/com/example/ui/components/ActionRail.kt` — ActionRail (describe me)
- `video player/app/src/main/java/com/example/ui/components/AddToPlaylistSheet.kt` — AddToPlaylistSheet (describe me)
- `video player/app/src/main/java/com/example/ui/components/AlertsScreen.kt` — AlertsScreen (describe me)
- `video player/app/src/main/java/com/example/ui/components/AllFeaturesSheet.kt` — AllFeaturesSheet (describe me)
- `video player/app/src/main/java/com/example/ui/components/AppUpdateUi.kt` — AppUpdateUi (describe me)
- `video player/app/src/main/java/com/example/ui/components/DeleteConfirmDialog.kt` — DeleteConfirmDialog (describe me)
- `video player/app/src/main/java/com/example/ui/components/DevCredit.kt` — DevCredit (describe me)
- `video player/app/src/main/java/com/example/ui/components/DownloaderKit.kt` — DownloaderKit (describe me)
- `video player/app/src/main/java/com/example/ui/components/DownloaderScreen.kt` — DownloaderScreen (describe me)
- `video player/app/src/main/java/com/example/ui/components/DownloaderSheet.kt` — DownloaderSheet (describe me)
- `video player/app/src/main/java/com/example/ui/components/EmptyFeedView.kt` — EmptyFeedView (describe me)
- `video player/app/src/main/java/com/example/ui/components/FeedTopBar.kt` — FeedTopBar (describe me)
- `video player/app/src/main/java/com/example/ui/components/Feedback.kt` — Feedback (describe me)
- `video player/app/src/main/java/com/example/ui/components/GestureTour.kt` — GestureTour (describe me)
- `video player/app/src/main/java/com/example/ui/components/LoadingKit.kt` — LoadingKit (describe me)
- `video player/app/src/main/java/com/example/ui/components/MetadataBottomSheet.kt` — MetadataBottomSheet (describe me)
- `video player/app/src/main/java/com/example/ui/components/PermissionRequestScreen.kt` — PermissionRequestScreen (describe me)
- `video player/app/src/main/java/com/example/ui/components/PlayerPool.kt` — PlayerPool (describe me)
- `video player/app/src/main/java/com/example/ui/components/ProfileScreen.kt` — ProfileScreen (describe me)
- `video player/app/src/main/java/com/example/ui/components/ProfileViewer.kt` — ProfileViewer (describe me)
- `video player/app/src/main/java/com/example/ui/components/ReelPlayerItem.kt` — ReelPlayerItem (describe me)
- `video player/app/src/main/java/com/example/ui/components/SearchScreen.kt` — SearchScreen (describe me)
- `video player/app/src/main/java/com/example/ui/components/SettingsScreen.kt` — SettingsScreen (describe me)
- `video player/app/src/main/java/com/example/ui/components/SharePopup.kt` — SharePopup (describe me)
- `video player/app/src/main/java/com/example/ui/components/SideGestureControls.kt` — SideGestureControls (describe me)
- `video player/app/src/main/java/com/example/ui/components/UniversalDownloaderUi.kt` — UniversalDownloaderUi (describe me)
- `video player/app/src/main/java/com/example/ui/components/VideoFeedScreen.kt` — VideoFeedScreen (describe me)
- `video player/app/src/main/java/com/example/ui/components/VideoGridView.kt` — VideoGridView (describe me)
- `video player/app/src/main/java/com/example/ui/components/VideoInfoOverlay.kt` — VideoInfoOverlay (describe me)
- `video player/app/src/main/java/com/example/ui/components/VideoOptionsSheet.kt` — VideoOptionsSheet (describe me)
- `video player/app/src/main/java/com/example/ui/model/DownloadStatus.kt` — Where the Instagram downloader currently is. Shown by the downloader sheet.
- `video player/app/src/main/java/com/example/ui/model/FeedFilter.kt` — Half-open [minMs, maxMs) so neighbouring presets never overlap.
- `video player/app/src/main/java/com/example/ui/model/SearchOptions.kt` — Length buckets offered as quick filters on the search page.
- `video player/app/src/main/java/com/example/ui/model/SortOption.kt` — SortOption (describe me)
- `video player/app/src/main/java/com/example/ui/model/StoryModels.kt` — Inside the Stories tab: a person's stories, or their reposts.
- `video player/app/src/main/java/com/example/ui/model/VideoDisplayMode.kt` — VideoDisplayMode (describe me)
- `video player/app/src/main/java/com/example/ui/navigation/AppNavigation.kt` — AppNavigation (describe me)
- `video player/app/src/main/java/com/example/ui/theme/Color.kt` — Frosted Glass Slate-950 Theme Colors
- `video player/app/src/main/java/com/example/ui/theme/DesignSystem.kt` — DesignSystem (describe me)
- `video player/app/src/main/java/com/example/ui/theme/Motion.kt` — Motion (describe me)
- `video player/app/src/main/java/com/example/ui/theme/Theme.kt` — Theme (describe me)
- `video player/app/src/main/java/com/example/ui/theme/Type.kt` — Set of Material typography styles to start with
- `video player/app/src/main/java/com/example/ui/viewmodel/AppUpdateViewModel.kt` — AppUpdateViewModel (describe me)
- `video player/app/src/main/java/com/example/ui/viewmodel/ProfileController.kt` — ProfileController (describe me)
- `video player/app/src/main/java/com/example/ui/viewmodel/UniversalDownloadViewModel.kt` — UniversalDownloadViewModel (describe me)
- `video player/app/src/main/java/com/example/ui/viewmodel/VideoFeedDownloaderActions.kt` — VideoFeedDownloaderActions (describe me)
- `video player/app/src/main/java/com/example/ui/viewmodel/VideoFeedLibraryActions.kt` — VideoFeedLibraryActions (describe me)
- `video player/app/src/main/java/com/example/ui/viewmodel/VideoFeedViewModel.kt` — VideoFeedViewModel (describe me)
- `video player/app/src/main/res/drawable/ic_stat_offscroll.xml` — Status-bar icon: notifications need a flat, single-colour silhouette (a full-colour launcher icon turns into a white blob)
- `video player/app/src/main/res/layout/reel_player_view.xml` — reel player view (describe me)
- `video player/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — ic launcher (describe me)
- `video player/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` — ic launcher round (describe me)
- `video player/app/src/main/res/values-hi/strings.xml` — Common words only. Everything not listed falls back to English.
- `video player/app/src/main/res/values-te/strings.xml` — Common words only. Everything not listed falls back to English.
- `video player/app/src/main/res/values/colors.xml` — colors (describe me)
- `video player/app/src/main/res/values/strings.xml` — Extracted from the screens (Phase 2.5)
- `video player/app/src/main/res/values/themes.xml` — The share pop-up: see-through window that floats over whichever app you shared from
- `video player/app/src/main/res/xml/backup_rules.xml` — Android 11 and older auto-backup. The WebView folder holds the Instagram sign-in session, never back it up.
- `video player/app/src/main/res/xml/data_extraction_rules.xml` — Android 12+ backup and device transfer. The WebView folder holds the Instagram sign-in session.
- `video player/app/src/main/res/xml/update_file_paths.xml` — AppUpdateService downloads OffScroll-<build>.apk into cacheDir/updates
- `video player/app/src/test/java/com/example/ExampleRobolectricTest.kt` — ExampleRobolectricTest (describe me)
- `video player/app/src/test/java/com/example/ExampleUnitTest.kt` — ExampleUnitTest (describe me)
- `video player/app/src/test/java/com/example/GreetingScreenshotTest.kt` — GreetingScreenshotTest (describe me)
- `video player/app/src/test/java/com/example/data/DiagnosticsTest.kt` — DiagnosticsTest (describe me)
- `video player/app/src/test/java/com/example/data/download/AlertHealthTest.kt` — AlertHealthTest (describe me)
- `video player/app/src/test/java/com/example/data/download/InstagramParserTest.kt` — InstagramParserTest (describe me)
- `video player/app/src/test/java/com/example/data/download/MediaExtrasTest.kt` — .trimIndent()
- `video player/app/src/test/java/com/example/data/download/PeopleStoreTest.kt` — PeopleStoreTest (describe me)
- `video player/app/src/test/java/com/example/data/download/StoryAlertHistoryTest.kt` — StoryAlertHistoryTest (describe me)
- `video player/app/src/test/java/com/example/data/universal/EngineInfoParserTest.kt` — .trimIndent()
- `video player/app/src/test/java/com/example/data/universal/UniversalModelsTest.kt` — A YouTube-shaped format list: several codecs per resolution, separate audio streams, a storyboard
- `video player/app/src/test/java/com/example/data/update/UpdateParserTest.kt` — OffScroll – latest build
- `video player/app/src/test/java/com/example/ui/DesignTokensLintTest.kt` — DesignTokensLintTest (describe me)
- `video player/app/src/test/java/com/example/ui/components/AutoHideControlsTest.kt` — AutoHideControlsTest (describe me)
- `video player/app/src/test/java/com/example/ui/components/DesignSystemComposeTest.kt` — DesignSystemComposeTest (describe me)
- `video player/app/src/test/java/com/example/ui/components/ProfileViewerHelpersTest.kt` — ProfileViewerHelpersTest (describe me)
- `video player/build.gradle.kts` — Top-level build file where you can add configuration options common to all sub-projects/modules.
- `video player/engine/build.gradle.kts` — OffScroll Engine: a separate, optional app that holds yt-dlp, Python and ffmpeg (about 50 MB).
- `video player/engine/src/main/AndroidManifest.xml` — AndroidManifest (describe me)
- `video player/engine/src/main/java/com/example/engine/EngineActivity.kt` — EngineActivity (describe me)
- `video player/engine/src/main/java/com/example/engine/EngineCore.kt` — A failure with text that is safe to show. [raw] is yt-dlp's own message when there is one.
- `video player/engine/src/main/java/com/example/engine/EngineProtocol.kt` — app -> engine (Message.what). Message.arg1 is a request id the engine echoes back.
- `video player/engine/src/main/java/com/example/engine/EngineService.kt` — EngineService (describe me)
- `video player/engine/src/main/res/values/strings.xml` — strings (describe me)
- `video player/engine/src/main/res/xml/handoff_paths.xml` — Finished downloads wait here until the main app has copied them (cacheDir/handoff/<id>/)
- `video player/gradle.properties` — Project-wide Gradle settings.
- `video player/gradle/libs.versions.toml` — libs.versions (describe me)
- `video player/gradle/wrapper/gradle-wrapper.properties` — gradle wrapper (describe me)
- `video player/metadata.json` — metadata (describe me)
- `video player/public/assets/aistudio/.gitignore` — .gitignore (describe me)
- `video player/settings.gradle.kts` — settings.gradle (describe me)

### Added 2026-11-21 (replace every "describe me")
- `.github/workflows/brain-check.yml` — Runs on every push and pull request: keeps the AI brain honest (no secrets committed,

### Added 2026-11-21 (replace every "describe me")
- `video player/app/src/main/java/com/example/ui/components/FolderPickerSheet.kt` — FolderPickerSheet The folder/filter picker bottom sheet: quick views, playlists and folders as cover cards with search; replaces the old drop-down.

### Added 2026-11-21 (replace every "describe me")
- `video player/app/src/main/java/com/example/ui/model/SmartTitle.kt` — A date, optionally followed by a time: 20260121_153012, 2026-01-21-15-30-12, ... Not preceded by another digit.
- `video player/app/src/test/java/com/example/ui/SmartTitleTest.kt` — SmartTitleTest Unit tests for smartTitle (not in CI's test filter; run locally).

### Added 2026-11-21 (replace every "describe me")
- `video player/app/src/main/java/com/example/ui/components/PlayPauseFeedback.kt` — PlayPauseFeedback Pause/resume feedback on the reel: dark veil, glass play/pause button that morphs and breathes, ripple ring.

### Added 2026-11-22 (replace every "describe me")

### Added 2026-11-22 (replace every "describe me")
- `video player/app/src/main/java/com/example/ui/components/SettingsParts.kt` — SettingsParts Settings & Guide building blocks: hero card, stat tiles, section headers, phone gesture map, gesture cards, toggle row, tool tiles.

### Added 2026-11-22 (replace every "describe me")
- `docs/COPY-GUIDE.md` — OffScroll copy guide

### Added 2026-11-23 (replace every "describe me")
- `.github/actions/setup-build/action.yml` — action Shared CI step: JDK 21, SDK licences, Gradle 9.3.1 with cache.
- `.github/actions/signing-key/action.yml` — action Shared CI step: decodes the release keystore (app and engine must share it) and prints the certificate SHA-256.
- `.github/dependabot.yml` — Keeps the pipeline current without anyone remembering to. Actions are pinned to exact commits (so a hijacked tag

### Added 2026-11-24 (replace every "describe me")
- `video player/app/src/main/java/com/example/data/download/HtmlText.kt` — HtmlText Decodes HTML codes in Instagram page text (&#x1f337; -> emoji, &amp; -> &) and cuts text without splitting emoji.
- `video player/app/src/test/java/com/example/data/download/HtmlTextTest.kt` — JIT🌷 on Instagram: "#TVアニメ『ONE PIECE』

### Added 2026-11-25 (replace every "describe me")
- `video player/app/src/main/java/com/example/ui/components/MovieModeControls.kt` — MovieModeControls Movie mode controls drawn over the landscape video: top bar, skip/play, seek bar, audio and subtitle picker, lock, +10 s flash.
- `video player/app/src/main/java/com/example/ui/model/MovieMode.kt` — A horizontal video this long counts as a "movie" (5 minutes or more).
- `video player/app/src/main/java/com/example/ui/viewmodel/VideoFeedMovieActions.kt` — VideoFeedMovieActions Movie mode actions (enter, exit, lock, fit, setting) and applyMovieRules, which decides movie mode for each new video.
- `video player/app/src/test/java/com/example/ui/MovieModeTest.kt` — MovieModeTest Tests for the movie mode rules, skip, time format and fit cycling.

### Added 2026-11-26 (replace every "describe me")
- `video player/app/src/main/java/com/example/data/music/MusicPlayerManager.kt` — One song in the play queue, as the music screens need it.
- `video player/app/src/main/java/com/example/data/music/MusicRepository.kt` — Reads the phone's music files from MediaStore. Needs the music (audio) permission.
- `video player/app/src/main/java/com/example/service/MusicPlaybackService.kt` — MusicPlaybackService Background music player (Media3 session service): notification, lock-screen and Bluetooth controls.
- `video player/app/src/main/java/com/example/ui/components/MusicNowPlaying.kt` — MusicNowPlaying Full Now playing screen, seek bar, sleep timer and the queue sheet.
- `video player/app/src/main/java/com/example/ui/components/MusicParts.kt` — MusicParts Music UI pieces: cover art, equalizer bars, song/album/artist rows, tabs and the mini player.
- `video player/app/src/main/java/com/example/ui/components/MusicScreen.kt` — MusicScreen The music player tab: songs, albums, artists, liked, search, mini player, Now playing and queue.
- `video player/app/src/main/java/com/example/ui/model/MusicModels.kt` — One music file on the phone. Plain data (no Android types) so the sorting and grouping can be tested.
- `video player/app/src/main/java/com/example/ui/viewmodel/MusicViewModel.kt` — MusicViewModel Music library and what the music screens have open (tab, search, album/artist, liked songs).
- `video player/app/src/test/java/com/example/ui/MusicModelsTest.kt` — MusicModelsTest Tests for music sorting, search, album/artist grouping and text helpers.

### Added 2026-11-26 (replace every "describe me")
- `video player/app/src/main/java/com/example/ui/components/MusicLeaveDialog.kt` — MusicLeaveDialog Pop-up shown when leaving the music tab while a song plays: Stay in music, or Switch to videos (pauses the music).

### Added 2026-11-27 (replace every "describe me")
- `video player/app/src/main/java/com/example/ExternalVideoActivity.kt` — Standalone player opened from other apps' Open with / Play with; plays one handed-over video
- `video player/app/src/test/java/com/example/ExternalVideoManifestTest.kt` — Guards the manifest filters that list OffScroll as a video player in other apps
