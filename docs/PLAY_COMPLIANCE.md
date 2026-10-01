# Google Play and Android compliance review

*Insula DICOM Viewer 1.9.2. Reviewed 30 September 2026 against Android 16 (API 36) and Android 17 (API 37, the current release since June 2026).*

## Summary

| Area | Status |
|---|---|
| Target API level 36, required for new apps and updates since 31 August 2026 | **Done** in 1.7.0 |
| Minimum supported version | Android 7.0 (API 24), unchanged |
| Edge-to-edge display (enforced when targeting API 35+, no opt-out at 36) | **Done** |
| Predictive back (`onBackPressed` no longer called when targeting 36 on Android 16+) | **Done** |
| Large screens (orientation and resizability limits ignored at 36) | **Compatible**: no orientation or aspect-ratio locks; layouts adapt |
| 16 KB memory page size | **Not applicable**: no native code |
| Permissions | Internet only |
| Hardware features | OpenGL ES 2.0 required (all phones); OpenGL ES 3.0 used when present for 3D VRT, with a CPU fallback, so it isn't a store filter |
| Android App Bundle (.aab) for Play uploads | **To do** |
| Play Console: App Signing, health apps declaration, data safety, privacy policy URL | **To do** (answers prepared in [RELEASING.md](RELEASING.md)) |
| Target API 37 (expected to be required from August 2027) | Planned; see "Android 17 readiness" |

## How compatibility with older Android is kept

The app still compiles against the Android 6 (API 23) platform with Ubuntu's build tools, so nothing it calls directly can be missing on older phones. Newer platform features are called by reflection, guarded by version checks, in `Compat.java`. Their signatures were checked against the Android 16 platform, and the whole app is compile-checked against the API 36 platform to confirm that no API it uses has been removed.

### Edge-to-edge

Every screen draws behind the status and navigation bars on **every** Android version, not only 15+, so the layout is identical from Android 7 to 17. `BaseActivity` wraps each screen in a container padded by the real system-bar, display-cutout, and keyboard insets, using `WindowInsets.getInsets(Type)` on Android 11+ and the older inset methods plus cutout safe insets below that. Including the keyboard inset keeps text fields visible, since `adjustResize` no longer resizes edge-to-edge windows on Android 11+.

### Back navigation

Every screen implements `handleBack()`, which closes panels, menus, and modes first.

- **Android 16 and later:** an `OnBackInvokedCallback` is registered at the default priority and calls `handleBack()`. If nothing needs closing, a non-root screen finishes and the home screen moves to the background, matching the system default.
- **Android 15 and earlier:** the platform still delivers Back to `onBackPressed()` for this app, which calls the same `handleBack()`. The callback is not registered there, so Back is never handled twice.

## Android 17 readiness (targeting API 37 later)

| Android 17 change | Impact on Insula | Action when targeting 37 |
|---|---|---|
| Large screens can no longer opt out of resizing and orientation changes | None; nothing is locked | None |
| Runtime permission for local-network access | PACS servers on a hospital LAN (for example Orthanc at 192.168.x.x) | Request the permission when a profile points at a local address |
| App memory limits based on device RAM (all apps) | MPR sizes its volume to the available heap and reduces resolution if needed | Re-test large series on low-RAM phones |
| `usesCleartextTraffic` to be deprecated | Used to allow `http://` PACS on local networks | Move to a network security configuration |
| Reflection can't modify static final fields | Not used | None |

## Google Play Console checklist

See [RELEASING.md](RELEASING.md). In brief: build an App Bundle, enrol in Play App Signing, complete the health apps declaration (medical functionality, not a regulated medical device, in-app disclaimer present), complete the data safety form (no data collected or shared), and publish the privacy policy URL (`docs/PRIVACY.md` via GitHub Pages).
