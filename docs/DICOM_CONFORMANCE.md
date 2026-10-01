# DICOM conformance statement

*Insula DICOM Viewer 1.9.0 for Android. Last updated 29 September 2026.*

This statement follows the spirit of DICOM PS3.2. It describes which DICOM objects, encodings, and services Insula supports.

## 1. Overview

| Function | Support |
|---|---|
| Read DICOM files (PS3.10) from storage, folders, ZIP archives, and links | Yes |
| Retrieve from PACS over DICOMweb (QIDO-RS search, WADO-RS retrieve) | Yes, as a client |
| Create DICOM objects | Yes: derived MPR image series |
| Traditional DICOM networking (DIMSE: C-ECHO, C-FIND, C-MOVE, C-GET, C-STORE) | No |
| DICOMweb STOW-RS (store to PACS), UPS-RS | No |
| DICOMDIR | Not read; the referenced files are imported by scanning the folder |

**Implementation identity** (in File Meta Information of created objects):

| Attribute | Value |
|---|---|
| Implementation Class UID (0002,0012) | 2.25.182406418203495711231137284109815413937 |
| Implementation Version Name (0002,0013) | INSULA_1_5 |

## 2. Media storage

Insula reads files with or without a PS3.10 preamble and File Meta Information. Files without a Transfer Syntax UID are treated as Explicit or Implicit VR Little Endian by inspecting the first element.

### 2.1 Transfer syntaxes read

