# Changelog

## Unreleased
- Background worker threads no longer keep the process alive, which fixed test runs hanging on multi-core CI machines
- CI: 10-minute limit per test, 30-minute job limit, and a newer push cancels the older run

## 1.5.0
- JPEG 2000 decoding (lossless and lossy) through the bundled JJ2000 decoder, verified pixel-exact against OpenJPEG on lossless files
- PACS downloads 10–30× faster: whole series per request, three series in parallel, connection reuse, and parallel per-image fallback. Progress shows images, size, and speed
- Measurement tools open as a rail right under the viewport's pencil. The selection bar floats inside the viewport
- Loop speed slider from 1 to 60 fps in the viewer and Settings
- Redesigned Settings with grouped cards and explanations
- Home screen: removed the side menu that duplicated the tabs. Added PACS and Settings icons, tab counts, and tab explanations
- Versioned APK file names

## 1.4.1
- PACS: fixes HTTP 406 by trying the standard and plain JSON formats, detecting web pages, and retrying searches without optional parameters
- Test connection looks for the DICOMweb path automatically
- Clear explanations for sign-in, address, network, VPN, and certificate problems

## 1.4
- Icon toolbars. Long-press any icon to see its name
- Select, reshape, move, duplicate, and delete measurements, with undo
- Measurements and key images saved per image
- Labels kept clear of the overlay text
- Full Settings screen and a searchable Guide to every tool
- Export and import of settings (passphrase-protected passwords) and study sets (ZIP with measurements, key images, albums; optional anonymization)

## 1.3
- Full MPR: true patient-space planes (including gantry tilt), oblique and double-oblique, trilinear interpolation, and MIP/MinIP/average slabs
- Curved MPR, and a 3D view with MIP and shaded volume rendering
- Save reformats as a new DICOM series

## 1.2
- Multiple PACS profiles, one per institution, with username/password, token, or no sign-in
- Search all profiles at once, and test a connection before saving
- Credentials encrypted with the Android Keystore
- Studies remember their source institution

## 1.1.2
- Fixed a crash when opening the PACS screen

## 1.1.1
- Fixed the home screen layout that hid the study list

## 1.1
- Redesigned home screen (tabs, study cards, import sheet, albums, transfers) and viewer (series dropdown, thumbnail strip, tools drawer, six layouts, cross-references, teacher mode)

## 1.0
- First release: DICOM parser, decoders (uncompressed, RLE, JPEG Lossless, JPEG baseline), viewer with measurements, basic MPR, DICOMweb client, anonymized export, app lock
