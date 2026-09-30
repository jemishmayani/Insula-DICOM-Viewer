/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import java.io.File;
import java.util.List;

/** 3D states and captures saved inside a study: round trip, listing, anonymized copies, deletion. */
public class VrtStoreTest {
    static int fails = 0;
    static void check(String n, boolean ok, String d) { System.out.println((ok ? "PASS " : "FAIL ") + n + "  " + d); if (!ok) fails++; }

    public static void main(String[] a) throws Exception {
        Library.dir = new File(System.getProperty("work", "tests/.work") + "/vrtstore-lib");
        Library.dir.mkdirs();
        DemoStudy.create(null);
        Library.Study st = Library.study(DemoStudy.studyUid(1));
        Library.Series src = st.series.get(0);
        long t0 = System.nanoTime();
        Volume vol = Volume.build(src, null);
        Vol3D v = Vol3D.from(vol, 1);
        long buildMs = (System.nanoTime() - t0) / 1000000;
        check("3D volume resampled onto a patient-aligned grid", v.nx > 100 && v.nz > 20 && Math.abs(v.sx - v.sz) < 1e-9, v.nx + "x" + v.ny + "x" + v.nz + " at " + String.format("%.2f", v.sx) + " mm, " + buildMs + " ms");
        Seg.Params p = Seg.estimate(v);
        Seg.segment(v, p, null);
        check("demo head separates into skull (bone) and brain (soft tissue)", Seg.classCount(v, Seg.BONE) > 1000 && Seg.classCount(v, Seg.ORGAN) > 10000, "bone " + Seg.classCount(v, Seg.BONE) + ", soft tissue " + Seg.classCount(v, Seg.ORGAN));
        // a manual edit, so the saved map differs from a fresh automatic one
        Seg.IntList comp = Seg.component(v, firstOf(v, Seg.BONE));
        Seg.set(v, "Hide", comp, true, 0);
        byte[] edited = v.labels.clone();

        VrtState s = new VrtState();
        s.defaults(true, p.vessel);
        s.params = p; s.name = "Skull hidden"; s.cam.rotate(0.4, 0.2); s.clipHi[0] = 0.6f;
        String sop = VrtStore.saveState(src, s, v);
        int before = st.series.size();
        List<VrtStore.Saved> saved = VrtStore.states(src);
        check("state saved into the study as its own series", saved.size() == 1 && saved.get(0).name.equals("Skull hidden") && saved.get(0).info.sopUid.equals(sop), st.series.size() + " series in study");
        Library.Series ss = null;
        for (Library.Series se : st.series) if (se.uid.equals(VrtStore.statesSeries(st))) ss = se;
        check("states series is named and non-image", ss != null && ss.desc.equals(VrtStore.STATES_DESC) && ss.slices().isEmpty(), ss == null ? "" : ss.desc);

        java.util.Arrays.fill(v.labels, (byte) 0);
        VrtStore.loadLabels(saved.get(0), v);
        VrtState back = VrtState.fromJson(saved.get(0).json);
        check("reopening restores the edited tissue map exactly", java.util.Arrays.equals(edited, v.labels), "");
        check("reopening restores view settings", Math.abs(back.cam.R[0] - s.cam.R[0]) < 1e-9 && back.clipHi[0] == 0.6f, "");
        long fileBytes = saved.get(0).info.file.length();
        check("saved state is compact", fileBytes < v.n() / 4, fileBytes / 1024 + " KB for " + v.n() / 1024 + " K voxels");

        // Different grid (another quality tier): labels are remapped
        Vol3D low = Vol3D.from(vol, 2);
        VrtStore.loadLabels(saved.get(0), low);
        check("a state opens at a different quality tier", Seg.classCount(low, Seg.ORGAN) > 1000, low.nx + "x" + low.ny + "x" + low.nz);

        // Anonymized copy keeps the state block but scrubs the name
        Dicom.DataSet ad = Dicom.parse(Library.readFile(saved.get(0).info.file));
        Anonymizer.scrub(ad);
        ad = Dicom.parse(ad.buf);
        byte[] js = VrtStore.element(ad, VrtStore.T_JSON);
        check("anonymized copy keeps the 3D state", js != null && new String(js, "UTF-8").contains("Skull hidden") && VrtStore.CREATOR.equals(ad.str(VrtStore.T_CREATOR).trim()), "");
        check("anonymized copy has no patient name", !ad.str(0x00100010).contains("DEMO"), ad.str(0x00100010));

        // Screenshot as a Secondary Capture image
        int[] img = new CpuVrt(v, s).render(96, 80, 1.5);
        String cap = VrtStore.saveCapture(src, img, 96, 80, VrtStore.capturesSeries(st), VrtStore.CAPTURES_DESC, 9902, 1);
        Library.Series cs = null;
        for (Library.Series se : st.series) if (se.uid.equals(VrtStore.capturesSeries(st))) cs = se;
        RawImage r = cs == null ? null : Library.load(cs.slices().get(0));
        boolean same = r != null && r.rgb && r.w == 96 && r.h == 80;
        for (int i = 0; same && i < img.length; i += 37) if ((r.pix[i] & 0xFFFFFF) != (img[i] & 0xFFFFFF)) same = false;
        check("screenshot added to the study as an image, pixel-exact", same, cap);

        VrtStore.delete(saved.get(0));
        boolean gone = true;
        for (Library.Series se : st.series) if (se.uid.equals(VrtStore.statesSeries(st))) gone = false;
        check("deleting the only state removes its series", VrtStore.states(src).isEmpty() && gone, st.series.size() + " series left (images and captures)");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }

    static int firstOf(Vol3D v, int cls) { for (int i = 0; i < v.n(); i++) if (v.labels[i] == cls) return i; return -1; }
}
