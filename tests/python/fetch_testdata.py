# Insula DICOM Viewer
# Copyright (C) 2026 Jemish Mayani
# SPDX-License-Identifier: GPL-3.0-or-later
"""Downloads extra pydicom test files (JPEG Lossless, JPEG 2000, RLE) and prints every test file path."""
import glob
import os
import warnings

import pydicom
from pydicom.data import get_testdata_file

warnings.filterwarnings("ignore")
EXTRA = ["JPEG-LL.dcm", "JPGLosslessP14SV1_1s_1f_8b.dcm", "emri_small.dcm", "emri_small_RLE.dcm",
         "emri_small_jpeg_2k_lossless.dcm", "MR2_J2KR.dcm", "MR2_J2KI.dcm", "US1_J2KR.dcm", "US1_J2KI.dcm",
         "RG1_J2KR.dcm", "RG1_J2KI.dcm", "RG3_J2KR.dcm", "693_J2KR.dcm", "693_J2KI.dcm", "GDCMJ2K_TextGBR.dcm",
         "J2K_pixelrep_mismatch.dcm"]
paths = set()
for n in EXTRA:
    try:
        p = get_testdata_file(n)
        if p:
            paths.add(p)
    except Exception:
        pass
bundled = os.path.join(os.path.dirname(pydicom.data.__file__), "test_files")
paths.update(glob.glob(os.path.join(bundled, "*.dcm")))
print("\n".join(sorted(paths)))