| Transfer syntax | UID | Supported |
|---|---|---|
| Implicit VR Little Endian | 1.2.840.10008.1.2 | Yes |
| Explicit VR Little Endian | 1.2.840.10008.1.2.1 | Yes |
| Deflated Explicit VR Little Endian | 1.2.840.10008.1.2.1.99 | Yes |
| Explicit VR Big Endian (retired) | 1.2.840.10008.1.2.2 | Yes |
| RLE Lossless | 1.2.840.10008.1.2.5 | Yes |
| JPEG Baseline (Process 1) | 1.2.840.10008.1.2.4.50 | Yes (8-bit, via Android's decoder) |
| JPEG Extended (Process 2 & 4) | 1.2.840.10008.1.2.4.51 | No |
| JPEG Lossless, Non-Hierarchical (Process 14) | 1.2.840.10008.1.2.4.57 | Yes |
| JPEG Lossless, First-Order Prediction (Process 14, SV1) | 1.2.840.10008.1.2.4.70 | Yes |
| JPEG-LS Lossless / Near-Lossless | 1.2.840.10008.1.2.4.80 / .81 | No |
| JPEG 2000 Image Compression (Lossless Only) | 1.2.840.10008.1.2.4.90 | Yes (JJ2000) |
| JPEG 2000 Image Compression | 1.2.840.10008.1.2.4.91 | Yes (JJ2000) |
| JPEG 2000 Part 2 Multi-component | 1.2.840.10008.1.2.4.92 / .93 | Not supported (Part 1 codestreams may decode) |
| High-Throughput JPEG 2000 | 1.2.840.10008.1.2.4.201–.203 | No |
| MPEG-2, MPEG-4, HEVC video | 1.2.840.10008.1.2.4.100–.108 | No |

### 2.2 Image pixel module support

| Feature | Support |
|---|---|
| Photometric Interpretation | MONOCHROME1, MONOCHROME2, RGB, PALETTE COLOR, YBR_FULL, YBR_FULL_422 (uncompressed and in JPEG), YBR_ICT and YBR_RCT (JPEG 2000) |
| Bits Allocated | 1, 8, 16, 32 |
| Pixel Representation | Unsigned and signed (two's complement), with Bits Stored masking and sign extension |
| Samples per Pixel / Planar Configuration | 1 or 3 / 0 or 1 |
| Multi-frame | Yes, including frames without a Basic Offset Table |
| Enhanced multi-frame (functional groups) | Per-frame and shared: Plane Position, Plane Orientation, Pixel Measures, Pixel Value Transformation, Frame VOI LUT |
| Rescale Slope / Intercept | Applied; values shown in HU for CT |
| Window Center / Width | First value pair used as the default; linear VOI function |
| Modality LUT Sequence, VOI LUT Sequence | Not applied |
| Overlay planes (60xx), presentation states | Not applied |

### 2.3 Specific character sets

ISO_IR 100 (Latin-1, the default), ISO_IR 192 (UTF-8), GB18030 / GBK, ISO_IR 144 (Cyrillic), ISO_IR 127 (Arabic), ISO_IR 126 (Greek), and ISO_IR 138 (Hebrew). ISO 2022 code extensions are not supported.

### 2.4 Non-image objects

Objects without pixel data (structured reports, presentation states, RT objects, encapsulated documents) are imported and listed. Their attributes can be read in the DICOM tag browser; they are not rendered.

## 3. DICOMweb client

Insula connects to user-configured servers ("PACS profiles") over HTTPS, or plain HTTP with a warning.

**Authentication:** HTTP Basic (username and password), Bearer token, or none. Credentials are encrypted with the Android Keystore.

### 3.1 QIDO-RS (search)

| Request | Use |
|---|---|
| `GET {base}/studies` | Study search |
| `GET {base}/studies/{study}/series` | Series list before download |
| `GET {base}/studies/{study}/series/{series}/instances` | Instance list, for progress and fallback retrieval |

Study search parameters: `PatientName` (with `*` wildcards; a trailing `*` is added to plain names), `PatientID`, `StudyDate` (single date or range), `ModalitiesInStudy`, `limit=100`, `fuzzymatching=true` when a name is given, and `includefield` for 0008,1030, 0008,0061, 0020,1206, and 0020,1208. If the server answers HTTP 400, the search is retried with only the filters.

**Accept:** `application/dicom+json` first, then `application/json`, then a weighted wildcard, if the server answers 406 or 415. HTML responses are reported as "a web page, not DICOMweb".

### 3.2 WADO-RS (retrieve)

| Request | Use |
|---|---|
| `GET {base}/studies/{study}/series/{series}` | Primary: whole series in one multipart response, streamed |
| `GET {base}/studies/{study}/series/{series}/instances/{instance}` | Fallback when series retrieval is refused, 4 requests in parallel |

**Accept:** `multipart/related; type="application/dicom"; transfer-syntax=*`, falling back to `multipart/related; type="application/dicom"` on 400, 406, or 415. Up to 3 series are retrieved in parallel, and connections are reused (HTTP keep-alive). Non-multipart `application/dicom` responses are also accepted.

### 3.3 Path discovery

If a profile's address is not a DICOMweb root, Test connection tries common roots on the same host: `/dicom-web`, `/dicomweb`, `/dicomWeb`, `/wado-rs`, `/rs`, `/dcm4chee-arc/aets/DCM4CHEE/rs`, `/orthanc/dicom-web`, `/pacs/dicom-web`, `/api/dicom-web`, `/DICOMweb`.

## 4. Created objects: saved MPR series

"Save planes as a new series" (MPR) creates one instance per reformatted slice.

| Item | Value |
|---|---|
| SOP Class | CT Image Storage (1.2.840.10008.5.1.4.1.1.2) for CT sources; MR Image Storage (1.2.840.10008.5.1.4.1.1.4) for MR; otherwise Secondary Capture Image Storage (1.2.840.10008.5.1.4.1.1.7) |
| Transfer syntax | Explicit VR Little Endian |
| New UIDs | SOP Instance UID and Series Instance UID, generated as 2.25 UUID-derived UIDs |
| Copied from source | Patient Name, ID, Birth Date, Sex, Age; Study Instance UID, Date, Time, ID, Description; Accession Number; Referring Physician; Institution; Frame of Reference UID; Specific Character Set; Modality |
| Image Type | DERIVED\SECONDARY\MPR |
| Series Description | For example "MPR Coronal 3mm MIP 10mm" |
| Geometry | Image Position (Patient), Image Orientation (Patient), Pixel Spacing, Slice Thickness, Slice Location |
| Pixel data | 16-bit signed (Bits Stored 16), MONOCHROME2, Rescale Slope 1, Intercept 0 (values already in modality units; Rescale Type HU for CT), Window Center/Width from the view |

Saved series are stored in the local library as part of the same study. Insula does not send them to a PACS.

### 4.1 3D VRT sessions and captures

| Object | SOP Class | Series | Content |
|---|---|---|---|
| Saved session | Raw Data Storage (1.2.840.10008.5.1.4.1.1.66) | "Insula 3D VRT states", Series Number 9901, one series per study (UID derived from the Study Instance UID) | Private creator (0071,0010) = `INSULA_VRT`; (0071,1001) OB: session settings as UTF-8 JSON; (0071,1002) OB: tissue map, one byte per voxel, deflate-compressed; (0071,1003) LO: Series Instance UID of the source series |
| Capture | Secondary Capture Image Storage (1.2.840.10008.5.1.4.1.1.7) | "Insula 3D VRT captures", Series Number 9902 | 8-bit RGB, Conversion Type WSD, Image Type DERIVED\\SECONDARY\\VOLUME_RENDERING, Burned In Annotation NO |
| Rotation series | Secondary Capture Image Storage | "3D VRT rotation (36 views)", Series Number 9903, new UID each time | 36 RGB images 10° apart |

All copy the patient and study attributes of the source series, with Content Date and Time of creation, so they file into the same study. Other viewers ignore the private session data and display the captures normally. Anonymized export keeps the `INSULA_VRT` private block (settings and a tissue map, no patient data) and still blanks all other private elements.

**Demo study.** The optional demo creates two synthetic CT Image Storage studies (patient ID `INSULA-DEMO`, institution "Insula demo (synthetic data)") with fixed UIDs under `2.25.3301…`, so creating it again adds no duplicates.

## 5. Anonymized export

Anonymized ZIP export (and anonymized study sets) modifies each file in place.

- **Replaced:** Patient's Name → "ANONYMOUS"; Patient ID → "ANON".
- **Blanked:** Issuer of Patient ID, Patient's Birth Date and Birth Time, Other Patient IDs and Names, Patient's Address, Mother's Birth Name, Telephone Numbers, Ethnic Group, Additional Patient History, Patient Comments, Accession Number, Institution Name and Address, Referring, Performing, Reading, and Record physicians, Operators' Name, Station Name, Institutional Department, Device Serial Number, Study ID, Requesting Physician, Scheduled and Performed Procedure Step IDs, Image Comments.
- **Private tags:** every private data element's value is zeroed.
- **Sequences:** nested items are processed with the same rules.
- **Retained:** all UIDs, study, series, and content dates and times, patient sex and age, and pixel data, including any burned-in text.

This is **not** a complete implementation of the DICOM PS3.15 Basic Application Level Confidentiality Profile. Deflated files are skipped.

## 6. Security

- Studies are stored in app-private storage (encrypted by Android on modern devices), with Android cloud backup disabled.
- App lock uses the device credential (PIN, pattern, password, or biometrics).
- Screenshots and screen recording can be blocked.
- No audit trail (ATNA) or node authentication (TLS client certificates) is provided.
