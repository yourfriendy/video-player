# OffScroll UX and engineering fix plan

Source: the UX and engineering audit (design-psychology research, then a measured read of 85 Kotlin files).
This file is the single source of truth. Each phase ends with its boxes ticked here, in the same commit as the work.

## Rules of engagement

1. **One phase = one branch.** Branch from `main`, work through the phase's tasks in order, merge only when CI is green.
2. **Every task has an acceptance check** (below). A task is only ticked when its check passes.
3. **CI is the compiler and the guard.** Unit tests must pass. From Phase 1 a design-lint test fails the build if someone adds an off-scale font size or corner radius.
4. **No device here.** I can't run the app, so each merged phase ends with a short on-phone checklist for you. Findings that need a device are marked *(device)*.
5. **Honest status.** Tasks I could not finish are left unticked with the reason, not quietly dropped.
6. **Small visual risk first.** Mechanical, behaviour-neutral changes (tokens, descriptions) go before anything that changes layouts or flows.
7. **Every build goes to Releases (never only to a workflow artifact), but only a deliberate one replaces `latest`.** A build from `main` is published as the `nightly` pre-release (replaced on every push). The public `latest` release (what the README button and the in-app updater use) changes only on a manual run of `build-apk.yml` with *Publish to latest* ticked, or on a pushed `v*` tag, so its download address and file stay stable between releases. Other branches publish `build-<branch>` pre-releases. A `main` build is never cancelled, and a flaky dependency download is retried once. Every release's notes keep the `Build number: <n>` line that `UpdateModels.kt` parses.

## Phase 1: trust and access (audit P0 + foundations): DONE, awaiting your on-phone check

| # | Task | Result |
|---|---|---|
| [x] 1.1 | Open the app without the video permission | The permission screen is now a welcome screen inside the feed with three paths (Allow video access, Just save stories/reels/videos, Demo reels). No dialog at launch; it appears when you tap Allow. `needsPermissionScreen` is gone; a test guards it. |
| [x] 1.2 | Notification permission in context | Removed from launch. Asked once per install when the Downloader opens, and when alerts are switched on (that flow already asked). |
| [x] 1.3 | Describe icon-only buttons; bigger taps | **Correction to the audit:** most of the 83 `contentDescription = null` icons are decorative icons beside text, which is correct. Only the two clickable thumbnails lacked a label; both fixed ("Open post", "Open video/photo"). Six undersized taps raised from 34-36 dp to 44 dp (playlist emoji, alert presets, profile buttons, search chip, share-popup close, feed pill). 44 dp is the WCAG AAA / Apple minimum; Material's 48 dp remains a stretch goal for chips. |
| [x] 1.4 | Reduced motion and low-RAM | `rememberMotionEnabled()` (animation scale 0 or low-RAM phone) makes the aurora background, shimmer, loader ring and dots draw a still version. **Still to do:** the other animations (search bar edge, scrubber, etc.) are not yet gated. |
| [x] 1.5 | Delete the unused sheet | `DownloaderSheet` removed along with four private helpers only it used: 1,066 lines down to 684. |
| [x] 1.6 | Design tokens | `ui/theme/DesignSystem.kt` (palette, type, radius, motion). 38 per-file colour constants now alias it. Font sizes 21 distinct down to 10; corner radii 21 down to 7. `DesignTokensLintTest` fails the build on any off-scale size or radius. |

**Type scale:** 11, 12, 13, 14, 16, 18, 20, 24, 28, 34 sp. **Radius scale:** 4, 8, 12, 16, 20, 24, 28 dp (plus circle).
Snapping moved some sizes by up to 2 sp (9 to 11, 22 to 20, 26 to 24, 15 to 14, 17 to 16). *(device)* Check for text that now wraps or clips.
The Instagram-black profile viewer keeps its own `Ig*` colours on purpose (people expect it to look like Instagram).

