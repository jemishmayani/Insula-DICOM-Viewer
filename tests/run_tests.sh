#!/usr/bin/env bash
# Insula DICOM Viewer
# Copyright (C) 2026 Jemish Mayani
# SPDX-License-Identifier: GPL-3.0-or-later
# Runs the desktop test suite against the app's own classes (no emulator needed).
# Requirements: the build tools from ../build.sh, python3, and
#   pip install -r tests/requirements.txt
# Network access is needed on first run (pydicom test data, org.json sources).
set -uo pipefail
cd "$(dirname "$0")/.."
T=tests; WORK=$T/.work; OUT=$T/.out; CACHE=$T/.cache
AJ=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
ORG_JSON_TAG=20250517
JAVA="timeout 600 java"   # no single test may run longer than 10 minutes

[ -f obj/com/insula/dicomviewer/Dicom.class ] || ./build.sh || exit 1
rm -rf "$WORK" "$OUT"; mkdir -p "$WORK" "$OUT" "$CACHE/org-json"

# Android's org.json and Base64 are stubs on a desktop JVM, so tests use real implementations.
if [ ! -f "$CACHE/org-json/JSONObject.java" ]; then
  for f in JSONArray JSONObject JSONException JSONTokener JSONString JSONPropertyIgnore JSONPropertyName \
           JSONPointer JSONPointerException JSONParserConfiguration ParserConfiguration JSONWriter StringBuilderWriter; do
    curl -sfL -o "$CACHE/org-json/$f.java" \
      "https://raw.githubusercontent.com/stleary/JSON-java/$ORG_JSON_TAG/src/main/java/org/json/$f.java" || rm -f "$CACHE/org-json/$f.java"
  done
fi
javac -nowarn -d "$OUT" "$CACHE"/org-json/*.java $T/shim/android/util/Base64.java || exit 1
CP="$OUT:obj:$AJ"
javac -nowarn -cp "$CP" -d "$OUT" $(find $T/java -name '*.java') 2>&1 | grep -v "^Note:" ; [ ${PIPESTATUS[0]} -eq 0 ] || exit 1

PASSED=(); FAILED=()
step() { local name=$1; shift; echo; echo "=== $name ==="; if "$@"; then PASSED+=("$name"); else FAILED+=("$name"); fi; }

decoders() {
  python3 $T/python/fetch_testdata.py > "$WORK/files.txt" || return 1
  $JAVA -cp "$CP" -Dout="$WORK/pixels" DecodeDump $(cat "$WORK/files.txt") > "$WORK/decode.txt" 2>/dev/null
  grep -c '^OK' "$WORK/decode.txt" | xargs echo "decoded files:"
  python3 $T/python/compare_pixels.py "$WORK/decode.txt" "$WORK/pixels"
}

writer() {
  $JAVA -cp "$CP" -Dwork="$WORK" com.insula.dicomviewer.DicomWriterTest || return 1
  python3 - "$WORK/mpr_saved.dcm" <<'PY'
import sys, warnings, numpy as np, pydicom
warnings.filterwarnings("ignore")
d = pydicom.dcmread(sys.argv[1]); a = d.pixel_array
o = np.array(d.ImagePositionPatient, float); u = np.array(d.ImageOrientationPatient[:3], float)
v = np.array(d.ImageOrientationPatient[3:], float); s = float(d.PixelSpacing[0]); c = np.array([10, -5, 15])
val = a[int(round((c - o) @ v / s)), int(round((c - o) @ u / s))]
print("pydicom reads it:", d.SOPClassUID.name, a.shape, "| sphere centre via DICOM geometry =", val)
sys.exit(0 if val == 1000 else 1)
PY
}

backup() {
  local td; td=$(python3 -c "import os,pydicom;print(os.path.join(os.path.dirname(pydicom.data.__file__),'test_files'))")
  $JAVA -cp "$CP" -Dwork="$WORK" -Dtestdata="$td" com.insula.dicomviewer.BackupTest
}

with_server() {  # with_server <script> <java class>
  local log="$WORK/$(basename "$1").log"
  python3 "$1" > "$log" 2>&1 & local pid=$!
  for _ in $(seq 1 60); do grep -q '^up' "$log" 2>/dev/null && break; sleep 0.5; done
  $JAVA -cp "$CP" -Dwork="$WORK" "$2" 2>/dev/null; local rc=$?
  kill $pid 2>/dev/null; wait $pid 2>/dev/null
  return $rc
}

step "Decoders vs pydicom/OpenJPEG" decoders
step "MPR geometry (phantoms)" timeout 600 java -Djava.awt.headless=true -Dwork="$WORK" -cp "$CP" com.insula.dicomviewer.MprPhantomTest
step "DICOM writer (saved MPR series)" writer
step "Backup, annotations, study sets" backup
step "Smooth viewing (windowing, previews, loading)" $JAVA -Dwork="$WORK" -cp "$CP" com.insula.dicomviewer.SmoothViewingTest
step "Demo study, comparison, parallel MPR build" $JAVA -Dwork="$WORK" -cp "$CP" com.insula.dicomviewer.DemoCompareTest
step "3D VRT: tissue separation, edits, sessions" $JAVA -Dwork="$WORK" -cp "$CP" com.insula.dicomviewer.VrtTest
step "3D VRT: GPU shader matches CPU renderer" python3 "$T/python/vrt_gpu_check.py" "$WORK/vrt" res/raw
step "3D VRT: states and captures saved in the study" $JAVA -Dwork="$WORK" -cp "$CP" com.insula.dicomviewer.VrtStoreTest
step "Update checker (version compare, release parsing)" $JAVA -cp "$CP" com.insula.dicomviewer.UpdateCheckTest
step "PACS: two institutions, password and token" with_server $T/python/mock_two_hospitals.py com.insula.dicomviewer.PacsProfilesTest
step "PACS: HTTP 406 recovery and path discovery" with_server $T/python/mock_406_portal.py com.insula.dicomviewer.Pacs406Test
step "PACS: download speed, fallback, stream parser" with_server $T/python/mock_slow_pacs.py com.insula.dicomviewer.DownloadBenchmark

echo; echo "=== Summary ==="
for s in "${PASSED[@]}"; do echo "PASS  $s"; done
for s in "${FAILED[@]:-}"; do [ -n "$s" ] && echo "FAIL  $s"; done
[ ${#FAILED[@]} -eq 0 ]
