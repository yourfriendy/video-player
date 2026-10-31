# Instagram sign-in blank screen: what changed

Branch: `fix/instagram-login-blank-screen` (based on `main` at `7d01d86`)

## The problem

Tapping **Sign in with Instagram** (Stories tab) opened a dialog with the header
"Sign in to Instagram" and an empty dark body. No form, no spinner, no error.

## What the evidence showed

- The empty area in the screenshot was a perfectly uniform `#0C0F14`. That is not an app
  colour (the dialog header is `Slate950`, `#020617`). It matches Instagram's own dark-mode
  background, so Instagram's page started rendering but its login form never appeared.
- The old `IgLoginDialog` only implemented `onPageFinished`. It had no loading indicator, no
  `onReceivedError` / `onReceivedHttpError`, and no console logging, so a slow load and a real
  failure both looked like a blank screen.
- `InstagramDownloader.webUserAgent` removed only `; wv` from the WebView user agent. The
  `Version/4.0` token, which plain Chrome never sends, stayed in, so the WebView was still
  identifiable as a WebView.

**Confidence:** the missing feedback is a fact (it is in the old code). The user-agent
explanation is the most likely cause but is **not confirmed**: it could not be tested against
instagram.com from the environment where this was written.

## Files changed

| File | Change |
|---|---|
| `data/download/InstagramDownloader.kt` | Modified |
| `ui/components/DownloaderSheet.kt` | Modified |
| `src/test/.../data/download/InstagramUserAgentTest.kt` | Added |
| `docs/INSTAGRAM_LOGIN_FIX.md` | Added (this file) |

Paths are under `video player/app/src/main/java/com/example/` except the test.
Nothing else was touched: no ViewModel, `InstagramStoryService`, manifest, Gradle, ProGuard or
dependency changes.

## 1. `InstagramDownloader.kt`

- Added a top-level `internal fun cleanWebUserAgent(userAgent: String): String` that removes
  `; wv` and also the regex `\s+Version/\d+(\.\d+)*`.
- `webUserAgent` is now `cleanWebUserAgent(WebSettings.getDefaultUserAgent(context))`.
  Before: `WebSettings.getDefaultUserAgent(context).replace("; wv", "")`.
  The fallback to `DESKTOP_UA` on exception is unchanged.
- Side effect: the same value is passed to `InstagramStoryService`, so the story API calls
  (OkHttp) now send the same cleaned user agent. This keeps the sign-in page and the API
  calls looking like one consistent client, which was the original intent of the comment.

## 2. `DownloaderSheet.kt` (`IgLoginDialog`)

New imports: `android.util.Log`, `ConsoleMessage`, `WebChromeClient`, `WebResourceError`,
`WebResourceRequest`, `WebResourceResponse`, `Icons.Default.Refresh`, and the Compose runtime
`remember` / `mutableStateOf` / `mutableIntStateOf` / `getValue` / `setValue`.

New file-level constants:

- `IG_LOGIN_URL` = `https://www.instagram.com/accounts/login/` (same URL as before)
- `IG_LOG_TAG` = `"IgLogin"`
- `IG_FORM_PROBE_JS` = `document.querySelectorAll('input').length`
- `IG_FORM_PROBE_INTERVAL_MS` = `4000`
- `IG_FORM_PROBE_MAX_TRIES` = `15`

New state: `loadProgress`, `error`, `formMissing`, and `webViewRef` (so Retry can reload).

New behaviour:

1. **Progress bar.** `WebChromeClient.onProgressChanged` drives a 3 dp cyan bar at the top of
   the page while `loadProgress < 100` and there is no error.
2. **Hard errors.** `onReceivedError` (main frame only) and `onReceivedHttpError` (main frame,
   status >= 400) replace the page with a message and a **Try again** button.
3. **Silent blank page.** After a page whose URL contains `/accounts/login` finishes loading,
   the dialog runs `IG_FORM_PROBE_JS` every 4 s. If it returns 0 from the second probe on
   (about 8 s after the page finished), a bottom banner appears:
   "Instagram's sign-in form hasn't appeared yet. It may just be slow to load." with a
   **Reload** action. The probe stops when the form is drawn, sign-in completes, an error is
   shown, the WebView is detached, or after 15 tries (about 60 s).
4. **Logging.** `onConsoleMessage` writes JavaScript errors from Instagram's page to logcat
   with `Log.w` under the tag `IgLogin`, and returns `false` so default console handling is
   kept. Main-frame load errors and HTTP errors are logged under the same tag.

Unchanged: the dialog header text, the close button (test tag `ig_login_close`), cookie
settings, JavaScript and DOM-storage settings, the user-agent assignment, and the sign-in
detection in `onPageFinished` (`InstagramStoryService.isSignedIn()` then `flush()` then
`onClose()`). `onRelease` still calls `destroy()` and now also clears `webViewRef`.
New test tag: `ig_login_reload`.

## 3. `InstagramUserAgentTest.kt` (new)

- `stripsBothWebViewMarkers`: a realistic WebView user agent ends up with no `wv` and no
  `Version/`, and is otherwise unchanged.
- `leavesAPlainChromeAgentUntouched`: a plain Chrome user agent is returned as-is.

## Known limits

- **Not compiled and not run on a device.** The change was checked by reading the code
  (bracket balance, imports) and the user-agent regex was checked separately. Please build
  and try the sign-in flow.
- If the page is still blank after this change, the reload banner and the `IgLogin` logcat
  output should show why. A further thing to try is the `X-Requested-With` header that
  WebView adds to every request (needs `androidx.webkit`, not added here).
- On a very slow connection the "form hasn't appeared yet" banner can show while the page is
  still loading. It does not block the page and the banner text says so.

## How to test

1. Build and install, open **Save from Instagram**, **Stories**, tap **Sign in with Instagram**.
2. Expected: a progress bar, then Instagram's login form.
3. Turn on airplane mode and retry: expected an error panel with **Try again**.
4. Debug a blank page: `adb logcat -s IgLogin`.

## Roll back

`git revert` the two commits on this branch, or simply do not merge it.
