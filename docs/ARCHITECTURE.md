# Architecture

*Insula DICOM Viewer 1.9.0*

Insula is a single-module Android app written in plain Java, with no Gradle and no third-party Android libraries. Its only bundled dependency is the JJ2000 JPEG 2000 decoder. All UI is built in code.

## Layers

```
 UI ─────────────────────────────────────────────────────────────────────────
   MainActivity        home: studies, albums, transfers, import
   ViewerActivity      viewports, thumbnails, tools menu, cine, sync, export
   MprActivity         MPR / curved MPR / 3D views, save reformats
   PacsActivity        profiles, search, path discovery
   SettingsActivity    settings;  GuideActivity  in-app guide
   DicomView           one viewport: rendering, gestures, overlays, measurements
   MeasureBar          tool rail and selection bar;  Ui, Icons  components, icons
   SwipePager          home-screen pages (Studies, Albums, Transfers) that follow a swipe
   StudyType, Windows  study-type recognition, quick-tool profiles, window presets
   StudyQuality, SmartUi   series quality report; hematoma and quality dialogs
 Imaging ────────────────────────────────────────────────────────────────────
   Dicom               PS3.10 parser (VRs, sequences, fragments), dictionary
   PixelDecoder        uncompressed, RLE, JPEG Lossless, JPEG (Android), palette, YBR
   J2k  →  ucar.jpeg.jj2000.*    JPEG 2000 frames
   RawImage            decoded frame + calibration; windowing to ARGB
   Volume              patient-space volume: resampling, slabs, curved, ray casting
   Seg                 3D tissue separation, table removal, heart isolation, manual edits
   DicomWriter         Explicit VR LE writer for derived series
 Data ───────────────────────────────────────────────────────────────────────
   Library             import, study/series model, slice ordering, frame cache
   AnnStore            measurements, annotations, key images (by SOP UID + frame)
   Store               albums, transfers, PACS profiles, study sources
   Vault               Android Keystore AES-GCM for PACS secrets
   Backup              settings files, study sets;  Anonymizer  de-identification
   Downloader          WADO-RS retrieval;  ShareProvider  file sharing
```

## Key design decisions

- **Plain Java, no Gradle.** The app builds with Ubuntu's packaged tools (`aapt`, `javac`, `dalvik-exchange`, `apksigner`). This keeps the build small and reproducible, but limits the compile API to Android 6 (API 23). Moving to API 36 for Google Play requires Google's SDK; see [RELEASING.md](RELEASING.md).
- **Patient-space geometry.** Slices are ordered along the image-plane normal. MPR builds a voxel-to-patient affine from Image Position and Orientation, so any acquisition direction and gantry tilt reconstructs correctly.
- **Stable identifiers.** Annotations and key images are keyed by `SOPInstanceUID#frame`, so they survive re-import, study-set transfer, and anonymization (which keeps UIDs).
- **Local-first privacy.** Everything lives in app-private storage with cloud backup disabled. Network access happens only for PACS profiles and user-supplied links.
- **Daemon background threads.** All executors use daemon threads (`Library.daemon`) so background work never keeps a process alive.

## Storage layout (app-private)

| Path | Contents |
|---|---|
| `files/library/*.dcm` | Imported DICOM instances, one file per SOP Instance UID |
| `files/annotations.json` | Measurements, annotations, and key images |
| `shared_prefs/insula.xml` | Preferences |
| `shared_prefs/insula_store.xml` | Albums, transfers, PACS profiles (secrets encrypted), study sources |
| `cache/exports/` | Files being shared or saved, served by `ShareProvider` |

## Threading

The UI thread renders frames synchronously from an LRU frame cache holding about a quarter of the heap. A background executor prefetches ±4 neighboring slices and another generates thumbnails. Volume resampling and 3D ray casting are split across CPU cores. PACS downloads use 3 series workers, or 4 instance workers on fallback, plus a progress thread.

## Testing

`tests/` runs the real classes on a desktop JVM against reference implementations and mock servers. Android-only APIs (BitmapFactory, UI) aren't exercised there. See [VALIDATION.md](VALIDATION.md).
