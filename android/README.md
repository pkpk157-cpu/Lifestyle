# Lifestyle — Android build

A thin native shell around the same web app, built for one reason: **automatic
SMS scanning**. No browser can read your inbox, so this build does it natively
and hands the messages to the same parser the web app already uses.

Everything else is identical to the PWA. Same code, same localStorage, same
screens.

---

## Why this exists

`READ_SMS` is an Android permission. There is no web equivalent — the only
SMS-adjacent browser API (WebOTP) reads a single specially-formatted one-time
code and cannot enumerate messages. So the choice is a native shell or manual
import.

## What it does

- Loads the web app from **bundled assets**, not from the internet.
- Exposes exactly one bridge, `window.AndroidSms`, with four read-only methods:
  `isAvailable()`, `hasPermission()`, `requestPermission()`, `readInbox(since, limit)`.
- Scans the inbox on launch (if you leave "Scan on every launch" on), parses
  bank / card / UPI alerts, and files them as spends.
- Keeps a watermark so later scans only read messages newer than the last one.

## Privacy

The manifest declares **no `INTERNET` permission**. That is deliberate and it is
the strongest guarantee available here: the app is structurally incapable of
sending your messages anywhere, whatever the code does.

Two consequences follow from that:

- The Google Drive sync in the PWA build will not work in this build. If you
  want it, add `<uses-permission android:name="android.permission.INTERNET" />`
  to `AndroidManifest.xml` and accept that you have given up the guarantee.
- The page is served over `https://appassets.androidplatform.net` via
  `WebViewAssetLoader`, from `app/src/main/assets/web/`. `addJavascriptInterface`
  exposes the bridge to whatever the WebView has loaded, so `MainActivity`
  refuses to navigate anywhere else — any other URL is handed to the system
  browser instead. Do not change the WebView to load a remote URL while the
  bridge is attached.

## Build it

You need Android Studio (or a command-line Android SDK) — this repo has no
Gradle wrapper checked in, so use Android Studio's, or your own `gradle`.

```bash
cd android
./sync-web.sh                     # copy index.html etc. into app assets
```

Then either open `android/` in Android Studio and hit Run, or from the command
line with an Android SDK installed:

```bash
gradle wrapper                    # first time only
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

Install it:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On the phone: **Settings → Apps → Lifestyle → Permissions → SMS → Allow**, or
just tap "Allow and scan my messages" in the app and answer the dialog.

## Updating after a web change

The assets are a copy, so every time `index.html` changes:

```bash
cd android && ./sync-web.sh && ./gradlew assembleDebug
```

## Play Store

Don't bother. `READ_SMS` is a restricted permission — Google only grants it to
apps that are the device's default SMS handler, or that get a specific policy
exception. This is a personal build; sideload it.

## Files

| Path | What it is |
|---|---|
| `app/src/main/java/com/lifestyle/app/MainActivity.java` | WebView host, asset loader, permission flow |
| `app/src/main/java/com/lifestyle/app/SmsBridge.java` | The only native surface exposed to JS |
| `app/src/main/AndroidManifest.xml` | Permissions — note what is *absent* |
| `app/src/main/assets/web/` | Copy of the web app, written by `sync-web.sh` |
| `sync-web.sh` | Copies the web app into assets |

## Known limits

- Only the **inbox** is read. Sent messages and RCS/chat messages are not
  visible through `content://sms/inbox`.
- Scanning happens when the app is open, not in the background. A
  `RECEIVE_SMS` broadcast receiver could file transactions the moment they
  arrive; it is not built yet.
- The parser targets Indian bank, card and UPI alert formats. A message it does
  not recognise is skipped silently rather than guessed at — check the preview
  after a scan and paste anything it missed into the manual box so the format
  can be added.
