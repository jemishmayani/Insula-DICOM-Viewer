# Changelog

## 1.7.0
- **Smoother scrolling:** slices are decoded in the background, so the screen never waits. Several slices decode in parallel, and prefetching reads ahead in the scrolling direction (the whole series when it fits in memory)
- **Faster window/level:** redraws at most once per frame, spread across CPU cores, with a reduced preview while dragging on very large images
- **Faster MPR:** slices are decoded in parallel when the volume is built; planes render at half resolution while you drag, then at full quality
- **Gestures:** flick to glide through a series, a scrub bar along the right edge, and long-press for window presets
- **Quick bar:** Scroll, Window, Measure, Presets, Play, Compare, Layout, Link, Align, Reset, and More, one tap each. Study details moved to the patient name
- **Compare:** a prior or later study of the same patient side by side, with the best-matching series and linked scrolling across studies (lined up by stack centres, or by **Align** from the slices shown)
- **Remembered window settings** per series (Settings › Viewer › Remember window settings)
- **First use:** a welcome screen with the intended-use confirmation, a synthetic demo study (prior and current), an actionable empty home screen, and a one-time tips card in the viewer
- Settings: the header now only shows version and library information; About has its own row
- Guide: collapsible sections with item counts, Expand/Collapse all, and search that opens matching sections
- **Android 16:** targets API level 36, with edge-to-edge layout and predictive back on all supported versions (Android 7.0 and later)
- Tests for windowing, previews, concurrent loading, prefetch, demo generation, comparison alignment, and parallel MPR building

## 1.6.0
- New About page (Settings › About Insula, or tap the card at the top of Settings): version, disclaimer, links to the guides, source code, releases, issues, and privacy policy, licence texts, and developer contact
- Check for updates: asks GitHub for the latest release only when tapped, and offers the download
- The GNU GPL, NOTICE, and JJ2000 licence texts are bundled in the app, as the GPL requires for distributed copies
- Copy app and device info for bug reports; Report a problem warns never to attach patient images
- The first-launch disclaimer shows the installed version
- Releases can be published from a locally signed APK: the workflow checks the signing certificate and version before attaching it

## 1.5.1
- Licensed under the GNU GPL v3 or later, with an additional permission for the bundled JJ2000 decoder (see NOTICE). Source files carry SPDX licence headers
- About shows the copyright, licence, no-warranty notice, and a link to the source code. Settings has a new "Source code and license" row
- Documentation: user guide, intended use and limitations, privacy policy, DICOM conformance statement, verification report, architecture, and release checklist (docs/, publishable with GitHub Pages)
- Created DICOM files now carry the implementation version name INSULA_1_5
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
