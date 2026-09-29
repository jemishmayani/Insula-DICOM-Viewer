# Privacy policy

*Insula DICOM Viewer for Android. Effective 29 September 2026.*

Insula is designed so that your images stay on your phone. This policy explains what the app does with data and what it does not do.

## The short version

- The developer **does not receive** your images, patient details, measurements, or settings.
- There are **no accounts, ads, analytics, tracking, or crash reporting**.
- The app connects only to servers **you** choose: the PACS servers you configure and the download links you enter.

## Data the app processes on your device

| Data | Where it is kept | Leaves the phone? |
|---|---|---|
| DICOM images you import or download, including patient and study details inside them | App-private storage | Only if you share or export them |
| Measurements, annotations, key-image marks, albums, transfer history | App-private storage | Only inside a study set or settings file you export |
| PACS profiles (server address, username) | App-private storage | Only inside a settings file you export |
| PACS passwords and access tokens | Encrypted with a key held in the Android Keystore | Only if you export settings *and* choose to include them, protected by a passphrase you set |
| Preferences (lock, teacher mode, loop speed) | App-private storage | Only inside a settings file you export |

App-private storage is inaccessible to other apps and is encrypted by Android on modern devices. The app opts out of Android cloud backup (`allowBackup="false"`), so its data is not copied to your Google account. Uninstalling the app deletes all of it.

## Network connections

The app uses the internet permission only to:

- search and download studies from PACS servers you have added as profiles
- download files from links you enter or open with the app

These connections go directly from your phone to that server; the developer does not operate them or see their traffic. Plain `http://` addresses are unencrypted, and the app warns you when you save one.

## Sharing and export

Images, PDFs, ZIP archives, study sets, and settings files leave the app only when you choose Share or Save, through Android's standard share sheet and file picker. Where they go next is controlled by you and the app you pick. You can hide patient details in exported images (Settings › Hide patient details in exports), and you can export anonymized copies. Anonymization does not remove text burned into image pixels.

## Permissions

| Permission | Why |
|---|---|
| Internet | PACS search and download, and downloads from links you provide |

Files are opened through Android's file picker, which grants access only to what you select. The app does not request access to storage, camera, location, contacts, or the microphone.

## Children

The app is not directed at children and does not knowingly collect data from anyone.

## Your rights

Because the developer does not collect or hold your personal data, there is nothing for us to access, correct, or delete on our side. You control all data in the app and can delete it at any time: delete individual studies, use Settings › Delete all studies, or uninstall the app.

If you handle other people's medical images, you may have your own obligations under data-protection law, such as India's Digital Personal Data Protection Act, 2023, the GDPR, or HIPAA. Use app lock, screenshot blocking, and anonymized export to help meet them.

## Changes to this policy

Changes are published in this file in the source repository, and the effective date above is updated. If a future version ever starts collecting data, this policy will say so before that version is released.

## Contact

Questions about privacy: open an issue at <https://github.com/jemishmayani/Insula-DICOM-Viewer/issues> (never attach patient images), or email jemishmayani1403@gmail.com.
