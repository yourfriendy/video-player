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

## Phase 2: consistency (audit P1)

| # | Task | Audit | Acceptance check |
|---|---|---|---|
| 2.1 | One button component (`DsButton`: primary, secondary, quiet) replacing GradientButton, OutlineButton, ProfileButton, ActionPill, Pill. | #8 | One definition per role; old names removed or thin wrappers. |
| 2.2 | One avatar component (ring optional) replacing Avatar, ProfileAvatar, AvatarWithRing. | #8 | One definition. |
| 2.3 | One feedback system: a Snackbar host with actions for passing events; Toasts removed. | #9 | `Toast.makeText` count is 0 in UI code. |
| 2.4 | Glossary applied to all visible text: Save (never Download), Alerts (never Notifications for our feature), Try again (never Retry). | #10 | A lint test fails on forbidden words in user-facing strings. |
| 2.5 | Extract hard-coded UI strings to `strings.xml`; scaffold Hindi and Telugu files. | #12 | `Text("...")` literal count falls below 20; `values-hi` and `values-te` exist. |
| 2.6 | Bridge the Instagram viewer chrome and the app chrome (top bars, sheets share tokens). | #11 | Shared top-bar and sheet components. |

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
- [ ] Phase 2
- [ ] Phase 3
- [ ] Phase 4

## Not verifiable without a device

Frame rate and battery, TalkBack output, 200 percent font scale, real-world Instagram load times, how the welcome screen reads to a first-time user.
