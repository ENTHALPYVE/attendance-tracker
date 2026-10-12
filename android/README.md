# Android app (Trusted Web Activity)

The Android app is a **Trusted Web Activity (TWA)**: a small Android app that opens
https://attandtrackinger.qd.je full screen in Chrome's engine. It is made with **Bubblewrap**, Google's
tool for turning a web app into an Android app. The app has no code or data of its own, so:

- the website, the Java server and the Aiven database stay the only place where data lives;
- a new version of the website reaches the app at once; the APK only needs rebuilding when its name,
  icon or colours change;
- login, sessions and all security rules are exactly those of the website.

## Files

| File | Purpose |
|---|---|
| `twa-manifest.json` | the app's settings: package name `je.qd.attandtrackinger.twa`, name, colours, icons, start URL |
| `setup-tools.sh` | one-time download of JDK 17 + Android SDK + Bubblewrap into `%LOCALAPPDATA%\attendance-android-tools`, then generates the Android project there |
| `generate-project.js` | turns `twa-manifest.json` into an Android project (used by `setup-tools.sh`) |
| `build-release.ps1` | creates your signing key (first time), builds and signs `AttendanceTracker.apk`, prints the key fingerprint |

Nothing secret is stored here. The signing key lives outside the project
(`%USERPROFILE%\attendance-signing\`), and `.gitignore` blocks `*.keystore`, `*.jks` and `*.apk`.

## Build the app

1. **Tools** (once, about 1 GB of downloads; accepts the Android SDK licence). In **Git Bash**:
   ```bash
   bash android/setup-tools.sh
   ```
2. **Signed APK.** In **PowerShell**, from the project folder:
   ```powershell
   powershell -ExecutionPolicy Bypass -File android\build-release.ps1
   ```
   The first time, `keytool` asks you to choose a **key password** and a few name questions (your name and
   college are fine). Then `apksigner` asks for the password again to sign the app. The result is
   `%LOCALAPPDATA%\attendance-android-tools\AttendanceTracker.apk`.

   **Keep `attendance-release.keystore` and its password safe** (for example a copy on a USB drive and
   the password in a password manager). Updates to the app must be signed with the same key. Never put
   the keystore in Git or send it to anyone.
3. **Link the app to the website.** The script prints a fingerprint like `AB:CD:...:EF` (public, not a
   secret). On Render → *attendance-tracker* → **Environment**, add
   `ANDROID_CERT_SHA256` = that fingerprint and save (Render restarts the app). Check that
   https://attandtrackinger.qd.je/.well-known/assetlinks.json now shows it.
   Without this step the app still works, but shows a small address bar at the top.

## Install on a phone

1. Copy `AttendanceTracker.apk` to the phone (USB cable, Google Drive, or email it to yourself).
2. Open it on the phone. Android asks to allow installing apps from that source (Files / Drive / Gmail):
   allow it once, then tap **Install**.
3. Open **Attendance** from the home screen and log in as on the website.

Chrome (or another browser that supports TWAs) must be installed on the phone; it is on almost every
Android phone.

## Changing the app

Edit `twa-manifest.json` (e.g. `appVersionCode` + 1 and `appVersion` for a new version), run
`bash android/setup-tools.sh` again to regenerate the project, then `build-release.ps1`.

**Package name:** `je.qd.attandtrackinger.twa` is built from the website's domain. Change it before the first
release if you prefer another name; after people have installed the app (or after publishing on Google Play)
it must stay the same. If you change it, also set `ANDROID_PACKAGE_NAME` on Render.

## Google Play (optional)

Publishing needs a Google Play developer account (one-time fee) and an **.aab** bundle instead of an APK
(`gradlew bundleRelease` in the generated project, signed with the same key). For a college project, the APK
is enough.

## iPhone / iPad

There is no native iOS app. On iPhone and iPad, open the website in Safari → **Share** →
**Add to Home Screen**; it then opens full screen like an app. An App Store app would need a Mac, Xcode
and an Apple Developer account.
