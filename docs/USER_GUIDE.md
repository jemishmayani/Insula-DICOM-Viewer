# User guide

*Insula DICOM Viewer 1.9.0*

The same information is in the app under **Settings › Guide**, with search. Long-press any icon in the app to see its name.

> Insula is for reference, teaching, and patient use, not primary diagnosis. See [Intended use](INTENDED_USE.md).

**Contents:** [Getting started](#1-getting-started) · [Home screen](#2-home-screen) · [Viewer](#3-viewer) · [Measuring and annotating](#4-measuring-and-annotating) · [MPR and 3D](#5-mpr-and-3d) · [3D VRT](#3d-vrt) · [PACS](#6-pacs-dicomweb) · [Sharing and privacy](#7-sharing-and-privacy) · [Settings and backup](#8-settings-and-backup) · [Troubleshooting](#9-troubleshooting)

---

## 1. Getting started

1. Download the latest APK from [Releases](https://github.com/jemishmayani/Insula-DICOM-Viewer/releases) and open it. Allow installing from that source if Android asks.
2. On first launch, the welcome screen explains what Insula does. Tick the box confirming that it isn't for primary diagnosis.
3. Choose **Explore with a demo study** to try every tool on a synthetic CT head phantom (a prior and a current study, no real patient), or **Import my own studies**.

Later, an empty home screen offers the same choices: Import studies, Search a hospital PACS, or Try the demo study.

Requires Android 7.0 or newer. Tablets and landscape orientation are supported.

## 2. Home screen

### Top bar

| Icon | Action |
|---|---|
| Search | Filter studies by patient name, ID, description, accession number, modality, date, or series description |
| Server | PACS: search hospital servers and download studies (section 6) |
| Gear | Settings, Guide, backup, and About (section 8) |

### Tabs

- **Studies:** your library. Each card shows the patient with age/sex, description, source institution (for PACS downloads), date, size, and a modality badge. Tap to open; long-press for details, albums, export, or delete. **Sort** orders by newest, oldest, patient name, modality, or size.
- **Albums:** collections you make, such as teaching files or one patient's follow-ups. A study can be in several albums. Long-press a study to add it; long-press an album to rename, delete, or export it.
- **Transfers:** a log of imports, PACS downloads, and exports, with sizes, speeds, and the reason for any failure.

### Importing (the + button)

| Source | Use it for |
|---|---|
| File(s) | DICOM files, ZIP archives, and Insula study sets |
| Folder | A copied patient CD, a USB drive, or any folder. Subfolders are scanned and non-DICOM files skipped |
| Download link | A direct link to a DICOM file or ZIP (not a web page) |
| DICOM query | Search and download from a PACS |

You can also share DICOM or ZIP files to Insula from other apps. Duplicates, identified by SOP Instance UID, are skipped.

## 3. Viewer

### Layout of the screen

- **Top bar:** Back, patient name with age/sex (tap it for study details), **Share**, and **Tools menu**.
- **Viewport:** the image, with the **series name** (tap to change series) at the top left and the **pencil** (measure and annotate) at the top right.
- **Corners:** WL and WW (window level and width), SE (series number), and IM (image number of total). The slice location and thickness appear above WL.
- **Edges:** orientation letters (A anterior, P posterior, R right, L left, H head, F feet). They follow rotation and flipping.
- **Quick bar:** one tap each for Scroll, Window, Measure, Presets, Play, Compare, Layout, Link and Align (with two or more viewports), Reset, and More (the full tools menu).
- **Thumbnail strip:** the study's series with image counts. Tap to show a series in the selected viewport; long-press for MPR, DICOM tags, or anonymized export.
- **Scrub bar:** the thin line along the right edge. Drag it to jump anywhere in the series.

A tips card appears the first time you open the viewer.

### Gestures

| Gesture | Effect |
|---|---|
| Pinch | Zoom |
| Two-finger drag | Pan |
| Double tap | Fit the image to the viewport |
| One-finger drag | Follows the selected tool: scroll, window, or measure |
| Flick (Scroll tool) | Keeps scrolling and slows down on its own; touch to stop |
| Drag along the right edge | Jumps through the series |
| Long-press (Scroll, Window, or Pan tool) | Window presets |

Slices load in the background, so scrolling never pauses for a slow image: the previous slice stays until the next is ready, and the app reads ahead in the direction you're scrolling.

### Tools menu

| Item | What it does |
|---|---|
| Scroll / Brightness / Measure | Chooses what a one-finger drag does. Brightness: left/right changes contrast (width), up/down changes brightness (level) |
| Link | Keeps viewports at the same position while scrolling, when their series share a frame of reference |
| Loop and Loop speed | Plays the series as a movie, from 1 to 60 frames per second |
| Layouts | 1, 2 stacked, 2 side by side, 3 stacked, 3 in a row, or 2 × 2. Empty viewports fill with the next series |
| Presets, Invert, Rotate, Flip H, Flip V | Image adjustments for the selected viewport |
| Annotations / Measures | Show or hide arrows and labels, or measurements |
| Cross-references | Dashed lines showing where other viewports' slices cut this image |
| Teacher mode | Hides the patient's name and IDs on screen and in exports |
| MPR (3 planes) | Opens reconstruction for the selected series |
| DICOM tags | Lists every attribute of the current image, with search |
| Reset | Resets zoom, pan, rotation, flips, inversion, and window |

**Remembered window settings.** When you change brightness or contrast (Window tool or a preset), the setting is saved for that series and used the next time you open it. **Reset** forgets it. Turn this off in Settings › Viewer.

**Window presets:** Brain (W 80 / L 40), Subdural (215/75), Stroke (40/40), Temporal bone (2800/600), Lung (1500/−600), Mediastinum (350/50), Abdomen (400/40), Liver (150/60), Bone (1800/400), Angio (600/300), plus the file's default and full range.

### Smart tools for the study type

Insula recognizes the kind of study from its descriptions (for example CT cervical spine, CT brain, coronary CTA). **Smart**, the first button in the quick bar, shows the recognized type and opens tools suited to it: window presets, MPR planes, MIP and MinIP slabs, measurements, and 3D. Measurement shortcuts such as ADI, BDI, midline shift, and hematoma volume show where to measure and, afterwards, a commonly cited reference value (for guidance only).

With the **Window** tool, a row of presets appears above the quick bar. **Study quality** (Tools menu or Smart) reports slice spacing, gaps, resolution, compression, dose settings, and noise, with verdicts for MPR and 3D.

### Comparing with a prior study

Tap **Compare** in the quick bar. Insula lists the other studies of the same patient (same Patient ID) and shows the chosen one side by side, with the series that best matches the current one (same modality, plane, and description). Its header shows the study date in amber.

Scrolling is linked. Within one study, positions match exactly. Across studies, the stacks are first lined up by their centres; if the anatomy doesn't match, scroll either side to the same level and tap **Align**. From then on, both scroll together from there.

## 4. Measuring and annotating

Tap the **pencil** on a viewport. The tools appear as a rail directly below it, and the pencil turns into a ✓ that closes them.

| Tool | How to use it | Shows |
|---|---|---|
| Select and edit | Tap a measurement; drag a white handle to reshape it, or drag the measurement to move it | — |
| Length | Drag between two points | mm or cm (pixels if uncalibrated) |
| Angle | Drag the first arm from its end to the vertex, then drag or tap the second arm | Degrees |
| Cobb angle | Drag along one endplate, then along the other | Degrees between the lines |
| Ellipse / Rectangle ROI | Drag to draw | Area, mean, SD, min, max, pixel count (HU for CT) |
| Pixel value | Tap or drag | HU for CT, stored value, or RGB |
| Arrow with label | Drag from the point of interest outward, then type a label | Your text |
| Eraser | Tap a measurement | Removes it |
| Undo | — | Reverses the last change on this image |
| Key image | — | Marks the current image; export key images as PDF from Share |
| Clear marks on this image | — | Removes all marks on the image (Undo restores them) |

When a measurement is selected, a bar shows its value with **Edit label** (arrows only), **Duplicate**, **Delete**, and **Deselect**. In any drawing tool, touching an existing handle edits that measurement instead of starting a new one.

Measurements and key images on stored series are **saved automatically** and included in study sets. Measurements on MPR planes last until the planes change.

## 5. MPR and 3D

Open from the viewer's Tools menu (**MPR (3 planes)**) or by long-pressing a thumbnail. The series must be a single stack of at least 3 slices with position data.

**Views:** axial (red), coronal (green), sagittal (yellow), and a fourth view showing 3D or a curved reformat. Planes are true patient-space planes, whatever direction the series was acquired in.

| Tool | Use |
|---|---|
| Crosshair | Drag near the center to move through the volume. Drag a colored line where its dot is to tilt the other two planes (oblique); do it in two views for double-oblique |
| Scroll | Swipe to page through a plane |
| Window | Brightness and contrast, linked across planes (can be turned off). In 3D it changes which densities are visible |
| Pan | Moves the image |
| Draw a curve | Tap points along a vessel, canal, or dental arch; the fourth view shows it straightened. Swipe it to shift the curve sideways |
| Measure | The same tools as the viewer |
| 3D view options | Opens the menu |
| Maximize | Shows the selected view alone |

**Menu:** layout (4 views, 3 planes, single); slab mode (Thin, MIP, MinIP, Average) and thickness (2–80 mm); 3D mode (MIP, Bone, Soft tissue, Vessels); crosshair lines; linked window; presets; clear curve; **Save planes as a new series**; and **Reset orientation**.

**Saving reformats:** choose a plane and a spacing (1, 2, 3, or 5 mm). The whole volume is reformatted with the current slab settings and stored as a DICOM series in the same study.

### 3D VRT

A separate mode for volume rendering with tissue separation, like the 3D tools on CT consoles. Open it with **3D** in the viewer's quick bar, **3D VRT** in a thumbnail's long-press menu, or **Open in 3D VRT** in MPR. The series needs at least 10 slices.

**What happens when it opens.** The series is resampled into a 3D volume, and contrast CT is separated automatically into tissue classes: skin and fat, organs and soft tissue, vessels and heart chambers, bone, lungs, and calcium (plus an empty Selection class for your own groupings). Thresholds come from the scan's own histogram; a message shows what was detected, for example the blood-pool density. Bone is identified by its dense outer layer and what that layer encloses, so an aorta pressed against the spine stays a vessel. MR and other scans use simple intensity bands.

**Check the result.** Automatic separation distinguishes tissue types, not individual organs, and can mislabel structures, for example veins with undiluted contrast (which can look like bone) or unusual anatomy. Correct it with the tools below.

| Tool | Use |
|---|---|
| Drag / pinch / two fingers / double-tap | Rotate / zoom / move / front view |
| Presets | Coronary CTA, vessels only, bones, all tissues, lungs and airways, vessel MIP, skin surface |
| Tissues | Per class: show or hide, colour (tap the swatch), opacity, and density range. Rerun the automatic separation with your own vessel and bone thresholds |
| Pick | Tap a structure: hide it, show only it, or move it to another class |
| Cut | Draw an outline: remove inside, keep only inside, or move visible tissue inside to a class. It cuts through the full depth from the current direction |
| Clip | Remove slabs from each side |
| Views, Spin | Standard directions; continuous rotation |
| Display | VRT, MIP, MinIP, or Surface; MIP window; lighting; background; quality |
| Undo | Steps back through picks and cuts |

**Example: coronary arteries.** Choose the Coronary CTA preset. Pick the heart chambers (in Vessels) and choose Hide this structure, or Move to Selection and hide Selection in Tissues. Cut away ribs or sternum if they block the view, then rotate.

**Saving and continuing.** The save icon opens **Sessions**. A saved session stores the view settings and your edited tissue map inside the study, as the series "Insula 3D VRT states", so it's included in study-set exports (including anonymized ones) and can be reopened later. When you open 3D on a series with saved sessions, Insula offers to continue one.

**Captures.** The camera icon saves or shares a PNG, adds the image to the study as a DICOM image ("Insula 3D VRT captures"), or adds a **36-view rotation series** you can scroll in the viewer.

**Quality and limited mode.** Insula chooses a quality level for your phone, shown at the top left of the 3D view:

| Level | When | Effect |
|---|---|---|
| High | GPU, lots of memory, 6+ cores | Up to 320 voxels on the longest side |
| Standard | GPU, typical phones | About 256 voxels |
| Low | Low-memory phones | About 192 voxels; small vessels are softer |
| Basic (CPU) | No OpenGL ES 3.0 | Drawn by the processor at reduced resolution: rotation is slow and the image sharpens after you let go |

If the GPU runs out of memory, Insula drops a level automatically. You can choose a level in Display › Quality; higher than recommended may be slow or fail.

## 6. PACS (DICOMweb)

Insula connects to PACS servers that offer **DICOMweb**. Ask your hospital's IT team for the **DICOMweb (QIDO-RS/WADO-RS) base URL**, and whether it works from outside the hospital network.

### Profiles

Add one profile per institution with the gear icon on the PACS screen:

- **Name and institution**, for example "City Hospital".
- **DICOMweb address**, for example `https://pacs.example.org/dicom-web` or, for Orthanc, `http://host:8042/dicom-web`.
- **Sign-in:** username and password, access token, or none. Passwords and tokens are encrypted on the phone.
- **Test connection** checks the details. If the address isn't a DICOMweb root, it tries common paths and fills in the one that works.

Tap the profile card to switch servers or choose **All profiles** to search every server at once.

### Searching and downloading

Search by patient name (a trailing `*` is added automatically), patient ID, date or date range (shortcuts: Today, Yesterday, Last 7 days, Last 30 days), and modality. Tap a result to download it. Whole series are fetched in single requests, several at once, and the progress shows images, size, and speed.

## 7. Sharing and privacy

| Action | Where |
|---|---|
| Share the current image; save as PNG or JPEG | Viewer › Share |
| Key images as a PDF | Viewer › Share |
| Anonymized ZIP of a series or study | Viewer › Share, or long-press a study |
| Study set (studies with measurements, optionally anonymized) | Long-press a study or album, or Settings |

**Anonymization** blanks names, IDs, birth dates, addresses, accession numbers, institution and physician names, and all private tags. It keeps UIDs and dates, and it **does not remove text burned into the pixels**. Check images before sharing.

**Privacy settings:** app lock (device PIN or biometrics), screenshot blocking, hiding patient details in exports, and Teacher mode. See the [Privacy policy](PRIVACY.md).

## 8. Settings and backup

| Group | Settings |
|---|---|
| Viewer | Default loop speed (1–60 fps), Remember window settings, Teacher mode |
| Privacy and security | App lock, Block screenshots, Hide patient details in exports |
| PACS | PACS profiles |
| Backup and transfer | Export/import settings; export/import study sets |
| Help and legal | Guide; About Insula |
| Storage | Library size; Delete all studies |

- **Settings file (.json):** preferences, albums, and PACS profiles, for moving to a new phone. PACS passwords are included only if you protect them with a passphrase.
- **Study set (.zip):** DICOM files with their measurements, key images, and albums. Importing a set also creates an album with its name. Study sets open in any DICOM viewer; the extra data is used by Insula.

The card at the top of Settings shows the version and your library: studies, series, files, storage used, measurements, PACS profiles, and albums.

The in-app **Guide** (Settings › Guide) groups every tool into collapsible sections. Tap a section to open it, or use Expand all. Searching opens the sections with matches and shows only matching tools.

### About Insula

Open **Settings › About Insula**. It shows the version and has **Check for updates**, the medical-use disclaimer, links (in-app guide, online user guide, source code, all releases, report a problem, privacy policy), the full licence texts (GNU GPL v3, the NOTICE with the JJ2000 permission, and JJ2000's licence), and developer contact details. **Copy app and device info** copies your version, Android version, and phone model for bug reports.

### Updating the app

Insula isn't updated automatically. In **About Insula**, tap **Check for updates**; the app contacts GitHub only then. If a newer version exists, tap **Download**, open the downloaded file, and install it over the current version. Your studies, measurements, and settings are kept, because releases are signed with the same key. You can also download any version from [Releases](https://github.com/jemishmayani/Insula-DICOM-Viewer/releases).

## 9. Troubleshooting

| Message or problem | What to do |
|---|---|
| "Compression not supported yet: JPEG-LS…" | JPEG-LS, HTJ2K, and 12-bit JPEG aren't supported. Ask the sender for another transfer syntax, or view it on a workstation |
| "This object has no image" | It's a report or other non-image object. Open DICOM tags to read it |
| PACS: HTTP 406 or "returned a web page" | The address is probably the PACS website. Use Test connection, or ask IT for the DICOMweb URL |
| PACS: HTTP 401 or 403 | Check the username and password or token |
| PACS: "Can't find the server" or timeouts | The server may only be reachable on the hospital network or VPN |
| MPR: "Slices have different orientations" | The series mixes a localizer with the stack; choose a single stack |
| MPR: "reconstructed at reduced resolution" | The series was too large for the phone's memory; detail is reduced |
| An update won't install over the old version | The APK is signed with a different key (for example a test build). Use the APK from Releases, or uninstall first (this deletes the library) |
| Check for updates: "GitHub is limiting requests" | GitHub allows about 60 checks an hour per network. Try later, or open All releases in About |
