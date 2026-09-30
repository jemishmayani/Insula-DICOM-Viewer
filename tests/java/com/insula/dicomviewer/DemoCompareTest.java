/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import java.io.File;
import java.util.*;

/** Demo study generation, parallel MPR volume building, and prior/current comparison with alignment. */
public class DemoCompareTest {
    static int fails = 0;
    static void check(String n, boolean ok, String d) { System.out.println((ok ? "PASS " : "FAIL ") + n + "  " + d); if (!ok) fails++; }

    public static void main(String[] a) throws Exception {
        Library.dir = new File(System.getProperty("work", "tests/.work") + "/demo-lib");
        Library.dir.mkdirs();
        long t0 = System.nanoTime();
        int[] r = DemoStudy.create(null);
        check("demo creates a prior and a current study", r[0] == 2 * DemoStudy.SLICES && Library.studies.size() == 2, r[0] + " images in " + (System.nanoTime() - t0) / 1000000 + " ms");
        int[] again = DemoStudy.create(null);
        check("creating the demo again adds no duplicates", again[0] == 0 && again[1] == 2 * DemoStudy.SLICES, "");
        Library.Study cur = Library.study(DemoStudy.studyUid(1)), prior = Library.study(DemoStudy.studyUid(0));

        // Comparison: finding the prior and its matching series
        List<Library.Study> rel = Compare.related(cur, Library.studies);
        check("Compare finds the same patient's other study", rel.size() == 1 && rel.get(0) == prior, "");
        Library.Series cs = cur.series.get(0), ps = Compare.bestMatch(cs, prior);
        check("best-matching series is the prior axial head", ps != null && ps.study == prior, ps == null ? "" : ps.label());
        Library.Study other = new Library.Study(cur.series.get(0).images.get(0));
        other.patientId = "SOMEONE-ELSE";
        check("a different patient ID is not treated as the same patient", !Compare.samePatient(cur, other), "");

        // Alignment: the current exam sits 6 mm higher; centre alignment must map the same anatomy
        SliceProvider.SeriesProvider pc = new SliceProvider.SeriesProvider(cs), pp = new SliceProvider.SeriesProvider(ps);
        double[] n = {0, 0, 1};
        double offset = Compare.centre(pp, n) - Compare.centre(pc, n);   // align[prior] - align[current]
        int lesionCur = Compare.nearest(pc, n, 12 + 6);                     // lesion centre z = 12 mm anatomical
        int mapped = Compare.nearest(pp, n, Library.dot(pc.position(lesionCur), n) + offset);
        double zCur = Library.dot(pc.position(lesionCur), n) - 6, zPrior = Library.dot(pp.position(mapped), n);
        check("linked scrolling lands on the same anatomy across studies", Math.abs(zCur - zPrior) < 0.01, String.format("current %.1f mm vs prior %.1f mm (anatomical)", zCur, zPrior));
        int naive = Compare.nearest(pp, n, Library.dot(pc.position(lesionCur), n));
        check("without alignment it would be 2 slices off", Math.abs(naive - mapped) == 2, "naive " + naive + " vs aligned " + mapped);

        // Parallel volume build from the stored series, then check anatomy
        Library.clearCache();
        t0 = System.nanoTime();
        Volume v = Volume.build(cs, null);
        long ms = (System.nanoTime() - t0) / 1000000;
        double[] vox = v.toVoxel(new double[]{-28, 22, 12 + 6});
        float lesion = v.tri(vox[0], vox[1], vox[2]);
        double[] sk = v.toVoxel(new double[]{0, 88, 6});
        float skull = v.tri(sk[0], sk[1], sk[2]);
        check("parallel MPR volume has the lesion where it was drawn", Math.abs(lesion - 68) < 12, String.format("%.0f HU (expect 68)", lesion));
        check("parallel MPR volume has the skull where it was drawn", skull > 800, String.format("%.0f HU (expect 1100)", skull));
        check("volume window comes from the series", v.defWc == 40 && v.defWw == 80, "WL " + v.defWc + " / WW " + v.defWw);
        System.out.printf("     MPR volume (%dx%dx%d) built in %d ms on %d cores%n", v.nx, v.ny, v.nz, ms, Runtime.getRuntime().availableProcessors());

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