**On-phone checklist for Phase 1**
1. Fresh install (or clear data): the app opens straight to the welcome screen with no system dialog. Tap "Just save stories, reels and videos": the Downloader opens.
2. Tap Allow video access: the system dialog appears; after allowing, the feed loads.
3. Open the Downloader: notification permission is asked once there.
4. Turn on system "Remove animations": the downloader background and loaders stop moving.
5. Skim the Downloader, Search, Settings and the profile for text that now wraps or looks cramped.

## Phase 2: consistency (audit P1): code done, awaiting CI and your on-phone check

| # | Task | Result |
|---|---|---|
| [x] 2.1 | One button component | `DsButton` (Primary and Secondary, both 56 dp, same radius and text size). `GradientButton` and `OutlineButton` are thin wrappers, so all 26 call sites now match. **Left as is on purpose:** `ProfileButton` and `ActionPill` belong to the Instagram-style viewer chrome; chips (`Pill`, `StatusChip`, `TypeTag`) are a separate family. |
| [x] 2.2 | One avatar component | `Avatar` is the single implementation (size by parameter or by modifier, optional plain placeholder colour). `ProfileAvatar` is now a one-line view of it. |
| [x] 2.3 | One feedback system | `Feedback.show(text, action?)` plus `FeedbackLayer`/`FeedbackPill`. All 15 Toasts in the UI are replaced; the unused Snackbar host is gone. Hosted in the main window, the profile dialog, the story viewer and the metadata sheet so the message shows above whichever window is on top. Announced to screen readers; shown 3 to 7 s depending on length. A test fails the build on any new Toast in the UI. |
| [x] 2.4 | Glossary | Save (never Download) for media; "Saving", "Save another", "Couldn't save."; the engine and app update screens keep "download" because that really is a download of a program. A test fails the build on Retry, Any link, Download another, Download failed. |
| [x] 2.5 | Strings to resources | 123 `Text("...")` call sites moved to `strings.xml` (133 resources). Hindi and Telugu files added with 16 common words each; everything else falls back to English. **Needs a native speaker review.** 32 strings with `$` placeholders, and strings outside `Text()` (labels, titles, error messages), are still hard-coded and need a second pass. |
| [ ] 2.6 | Bridge the viewer chrome and the app chrome | Not done. Tokens already unify colour, type and radius across both. A shared top bar and sheet belong with the navigation refactor, so this moves to Phase 3 (3.1). |

**On-phone checklist for Phase 2**
1. Copy a link somewhere in the profile viewer: a dark pill ("Link copied") appears at the bottom, above the viewer.
2. Turn on alerts without notification permission: the message appears and Settings opens.
3. Look at every button in the Downloader: Save, Save another, Try again should all be the same height and radius.
4. Profile viewer avatars still look right (grey placeholder, no gradient ring).
5. Switch the phone language to Hindi or Telugu: Search, Save, Cancel and a few other words change; the rest stays English.

## Phase 3: flows and architecture (audit P2): done, with the exceptions listed

| # | Task | Result |
|---|---|---|
| [x] 3.1 | Routes and deep links | **Alerts is a real route** (`offscroll://alerts`), and `offscroll://person/<name>` opens a person. The navigation host now closes the Downloader when you land back on the feed, so back behaviour no longer depends on a Dialog's dispose callback. **Profile and Search stay overlays on purpose:** their state lives in controllers and the profile is a Dialog window; turning them into routes needs on-device testing of back behaviour. |
| [x] 3.2 | Split the ViewModel | `VideoFeedViewModel` 1,759 lines down to 352 (state, jobs, init, core). The Downloader/people/alerts functions and the library/playback functions are extension functions in `VideoFeedDownloaderActions.kt` (about 740 lines) and `VideoFeedLibraryActions.kt` (about 770 lines): same package, same call syntax. The two action files are still large; splitting them into separate controllers is the next step. |
| [x] 3.3 | Gesture tour | A "Quick tour" dialog appears once, 2.5 s after the first feed, and again from the three-dots hub. |
| [x] 3.4 | Alerts plain states and history | Done earlier (banner, one fix button, last 5 checks). |
| [~] 3.5 | Staged, cancellable loading | **Cancel done.** **Real stage names done:** the loader reports "Asking Instagram directly…" and "Opening Instagram's own page…", shown in the full-screen wait and the Downloader. **Not possible:** showing the first stories while the rest load, because the Instagram loaders return everything at once. |
| [~] 3.6 | Errors and diagnostics | Copy diagnostics done; silent catch blocks 28 down to 5 (each now logs to diagnostics). **Not done:** a typed error hierarchy. |
| [x] 3.7 | Completion moments | "Saved to your library · View" after profile, story and repost saves; the direct reel save shows its own success card. |

