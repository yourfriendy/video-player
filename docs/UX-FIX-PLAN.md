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

## Phase 1: trust and access (audit P0 + foundations)

| # | Task | Audit | Acceptance check |
|---|---|---|---|
| 1.1 | Open the app without the video permission. The permission screen becomes a welcome screen with three paths: allow access, use the Downloader, try demo clips. No permission dialog at launch; it appears when the person taps Allow. | #1 | Fresh install reaches a usable screen with no system dialog. `needsPermissionScreen` gate gone from MainActivity. |
| 1.2 | Ask for the notification permission in context (when alerts are switched on, and when the Downloader opens), not at launch. | #2 | `askForNotificationsOnce()` is no longer called from `onResume`. |
| 1.3 | Describe every icon-only button and give small taps a 48 dp touch target. | #3 | Zero `contentDescription = null` on an `Icon` directly inside an `IconButton`. Listed small taps use `touchTarget()`. |
| 1.4 | Respect reduced motion and low-RAM phones in the shared animations (aurora background, shimmer, loader ring, dots). | #4 | A single `rememberMotionEnabled()` gates them; static fallback draws. |
| 1.5 | Delete the unused `DownloaderSheet` composable and anything only it used. | #8 | `DownloaderSheet(` has no definition; build green; the duplicate disclaimer text is gone. |
| 1.6 | Design tokens: one colour/type/shape file; the per-file palettes alias it; font sizes and corner radii snap to the scale; a lint test enforces the scale. | #5 #6 #7 | `DesignSystem.kt` exists; `DesignTokensLintTest` passes; distinct font sizes <= 10, radii <= 8. |

**Type scale:** 11, 12, 14, 16, 18, 20, 24, 28, 34 sp. **Radius scale:** 4, 8, 12, 16, 20, 24, 28 dp (plus circle).
The Instagram-black profile viewer keeps its own `Ig*` colours on purpose (people expect it to look like Instagram).

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
- [ ] Phase 1
- [ ] Phase 2
- [ ] Phase 3
- [ ] Phase 4

## Not verifiable without a device

Frame rate and battery, TalkBack output, 200 percent font scale, real-world Instagram load times, how the welcome screen reads to a first-time user.
