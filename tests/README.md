# Tests

`run_tests.sh` compiles the app with `../build.sh` if needed, then runs its real classes on a desktop JVM against reference implementations and mock servers. No emulator or device is needed.

```bash
pip install -r tests/requirements.txt
tests/run_tests.sh
```

The first run downloads pydicom's test data and the org.json sources, so it needs network access. Output goes to `tests/.work/`, which is ignored by git.

| Suite | What it checks |
|---|---|
| Decoders vs pydicom/OpenJPEG | Every decodable pydicom test file, pixel for pixel. Lossless must match exactly; lossy JPEG 2000 may differ by 2 levels. JPEG baseline uses Android's BitmapFactory, so it's skipped on the desktop. |
| MPR geometry | Phantoms with known sizes: true planes from axial, sagittal, and gantry-tilted acquisitions; oblique planes; MIP/MinIP/average slabs; curved MPR; 3D MIP and volume rendering. PNG renders are written to `tests/.work/`. |
| DICOM writer | A saved oblique MIP reformat is re-read by the app and by pydicom, and its geometry tags locate the phantom correctly. |
| Backup | Passphrase encryption (a wrong passphrase is rejected); annotation store export, merge, dedupe, and delete; study-set ZIP import; anonymized copies keep their measurements. |
| Smooth viewing | Windowing output identical to the reference at 512² and 3000²; drag previews sample the full result; MPR drag previews keep size and spacing and are faster; 8 threads loading one slice share one decode; background loading and direction-aware prefetch. |
| Demo and comparison | Demo prior and current studies (no duplicates when recreated); Compare finds the patient's other study and its matching series; linked scrolling lands on the same anatomy across studies despite a 6 mm shift; parallel MPR build puts the lesion and skull where they were drawn. |
| 3D VRT: tissue separation | A synthetic coronary CTA phantom with known truth: contrast detection, bone, vessels, lungs, soft tissue, fat, the aorta touching the spine, a thin coronary, and calcified plaque; picking, moving, hiding, cutting, undo; half-float HU upload; session JSON round trip; heart isolation (chambers, coronaries, myocardium kept; bone, lungs, chest wall hidden; undo); a small field of view with scanner padding cutting the lungs; CT table and mattress removal. |
| 3D VRT: GPU shader | Runs `res/raw/vrt_frag.glsl` on Mesa (moderngl, EGL) and compares five scenes pixel by pixel with the Java renderer. Skips if no OpenGL context is available. |
| 3D VRT: sessions and captures | Sessions saved into a study as DICOM, listed, reopened exactly, opened at another quality level, kept by anonymization, deleted; captures reopened pixel-exact. |
| Study types and quality | 15 study descriptions recognized correctly; profiles contain the requested tools; MR studies get no CT windows or 3D skull; every preset and shortcut exists; point-to-line geometry; Study Quality on the demo (spacing, verdicts, noise estimate within 0.2 HU of the phantom). |
| Update checker | Version comparison (including v1.10 vs 1.9.9 and pre-release tags) and picking the APK from GitHub's release data, with a fallback to the releases page. |
| PACS: two institutions | Password and token sign-in against two mock servers; a wrong password gives a clear 401. |
| PACS: HTTP 406 | Finds DICOMweb behind a portal and at the dcm4chee path, falls back to plain JSON, retries without optional search parameters after a 400, and explains an unknown host. |
| PACS: download speed | 180 images at 120 ms latency, old one-by-one approach vs the new downloader. Also tests the per-image fallback and the streaming multipart parser with 1–9 byte reads. |

`shim/` holds a desktop stand-in for `android.util.Base64`. `python/` holds the mock servers and comparison scripts.
