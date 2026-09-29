# Releasing

## Versioning

Versions follow `MAJOR.MINOR.PATCH`. For each release:

1. Update `android:versionCode` (+1) and `android:versionName` in `AndroidManifest.xml`.
2. Add an entry to `CHANGELOG.md`.
3. Update the version line at the top of each document in `docs/` if its content changed.

## Building a signed APK

```bash
export INSULA_KEYSTORE=/path/to/release.jks
export INSULA_KEYSTORE_PASS='…'
export INSULA_KEY_ALIAS=insula
./build.sh
tests/run_tests.sh
```

The APK is written to `out/InsulaDICOMViewer-v<version>.apk`. Never commit the keystore.

## Publishing on GitHub

There are two ways. Both run the full test suite before anything is published, and both produce releases that install over earlier versions.

### A. Signed locally (no secrets needed)

1. Build and sign locally with your release key (see above).
2. Commit the APK to the `release-binaries` branch as `InsulaDICOMViewer-v<version>.apk`, with optional release notes in `notes/v<version>.md`.
3. Push the tag, for example `git tag v1.6.0 && git push origin v1.6.0`.

The `publish-prebuilt` job checks that the APK is signed with the certificate in `.github/release-signing-cert.sha256` and that its version matches the tag. It then creates the release with the notes and the APK's SHA-256 checksum.

### B. Signed by GitHub Actions


With the repository secrets `INSULA_KEYSTORE_BASE64`, `INSULA_KEYSTORE_PASS`, and `INSULA_KEY_ALIAS` set:

```bash
git tag v1.6.0
git push origin v1.6.0
```

GitHub Actions builds, tests, signs, and attaches the APK to a GitHub Release. Without the secrets, the APK is only available as a workflow artifact, signed with a temporary debug key.

## Before a public release

- [ ] Create a new release key with a strong password (the current key's password has been shared), and keep two offline backups.
- [ ] Recreate the repository if earlier commits must not stay public.
- [ ] Enable GitHub Pages (Settings › Pages › Branch `main`, folder `/docs`) so the privacy policy has a public URL.
- [ ] Register as a verified Android developer. Google's developer verification applies to sideloaded apps in some countries from 30 September 2026 and globally from 2027.
- [ ] Check the name "Insula" for trademark conflicts.

## Google Play (not yet ready)

| Requirement | Status |
|---|---|
| Target API level 36 (required for new apps and updates since 31 August 2026) | **To do.** Needs Google's Android SDK instead of Ubuntu's API 23 jar, predictive back (Android 16 no longer calls `onBackPressed` for apps targeting API 36), and edge-to-edge layouts |
| Android App Bundle (.aab) | **To do.** The build currently produces an APK |
| Play App Signing | Enrol when creating the app |
| Privacy policy URL | `docs/PRIVACY.md` via GitHub Pages |
| Health apps declaration | Declare medical functionality; not a regulated medical device; in-app disclaimer present |
| Data safety form | No data collected or shared. Encryption: data stays on the device; network traffic uses HTTPS where the server supports it |
| Store listing | Describe your own features; don't compare with or name other apps; use anonymized screenshots only |
