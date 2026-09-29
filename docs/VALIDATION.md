# Verification and validation report

*Insula DICOM Viewer 1.5.1. Last updated 29 September 2026.*

This report summarizes automated **software verification**: whether the software does what it was designed to do. It is **not clinical validation**, and it does not establish fitness for diagnostic use (see [INTENDED_USE.md](INTENDED_USE.md)).

All results below come from `tests/run_tests.sh`, which runs the app's own classes on a desktop Java VM against independent reference software. GitHub Actions runs the suite on every push; the first full run (run 36522745128, commit 6a30d56) passed in 86 seconds, 50 of them in the tests.

## 1. Image decoding

**Method.** Every pydicom test file, plus additional JPEG Lossless, RLE, and JPEG 2000 files from pydicom-data, is decoded by Insula and by pydicom, which uses pylibjpeg-libjpeg and pylibjpeg-openjpeg (OpenJPEG) for compressed data. Decoded pixel values are compared one by one.

**Acceptance.** Uncompressed and lossless data must match exactly. Lossy JPEG 2000 may differ by at most 2 levels, because conforming decoders round 9/7 wavelet results differently.

**Result: pass.** 51 of 51 comparable files match the reference.

| Encoding | Files | Result |
|---|---|---|
| Uncompressed: implicit, explicit, big endian, deflated; 1–32 bits; signed and unsigned; palette color; YBR 4:2:2 | 22 | Pixel-identical |
| RLE Lossless: 8-, 16-, 32-bit, grayscale and color | 10 | Pixel-identical |
| JPEG Lossless (Process 14, SV1): 8- and 16-bit | 3 | Pixel-identical |
| JPEG 2000 lossless: including tiled, 16-bit, signed, RCT color, and a JP2-wrapped codestream | 10 | Pixel-identical |
| JPEG 2000 lossy (9/7) | 6 | Within 1–2 levels |
| **Total** | **51** | **51 pass** |

**Not comparable on a desktop:** JPEG Baseline (decoded by Android's BitmapFactory on the device) and objects without pixel data.

**Found and fixed during verification:**
- 16- and 32-bit RLE color was clamped instead of scaled.
- 8-bit data stored as OW in big-endian files was byte-swapped.
- Lossy JPEG 2000 values overshooting the valid range were not clipped.
- A JP2 container with an unknown box failed to decode.

## 2. Multiplanar reconstruction and 3D

**Method.** Synthetic volumes with known geometry: a 40 mm sphere at a known position, an 8 mm rod, and a body cylinder, sampled as real series would be.

| Check | Acceptance | Result |
|---|---|---|
| Sphere diameter in axial, coronal, and sagittal planes (axial acquisition) | 40 ± 3 mm | 41.0 mm in each plane |
| True axial plane from a sagittal acquisition | 40 ± 3.5 mm | 40.0 mm |
| Sagittal plane from a gantry-tilted acquisition | 40 ± 3.5 mm | 40.0 mm |
| 30° oblique plane through the rod | Rod visible along its length | 101/101 rows |
| Thin slice 30 mm from the sphere | Sphere not visible | Maximum 500 (rod only) |
| 30 mm MIP slab | Sphere visible | Maximum 1000 |
| MinIP slab | Air retained | Minimum −1000 |
| Curved MPR along an arc through the sphere | Sphere across ≥ 80% of the path | 37/37 columns |
| 3D MIP projected area | Within 15% of πr² | 1094 vs 1257 mm² |
| Shaded volume rendering | Surfaces rendered | Pass; renders inspected visually |

**Result: pass (12/12).** Cross-reference line geometry was also verified: a sagittal plane intersected an axial image at the predicted column (61.01 vs 61.01).

## 3. Created DICOM objects

A saved oblique MIP reformat is re-read by Insula's parser (pixel-identical) and by pydicom, which recognizes it as CT Image Storage in Explicit VR Little Endian. Using only the file's geometry attributes, the phantom sphere's center maps to a pixel of value 1000, as expected. **Result: pass.**

## 4. Backup, annotations, and study sets

| Check | Result |
|---|---|
| Passphrase encryption hides credentials; the correct passphrase opens them; a wrong one is rejected | Pass |
| Annotation export limited to the chosen images; unfinished measurements excluded | Pass |
| Import restores measurements, labels, and key-image marks without duplicates on re-import | Pass |
| Deleting a series removes its annotations | Pass |
| Study-set ZIP: DICOM imported and manifest detected | Pass |
| Anonymized copies keep SOP UIDs (so measurements re-attach) and have no patient name | Pass |

**Result: pass (14/14).**

## 5. PACS (DICOMweb)

Mock servers stand in for real PACS; no real hospital system was used.

| Check | Result |
|---|---|
| Two institutions, one with username/password and one with a bearer token: search and download | Pass |
| Wrong password gives a clear HTTP 401 message | Pass |
| DICOMweb found behind a web portal (`/dicom-web`) and at the dcm4chee path | Pass |
| Server refusing `application/dicom+json` (HTTP 406): fallback to `application/json` | Pass |
| Server rejecting optional search parameters (HTTP 400): retry with filters only | Pass |
| Unknown host explained in plain language | Pass |
| Download of 180 images at 120 ms network latency | 30.6 s one-by-one (v1.4) vs **1.0 s** series-level (v1.5): 31× faster |
| Server without series-level retrieval: parallel per-image fallback | 180 images in 3.4 s |
| Streaming multipart parser with 1–9 byte network reads and delimiter-like payloads | 25/25 parts byte-identical |

**Result: pass.**

## 6. Not yet verified

- Behaviour on physical devices across Android versions, screen sizes, and vendors (manual testing so far)
- Touch-gesture usability
- Interoperability with specific commercial PACS products
- Rendering of JPEG Baseline through Android's BitmapFactory (device-only)
- Performance on low-memory phones with very large series

## Reproducing

```bash
./build.sh
pip install -r tests/requirements.txt
tests/run_tests.sh
```

Reference-software versions are pinned in `tests/requirements.txt`.
