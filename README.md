# release-binaries

Signed release APKs for Insula DICOM Viewer, one per version, with release notes in `notes/`.

When a version tag (for example `v1.6.0`) is pushed to the main repository, the `publish-prebuilt` GitHub Actions job:
1. runs the full test suite,
2. checks that `InsulaDICOMViewer-v<version>.apk` here is signed with the certificate in `.github/release-signing-cert.sha256` and has the matching version,
3. publishes it as a GitHub Release with `notes/v<version>.md` and its SHA-256 checksum.

Download releases from the Releases page, not from this branch.
