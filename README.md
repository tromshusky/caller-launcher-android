# Caller Launcher

A minimal Android home-screen launcher designed for hardware-keyboard use.

## Features

- **Big clock** at the top, with the date right below in `Weekday DD-MM-YYYY` format.
- **Alphabetical app list** that scrolls below the clock.
- **Keyboard navigation**: the current app is outlined by a clear frame; move the
  selection with the **Up / Down arrow keys** and open it with **Enter**.
- **Dial by typing**: press number keys to build a phone number shown in a
  single-line field; press **Enter** to dial it (falls back to the system dialer
  if the call permission is not granted). **Backspace** deletes the last digit.

## Building

```bash
./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # release APK
```

The APK is written to `app/build/outputs/apk/`.

## Releases

Pushing a tag matching `v*` (e.g. `v1.0.0`) triggers the
[`Build and Release APK`](.github/workflows/build-release.yml) workflow, which
builds, signs, and publishes the APK as a GitHub Release.

If the `SIGNING_KEY`, `KEY_ALIAS`, `KEY_STORE_PASSWORD`, and `KEY_PASSWORD`
repository secrets are set, the APK is signed with that key. Otherwise a
temporary keystore is generated so the released APK is still installable.
