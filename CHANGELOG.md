# Changelog

## 1.9.2
- **3D VRT is labelled Beta** throughout (screen title, quick bar, menus, Smart tools, Guide), with a one-time notice explaining its limits, how to correct results, and how to report problems
- New app icon from the project's logo artwork: an adaptive icon (Android 8+) that launchers can shape without clipping, a monochrome layer for Android 13+ themed icons, and a rounded icon for Android 7
- The logo appears on the Welcome and About screens
- Brand assets in docs/assets: the logo as SVG (with and without background) and a 1280 x 640 GitHub social preview

## 1.9.1
- **Faster scrolling:** slices you scroll past are no longer decoded one by one before the one you stop on; only the slice on screen is decoded (a fast scroll through 30 uncached slices now decodes 1, not 30). Decoding the visible slice also runs at a higher priority than background prefetching
- **3D: CT table and cradles removed whatever their shape**, including curved head and body cradles and tables touching the skin: thin shells outside the body are removed by thickness, and dense material lying on the outer surface (cradles, tables, ECG leads) is recognized because body tissue is never dense at the skin surface
- Tests: curved cradle on a foam pad and touching the skin, with the body and thin internal vessels kept; fast-scroll decoding

## 1.9.0
- **Smart tools by study type:** recognizes cervical spine, CVJ, spine, brain, cardiac/coronary CTA, CTA, CTPA, trauma, neck, chest, and abdomen from the study and series descriptions, body part, protocol, and contrast. The Smart button in the quick bar offers suited window presets, MPR planes, MIP/MinIP/thin-MIP slabs, measurements, and 3D (for example 3D bone for spine and trauma)
- **Measurement shortcuts** with how-to guidance and commonly cited reference values (for guidance only): ADI, BDI, Chamberlain, McGregor, canal diameter, vertebral body height, Cobb, midline shift, and hematoma volume
- **New measurements:** point to line (perpendicular distance) and hematoma volume (ABC/2)
- **Window presets strip** whenever the brightness tool is on, in the viewer and MPR, chosen for the study type; more presets (posterior fossa, sinus, neck, spine, soft tissue, pulmonary embolism, blood/hematoma)
- **MPR:** three planes with axial on top is the default layout; Settings › MPR sets the layout, the 3D view style, and plane linking. A link button chooses whether tilting a line turns both other planes or only that one; a Reset button restores axial, coronal, and sagittal
- **3D tissue separation:** lungs are kept where a small field of view cuts through them (cardiac CT); the CT table and mattress are removed; heart isolation for cardiac CTA (automatic, undoable) keeps the heart, great vessels, coronaries, and myocardium and hides chest wall, spine, ribs, lungs, and pulmonary vessels
- **3D Cut:** an icon bar replaces the text menu; long-press an icon to see what it does
- **Study quality:** slice spacing and gaps, pixel size, voxel shape, compression, acquisition settings, and a measured noise estimate, with verdicts for MPR and 3D
- Viewer menu: Scroll, Brightness, and Measure removed (they are in the quick bar); Quick tools and Study quality added
- Clearer empty screens, progress bars with percentages, and plain-language error messages
- New app icon: stacked image slices with an axial brain, the front slice highlighted
- Home screen: swipe between Studies, Albums, and Transfers
- Smart tools: neutral action buttons that wrap onto new rows (no longer look like switched-on toggles); MR studies no longer offer CT windows or 3D skull
- Measurement shortcuts: the result bar updates while you edit the measurement, shows the hematoma volume once the slice count is entered, and closes if the measurement is deleted
- Tests: study-type recognition and profiles, presets, point-to-line geometry, Study Quality (noise estimate within 0.2 HU of the phantom), heart isolation, small field of view, table and mattress removal

## 1.8.0
- **3D VRT**, a separate mode (quick bar › 3D, a thumbnail's long-press menu, or MPR › Open in 3D VRT):
  - GPU ray casting with OpenGL ES 3.0 (volume as a half-float 3D texture, tissue classes as a label texture, per-class transfer functions), rendered at reduced resolution while dragging and sharpened on release
  - Automatic tissue separation for CT with thresholds measured from each scan: skin and fat, organs and soft tissue, vessels and heart chambers, bone, lungs, calcium, plus a Selection class. Bone is cortical bone plus what it encloses in cross-section, so vessels touching bone stay vessels; compact dense spots are calcium. MR and other scans use intensity bands
  - Manual refinement: per-class visibility, colour, opacity, and density range; rerun separation with your own thresholds; Pick (hide, show only, or move a structure to another class); Cut (remove, keep, or reclassify inside a drawn outline, through the full depth); clip box; undo
  - Presets (coronary CTA, vessels, bones, all tissues, lungs, vessel MIP, skin), VRT/MIP/MinIP/Surface modes, lighting, background, standard views, spin, orientation letters
  - Captures: save or share a PNG, add the image to the study (Secondary Capture), or add a 36-view rotation series
  - Sessions saved inside the study as DICOM (series "Insula 3D VRT states"), including the edited tissue map, so work continues across sessions and travels with study-set exports. Anonymized exports keep the session block
  - Quality tiers chosen from the phone's memory and graphics (High, Standard, Low, and Basic CPU mode for phones without OpenGL ES 3.0), with automatic fallback if the GPU runs out of memory, and in-app instructions
- Tests: tissue separation on a synthetic coronary CTA phantom (bone, vessels, lungs, soft tissue, aorta touching the spine, coronary with calcified plaque), edits and undo, session round trip, and the GPU shader compared pixel by pixel with the CPU renderer on Mesa

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
