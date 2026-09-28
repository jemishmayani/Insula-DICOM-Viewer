# Security policy

Insula handles medical images, so security and privacy reports are welcome.

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting (Security tab › "Report a vulnerability") instead of a public issue. Include the app version, the Android version, and steps to reproduce.

**Do not include real patient data** in reports, issues, or pull requests. Use pydicom's public test files or anonymized images.

## Scope

In scope:
- Leaks of patient data (exports, logs, other apps, backups)
- Weaknesses in credential storage (Android Keystore use, passphrase-protected settings files)
- Parsing bugs that crash the app or corrupt data when opening a crafted DICOM file
- Anonymization gaps
