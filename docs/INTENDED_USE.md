# Intended use and limitations

*Applies to Insula DICOM Viewer 1.7.0. Last updated 29 September 2026.*

## Product description

Insula DICOM Viewer is free, open-source software for Android phones and tablets. It displays medical images stored in DICOM format, with basic measurements, annotations, multiplanar reconstruction, and anonymized sharing.

## Intended use

Insula is intended for **reference, education, communication, and personal viewing** of medical images, for example:

- a clinician reviewing images already reported elsewhere, away from a workstation
- teaching, case discussion, and preparing teaching files (with Teacher mode and anonymized export)
- a patient viewing their own images from a CD or download
- checking that a study transferred completely before it is read on a diagnostic system

## Not intended for

- **Primary diagnosis** or any decision that relies solely on images viewed in Insula
- Mammography, or other reading that requires display calibration or regulatory clearance
- Treatment planning, surgical navigation, or dose calculation
- Emergency or time-critical care as the only means of viewing images
- Archiving: Insula is not a PACS and is not a system of record

## Regulatory status

Insula is **not registered, cleared, or certified as a medical device** by any regulator, including CDSCO (India), the US FDA, or under the EU MDR. It is distributed as general-purpose viewing software for the intended use above. Anyone who uses, modifies, or redistributes it for a medical purpose is responsible for meeting the regulations that apply to them.

## Intended users

Healthcare professionals, medical students and educators, and patients viewing their own images. Users are expected to understand that a phone is not a diagnostic reading environment.

## Known limitations

| Area | Limitation |
|---|---|
| Display | Phone and tablet screens are not calibrated to the DICOM Grayscale Standard Display Function. Brightness, ambient light, and screen size affect what is visible. |
| Grayscale | Only linear window/level is applied. Modality LUT and VOI LUT sequences, and presentation states, are not applied. |
| Compression | Lossy JPEG and JPEG 2000 images are shown as stored; lossy compression can hide or create detail. JPEG-LS, HTJ2K, and 12-bit JPEG are not supported. |
| Measurements | Distances and areas use Pixel Spacing, or Imager Pixel Spacing for projection radiographs. Imager Pixel Spacing is measured at the detector, so objects appear magnified and measurements over-estimate their true size. Uncalibrated images are measured in pixels. |
| Reconstructions | MPR, MIP, curved MPR, and 3D are interpolated from the acquired slices. Uneven slice spacing, large gaps, or motion reduce accuracy; the app warns about uneven spacing. Very large series are reduced in resolution to fit in memory, and the app says so. |
| Overlays | DICOM overlay planes (60xx), segmentation objects, and structured-report rendering are not displayed. Reports can be read in the DICOM tag browser. |
| Anonymization | Anonymized export blanks common identifying attributes and all private tags, keeps UIDs and study dates, and does not remove text burned into image pixels. It is not a complete implementation of the DICOM PS3.15 de-identification profiles. Always check exported images. |
| Network | PACS access uses DICOMweb only (QIDO-RS, WADO-RS). Traditional DICOM networking (C-FIND, C-MOVE, C-STORE) is not supported. |

## Warnings shown to users

- A disclaimer on first launch states that the app is not a medical device and must not be used for primary diagnosis.
- About (Settings › Help and legal) repeats the disclaimer, the licence, and the absence of warranty.
- Anonymized exports warn that burned-in text is not removed, and warn again if images declare burned-in annotation.

## Warranty

Insula is provided under the GNU General Public License version 3 or later, **without any warranty**, including the implied warranties of merchantability and fitness for a particular purpose. See [LICENSE](../LICENSE).
