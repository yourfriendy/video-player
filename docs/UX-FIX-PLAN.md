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

## Phase 3: flows and architecture (audit P2)

| # | Task | Audit | Acceptance check |
|---|---|---|---|
| 3.1 | Real routes for Profile, Alerts and Search with back-stack and deep links. | #13 #14 | NavHost has the routes; BackHandler count drops. |
| 3.2 | Split the 1,750-line ViewModel by feature (Feed, Search, Downloader, Profile). | #13 | No ViewModel over 600 lines. |
| 3.3 | Coach marks for gestures at first use; a "?" in the player. | #15 | First-run flag + tests. |
| 3.4 | Alerts: one entry point, four plain states, short history. | #16 | One bell entry; state enum with tests. |
| 3.5 | Staged, cancellable loading with early results. | #17 | Controller reports `step i of n`; Cancel works. |
| 3.6 | Typed errors plus one reporter; "Send diagnostics" in Settings. | #18 | Empty catches under 10; diagnostics action exists. |
| 3.7 | Completion moments: "View in library" Snackbar after save. | #19 | Snackbar action opens the library item. |

## Phase 4: polish and engineering (audit P3 + section 7)

| # | Task | Acceptance check |
|---|---|---|
| 4.1 | Unseen-story rings from the stored seen ids. | Ring style differs for seen and unseen. |
| 4.2 | Haptics on toggles, tab switches, save complete, timer slider. | Haptic calls in each. |
| 4.3 | Replace polling loops with StateFlow collection. | `while (true)` in UI code is 0. |
| 4.4 | Baseline Profile and a macrobenchmark module. | Module builds in CI. |
| 4.5 | Compose UI tests for core flows plus automatic accessibility checks. | UI test count over 10. |
| 4.6 | Window-size aware layouts (tablet, foldable). | Adaptive grid on wide screens. |
| 4.7 | Light theme, AMOLED black, dynamic colour. | Theme switch in Settings. |

## Status log

- [x] Plan written (this file)
- [x] Phase 1 (code done and CI-checked; waiting for the on-phone checklist)
- [x] Phase 2 (2.6 moved to Phase 3; strings second pass pending)
- [ ] Phase 3
- [ ] Phase 4

## Not verifiable without a device

Frame rate and battery, TalkBack output, 200 percent font scale, real-world Instagram load times, how the welcome screen reads to a first-time user.
