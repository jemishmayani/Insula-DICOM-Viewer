# Insula DICOM Viewer
# Copyright (C) 2026 Jemish Mayani
# SPDX-License-Identifier: GPL-3.0-or-later
"""Compares the app's decoded pixels (from DecodeDump) with pydicom's reference decoders.

Lossless and uncompressed data must match exactly; lossy JPEG 2000 may differ by 2 levels.
Files the JVM can't decode because they need Android's BitmapFactory (JPEG baseline) are skipped.
"""
import os
import sys
import warnings

import numpy as np
import pydicom
from pydicom.pixels import apply_color_lut

warnings.filterwarnings("ignore")
results, pix_dir = sys.argv[1], sys.argv[2]
LOSSLESS = {"1.2.840.10008.1.2", "1.2.840.10008.1.2.1", "1.2.840.10008.1.2.2", "1.2.840.10008.1.2.1.99",
            "1.2.840.10008.1.2.5", "1.2.840.10008.1.2.4.57", "1.2.840.10008.1.2.4.70", "1.2.840.10008.1.2.4.90"}
tested = failed = 0
for line in open(results):
    parts = line.rstrip("\n").split("|")
    if parts[0] != "OK":
        continue
    path, rgb, frame = parts[1], parts[3] == "rgb=true", int(parts[4].split("=")[1])
    name = os.path.basename(path)
    ds = pydicom.dcmread(path)
    try:
        arr = ds.pixel_array
    except Exception:
        continue  # no reference decoder available for this one
    if int(getattr(ds, "NumberOfFrames", 1) or 1) > 1:
        arr = arr[frame]
    ours = np.fromfile(os.path.join(pix_dir, name + ".bin"), dtype="<i4").astype(np.int64)
    if rgb:
        if arr.ndim == 2:  # palette color
            arr = apply_color_lut(arr, ds).astype(np.int64) >> 8
        elif arr.dtype.itemsize > 1:
            arr = arr.astype(np.int64) >> (8 * (arr.dtype.itemsize - 1))
        ref = arr.reshape(-1, 3).astype(np.int64)
        diff = int(max(abs(((ours >> s) & 255) - ref[:, k]).max() for k, s in enumerate((16, 8, 0))))
        tol = 1
    else:
        ref = arr.reshape(-1).astype(np.int64)
        diff = int(abs(ours - ref).max()) if len(ours) == len(ref) else 10**9
        tol = 0
    if str(ds.file_meta.TransferSyntaxUID) not in LOSSLESS:
        tol = 2
    tested += 1
    ok = diff <= tol
    failed += not ok
    print(("PASS" if ok else "FAIL"), f"{name:45s} maxdiff={diff}")
print(f"{tested - failed}/{tested} files match the reference decoders")
sys.exit(1 if failed or tested == 0 else 0)
