# Third-party notices

Insula's own code is licensed under the GNU GPL v3 or later (see [LICENSE](LICENSE)). The components below keep their own licences. The additional permission in [NOTICE](NOTICE) allows Insula to be distributed combined with JJ2000.

## JJ2000 (JPEG 2000 decoder)

- Source: https://github.com/Unidata/jj2000 (package `ucar.jpeg.jj2000`)
- Included in `src/ucar/jpeg/`. The display (`disp/`), command-line (`Decoder`, `CmdLnDecoder`, `SimpleAppletDecoder`), and `JJ2KDecoder`/`JJ2KEncoder` wrapper classes are omitted. Files were converted from ISO-8859-1 to UTF-8; code is otherwise unmodified.
- Used by `src/com/insula/dicomviewer/J2k.java` to decode JPEG 2000 frames in DICOM files.
- License: the JJ2000 copyright notice, reproduced in full in [licenses/JJ2000-COPYRIGHT.txt](licenses/JJ2000-COPYRIGHT.txt). It must be included in all copies or derivative works. It grants rights for use in products claiming conformance to the JPEG 2000 standard, and it notes that use may be subject to third-party patents.

## Test-only dependencies (not shipped in the app)

- [pydicom](https://github.com/pydicom/pydicom) and its test data, [pylibjpeg](https://github.com/pydicom/pylibjpeg) plugins (OpenJPEG, libjpeg), and NumPy, used as reference decoders.
- [JSON-java (org.json)](https://github.com/stleary/JSON-java), downloaded by `tests/run_tests.sh` because Android's copy is a stub on a desktop JVM.