## Phase 4: polish and engineering

| # | Task | Result |
|---|---|---|
| [x] 4.1 | Unseen-story rings | A person's ring glows while they have a live story you haven't watched here, and goes faint after. Stored per person (live ids and viewed ids), also read by screen readers ("has a new story"). Unit tested. |
| [x] 4.2 | Haptics | Tab switches, alert switches, the timer slider steps, and the moment a save finishes. |
| [~] 4.3 | Replace polling | The alert status now arrives through a flow (and a refresh when the screen returns), not a 2-second loop. The 5 remaining `while (true)` loops are legitimate tickers (a clock, the video progress bar, the "taking longer" timer). |
| [~] 4.4 | Baseline profile | A hand-written `baseline-prof.txt` (marks the app's own code as hot) plus the profile installer so sideloaded installs benefit. **Not created:** a Macrobenchmark module; it can only be generated and verified on a device or emulator. |
| [~] 4.5 | Tests | 121 test cases in 16 files, including 10 Compose component tests on the JVM and the design lint tests. **Not done:** automatic accessibility checks (the Compose test version in use doesn't include them) and full-screen UI flow tests (need a device). |
| [x] 4.6 | Wide screens | Downloader, Alerts and Settings are width-capped (640 dp) and centred; the profile and library grids adapt their column count to the screen width. |
| [~] 4.7 | Appearance | **Pure black (AMOLED) switch in Settings.** **Not done:** light theme and dynamic colour, which need the screens' hundreds of colour literals turned into semantic roles first. |

## Issues you reported during this work (all fixed)

| Issue | Cause | Fix |
|---|---|---|
| "Where do I watch my playlist videos?" | The feed's filter menu never listed playlists, even though you could add videos to them | Playlists are listed in the filter menu with counts, and a hint shows when there are none |
| Options vanish a few seconds after tapping Grid | The playback auto-hide timer also ran in the grid view | The timer skips grid mode, and entering the grid brings the controls back |
| Buttons uneven and cramped | Five 38 dp buttons plus a pill overflowed a 360 dp screen | Four 44 dp buttons, one icon size, one gap; Settings moved into the three-dots hub; the filter pill is 44 dp and shrinks with an ellipsis |

## Scoreboard (before the work, after)

| Measure | Audit | Now |
|---|---|---|
| Distinct font sizes | 21 | 10 |
| Distinct corner radii | 21 | 7 |
| Toasts in the UI | 15 | 0 |
| Silent catch blocks | 28 | 5 |
| Main ViewModel lines | 1,759 | 352 |
| String resources | 18 | 135 (plus 2 partial translations) |
| Hard-coded `Text("...")` | 192 | 43 |
| Test cases | about 60 | 121 |

## Left for a supervised, on-device session

1. Profile and Search as full routes; splitting the two action files into proper controllers.
2. Light theme and dynamic colour (semantic colour roles first).
3. Macrobenchmark module and a measured baseline profile.
4. Full-screen UI flow tests and automatic accessibility checks.
5. Typed errors, showing partial results while Instagram loads.
6. Native-speaker review of the Hindi and Telugu strings, and the remaining 43 hard-coded strings.

## Not verifiable without a device

Frame rate and battery, TalkBack output, 200 percent font scale, real-world Instagram load times, how the welcome screen reads to a first-time user.
