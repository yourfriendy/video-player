# Getting rid of the "unverified app" and scan prompts

OffScroll isn't on the Google Play Store, so Android treats it as an app from an unknown developer. That is what
causes the "Play Protect wants to scan this app" prompt, and on some phones a block. Nothing in the app's code can
switch that off. Google's own fix is **developer verification**: you link your identity to the app's package name
and signing key once, and every build signed with that key is recognised afterwards.

## What to register

| What | Value |
|---|---|
| App package name | `com.aistudio.offscroll.vfeed` |
| Engine package name | `com.aistudio.offscroll.engine` |
| Signing key SHA-256 | printed by every CI run (open the run, scroll to the **Summary**) and in each release's notes |

Both apps use the same key, so register both package names with that one fingerprint.

## Steps (about 15 minutes, needs your Google account and ID)

1. Create an account in the **Android Developer Console** (developer.android.com/developer-verification).
   - **Full account:** one-time fee (about $25) plus ID. Anyone can install your apps.
   - **Limited distribution (free, hobbyists):** no ID, but only up to 20 devices. Good for friends, not for the public.
2. **Package names** > add `com.aistudio.offscroll.vfeed` and `com.aistudio.offscroll.engine`.
3. Add the **SHA-256 fingerprint** from the CI summary.
4. For package names that already exist, Google asks you to prove you own the key by signing a dummy APK:
   1. Download the dummy APK from the console.
   2. Put it somewhere with a direct link (attach it to a GitHub issue, for example).
   3. Run the **Sign Google registration APK** workflow (Actions tab) with that link.
   4. Download the **signed-registration-apk** artifact and upload it to the console.

The key lives only in the repository secrets, which is why the workflow signs it for you.

## After registering

- Every future CI build signed with the same key qualifies automatically.
- **Never change the signing key.** A new key would need registering again, and Android refuses to update over an app signed
  with a different key. Keep the `ANDROID_KEYSTORE_BASE64` and `ANDROID_KEYSTORE_PASSWORD` secrets safe.
- Play Protect's "scan this app" prompt can still appear for brand-new apps with few installs. It goes away as more
  people install the same signed app. Tapping **More details > Install anyway** (or **Scan app**) is safe.

## Why some phones block it

From September 30, 2026 certified phones in Brazil, Indonesia, Singapore and Thailand refuse apps from unregistered
developers (a global rollout follows in 2027). Google also provides an "advanced flow" for people who want to install an
unregistered app anyway; it includes a waiting period. Registering the package names above avoids all of it.
