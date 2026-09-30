/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/**
 * 3D VRT on a synthetic coronary CTA phantom: automatic tissue separation against known truth, manual edits and
 * undo, saved-state round trip, and CPU renders exported for comparison with the GPU shader (vrt_gpu_check.py).
 */
public class VrtTest {
    static int fails = 0;
    static void check(String n, boolean ok, String d) { System.out.println((ok ? "PASS " : "FAIL ") + n + "  " + d); if (!ok) fails++; }

    static final int NX = 160, NY = 140, NZ = 120;
    static byte[] truth;
    static final int T_AORTA = 20, T_COR = 21, T_PLAQUE = 22, T_SPINE = 23, T_BRIDGE = 24;   // finer truth codes
    static byte[] fine;

    static double sq(double a) { return a * a; }

    static Vol3D phantom() {
        Vol3D v = new Vol3D(NX, NY, NZ, 1, 1, 1, new double[]{-79.5, -69.5, -59.5}, true, 40, 400, -1024, 3071);
        truth = new byte[v.n()];
        fine = new byte[v.n()];
        Random rnd = new Random(7);
        float[] sharp = new float[v.n()];
        for (int k = 0; k < NZ; k++) for (int j = 0; j < NY; j++) for (int i = 0; i < NX; i++) {
            double x = -79.5 + i, y = -69.5 + j, z = -59.5 + k;
            double hu; int t; int f = 0;
            if (sq(x / 75) + sq(y / 62) >= 1) { hu = -1000; t = Seg.BG; }
            else if (sq(x / 72) + sq(y / 59) >= 1) { hu = -100; t = Seg.SKIN; }
            else { hu = 40; t = Seg.ORGAN; }
            if (t == Seg.ORGAN) {
                if (sq((x - 35) / 28) + sq(y / 40) + sq((z - 10) / 50) < 1 || sq((x + 35) / 28) + sq(y / 40) + sq((z - 10) / 50) < 1) { hu = -850; t = Seg.LUNG; }
                if (sq((x - 5) / 32) + sq((y + 15) / 28) + sq(z / 38) < 1) { hu = 60; t = Seg.ORGAN; }                          // myocardium
                if (sq((x - 10) / 18) + sq((y + 12) / 15) + sq(z / 25) < 1) { hu = 380; t = Seg.VESSEL; }                       // LV cavity
                if (sq(x + 5) + sq(y + 25) < 144 && z > 10) { hu = 380; t = Seg.VESSEL; }                                     // ascending aorta
                double ra = Math.sqrt(sq(x + 20) + sq(y - 31));
                if (ra < 10) { hu = 380; t = Seg.VESSEL; f = T_AORTA; }                                                      // descending aorta
                double rs = Math.sqrt(sq(x) + sq(y - 45));
                if (rs < 14) { hu = rs >= 12 ? 900 : 250; t = Seg.BONE; f = T_SPINE; }                                       // vertebral body
                // thin partial-volume contact between the aorta and the spine, along the whole length
                if (t == Seg.ORGAN && ra < 11 && rs < 15) { hu = 320; f = T_BRIDGE; }
                if (Math.abs(x) < 10 && Math.abs(y + 56) < 4) { hu = 700; t = Seg.BONE; }                                       // sternum
                for (int r = -2; r <= 2; r++) {                                                                                 // ribs
                    double zr = r * 22 + 5;
                    if (Math.abs(z - zr) < 3.5 && Math.abs(Math.sqrt(sq(x / 66) + sq(y / 53)) - 1) * 60 < 3.5 && y > -45) { hu = 750; t = Seg.BONE; }
                }
                // coronary artery on the heart surface, with a calcified plaque
                for (double th = 0.3; th < 2.8; th += 0.02) {
                    double cx = 5 + 34.5 * Math.cos(th), cy = -15 - 30.5 * Math.sin(th), cz = -5 + 8 * th;
                    if (sq(x - cx) + sq(y - cy) + sq(z - cz) < sq(1.9)) {
                        boolean plaque = th > 1.40 && th < 1.62;
                        hu = plaque ? 1000 : 380; t = plaque ? Seg.CALCIUM : Seg.VESSEL; f = plaque ? T_PLAQUE : T_COR; break;
                    }
                }
            }
            int x0 = v.idx(i, j, k);
            sharp[x0] = (float) hu;
            truth[x0] = (byte) t;
            fine[x0] = (byte) f;
        }
        // Scanner-like partial-volume blur (separable [1 2 1]/4 in each axis), then noise (sigma 18 HU)
        float[] a = sharp, b = new float[a.length];
        int[] strides = {1, NX, NX * NY}, dims = {NX, NY, NZ};
        for (int ax = 0; ax < 3; ax++) {
            for (int k = 0; k < NZ; k++) for (int j = 0; j < NY; j++) for (int i = 0; i < NX; i++) {
                int x0 = v.idx(i, j, k), c = ax == 0 ? i : ax == 1 ? j : k, st = strides[ax];
                float lo = c > 0 ? a[x0 - st] : a[x0], hi = c < dims[ax] - 1 ? a[x0 + st] : a[x0];
                b[x0] = 0.25f * lo + 0.5f * a[x0] + 0.25f * hi;
            }
            float[] tmp = a; a = b; b = tmp;
        }
        for (int x0 = 0; x0 < a.length; x0++) v.hu[x0] = (short) Math.round(a[x0] + (a[x0] > -990 ? rnd.nextGaussian() * 18 : 0));
        return v;
    }

    /** {centreline fraction labelled vessel or calcium, fraction over all voxels}. Centreline = all 6 neighbours inside. */
    static double[] centreline(Vol3D v, int code) {
        int in = 0, inOk = 0, all = 0, allOk = 0;
        for (int k = 1; k < v.nz - 1; k++) for (int j = 1; j < v.ny - 1; j++) for (int i = 1; i < v.nx - 1; i++) {
            int x = v.idx(i, j, k);
            if (fine[x] != code) continue;
            int c = v.labels[x] & 0x7F;
            boolean ok = c == Seg.VESSEL || c == Seg.CALCIUM;
            all++; if (ok) allOk++;
            if (fine[x - 1] == code && fine[x + 1] == code && fine[x - v.nx] == code && fine[x + v.nx] == code
                    && fine[x - v.nx * v.ny] == code && fine[x + v.nx * v.ny] == code) { in++; if (ok) inOk++; }
        }
        return new double[]{inOk / (double) Math.max(1, in), allOk / (double) Math.max(1, all)};
    }

    static double recall(Vol3D v, int cls, int fineCode) {
        long n = 0, ok = 0;
        for (int x = 0; x < v.n(); x++) {
            boolean in = fineCode > 0 ? fine[x] == fineCode : (truth[x] == cls && fine[x] != T_BRIDGE);
            if (!in) continue;
            n++;
            if ((v.labels[x] & 0x7F) == cls) ok++;
        }
        return n == 0 ? Double.NaN : ok / (double) n;
    }

    static double share(Vol3D v, int fineCode, int cls) {
        long n = 0, c = 0;
        for (int x = 0; x < v.n(); x++) if (fine[x] == fineCode) { n++; if ((v.labels[x] & 0x7F) == cls) c++; }
        return c / (double) Math.max(1, n);
    }

    public static void main(String[] a) throws Exception {
        String work = System.getProperty("work", "tests/.work");
        File out = new File(work, "vrt");
        out.mkdirs();

        // Half-float conversion used for the GPU texture
        boolean halfOk = true;
        for (int hu = -1024; hu <= 3071; hu++) {
            float back = Vol3D.fromHalf(Vol3D.toHalf(hu));
            if (Math.abs(back - hu) > (Math.abs(hu) > 2048 ? 1.0 : 0.0)) { halfOk = false; break; }
        }
        check("HU values survive half-float upload (exact to 2048, within 1 HU above)", halfOk, "");

        long t0 = System.nanoTime();
        Vol3D v = phantom();
        Seg.Params p = Seg.estimate(v);
        check("contrast enhancement detected", p.contrast, p.summary);
        check("vessel threshold between soft tissue and blood pool", p.vessel > 100 && p.vessel < 330, String.format("%.0f HU", p.vessel));
        t0 = System.nanoTime();
        Seg.segment(v, p, null);
        long segMs = (System.nanoTime() - t0) / 1000000;
        System.out.printf("     separated %d voxels in %d ms%n", v.n(), segMs);
        double rb = recall(v, Seg.BONE, 0), rv = recall(v, Seg.VESSEL, 0), rl = recall(v, Seg.LUNG, 0), ro = recall(v, Seg.ORGAN, 0), rs = recall(v, Seg.SKIN, 0);
        check("bone found", rb >= 0.90, String.format("%.1f%%", rb * 100));
        check("vessels and chambers found", rv >= 0.90, String.format("%.1f%%", rv * 100));
        check("lungs found", rl >= 0.97, String.format("%.1f%%", rl * 100));
        check("organs and soft tissue found", ro >= 0.90, String.format("%.1f%%", ro * 100));
        check("skin and fat found", rs >= 0.80, String.format("%.1f%%", rs * 100));
        check("aorta touching the spine is not called bone", share(v, T_AORTA, Seg.BONE) < 0.05, String.format("%.1f%% of aorta labelled bone", share(v, T_AORTA, Seg.BONE) * 100));
        check("spine touching the aorta is not called vessel", share(v, T_SPINE, Seg.VESSEL) < 0.05, String.format("%.1f%% of spine labelled vessel", share(v, T_SPINE, Seg.VESSEL) * 100));
        int ribs = 0, ribsVessel = 0;
        for (int x = 0; x < v.n(); x++) if (truth[x] == Seg.BONE && fine[x] != T_SPINE) { ribs++; if ((v.labels[x] & 0x7F) == Seg.VESSEL) ribsVessel++; }
        check("ribs' blurred edges stay bone (no red rims), even where bone links to the aorta", ribsVessel < ribs * 0.03, String.format("%.1f%% of rib and sternum voxels labelled vessel", 100.0 * ribsVessel / ribs));
        double[] cor = centreline(v, T_COR);
        check("thin coronary artery: centreline kept as vessel", cor[0] >= 0.90, String.format("%.1f%% of centreline; %.1f%% including partial-volume edges", 100 * cor[0], 100 * cor[1]));
        check("coronary calcification labelled calcium", share(v, T_PLAQUE, Seg.CALCIUM) >= 0.5, String.format("%.1f%%", 100 * share(v, T_PLAQUE, Seg.CALCIUM)));

        // Manual edits
        byte[] before = v.labels.clone();
        int aortaSeed = v.idx(60, 100, 60);   // x=-19.5, y=30.5, inside descending aorta
        Seg.IntList comp = Seg.component(v, aortaSeed);
        check("tapping a vessel selects its connected structure", comp.size > 5000 && (v.labels[aortaSeed] == Seg.VESSEL), comp.size + " voxels");
        Seg.Edit e1 = Seg.set(v, "Move to Selection", comp, false, Seg.SELECT);
        check("move to another class", v.labels[aortaSeed] == Seg.SELECT, "");
        Seg.Edit e2 = Seg.set(v, "Hide", comp, true, 0);
        check("hide keeps the class but marks removed", (v.labels[aortaSeed] & 0x80) != 0 && (v.labels[aortaSeed] & 0x7F) == Seg.SELECT, "");
        Seg.undo(v, e2); Seg.undo(v, e1);
        check("undo restores every voxel", java.util.Arrays.equals(before, v.labels), "");
        VrtState st = new VrtState();
        st.defaults(true, p.vessel);
        st.params = p;
        int W = 200, H = 200;
        float[] poly = {0, 0, W / 2f, 0, W / 2f, H, 0, H};         // left half of the screen (patient's right in anterior view)
        Seg.IntList cutL = Seg.cut(v, st.cam, poly, W, H, true);
        boolean allRight = true;
        double[] half = v.half();
        for (int c = 0; c < cutL.size; c += 97) { int x = cutL.a[c] % NX; if (x >= NX / 2 + 16) { allRight = false; break; } }
        Seg.Edit e3 = Seg.set(v, "Cut", cutL, true, 0);
        check("scalpel removes what's inside the drawn outline (patient's right side in front view)", allRight && cutL.size > v.n() / 5, cutL.size + " voxels");
        Seg.undo(v, e3);
        check("scalpel undo", java.util.Arrays.equals(before, v.labels), "");

        // Saved state round trip
        st.name = "Coronary review"; st.seriesUid = "1.2.3"; st.cam.rotate(0.6, -0.3); st.cls[Seg.BONE].visible = false; st.clipHi[2] = 0.8f; st.mode = VrtState.VRT;
        VrtState back = VrtState.fromJson(new JSONObject(st.json().toString()));
        check("saved state restores camera, classes, clip, and parameters", back.name.equals("Coronary review") && Math.abs(back.cam.R[4] - st.cam.R[4]) < 1e-9
                && !back.cls[Seg.BONE].visible && back.clipHi[2] == 0.8f && Math.abs(back.params.vessel - p.vessel) < 1e-9
                && java.util.Arrays.equals(back.table(-1024, 3071), st.table(-1024, 3071)), "");

        // CPU renders, exported for the GPU comparison
        JSONArray cases = new JSONArray();
        Object[][] views = {
                {"vrt_anterior", VrtState.VRT, 0.0, 0.0, 0},
                {"vrt_oblique", VrtState.VRT, 0.7, -0.35, 0},
                {"mip_anterior", VrtState.MIP, 0.0, 0.0, 0},
                {"surface_left", VrtState.SURFACE, Math.PI / 2, 0.0, 0},
                {"vrt_clipped", VrtState.VRT, 0.3, 0.0, 1}};
        int rw = 160, rh = 160;
        for (Object[] vw : views) {
            VrtState s = new VrtState();
            s.defaults(true, p.vessel);
            s.preset(0, true);
            s.cls[Seg.BONE].visible = true;
            s.mode = (Integer) vw[1];
            s.winC = 300; s.winW = 900;
            s.cam.rotate((Double) vw[2], (Double) vw[3]);
            if ((Integer) vw[4] == 1) { s.clipLo[1] = 0.25f; s.clipHi[2] = 0.7f; }
            if (s.mode == VrtState.SURFACE) s.cls[Seg.BONE].visible = true;
            long r0 = System.nanoTime();
            int[] img = new CpuVrt(v, s).render(rw, rh, 1.0);
            long rms = (System.nanoTime() - r0) / 1000000;
            String name = (String) vw[0];
            try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(out, "cpu_" + name + ".bin"))))) {
                ByteBuffer bb = ByteBuffer.allocate(img.length * 4).order(ByteOrder.LITTLE_ENDIAN);
                for (int px : img) bb.putInt(px);
                o.write(bb.array());
            }
            JSONObject c = new JSONObject();
            c.put("name", name); c.put("w", rw); c.put("h", rh); c.put("mode", s.mode); c.put("vis", s.visMask());
            c.put("toModel", new JSONArray(toD(s.cam.toModel()))); c.put("zoom", s.cam.zoom); c.put("panX", s.cam.panX); c.put("panY", s.cam.panY);
            c.put("clipLo", new JSONArray(toD(s.clipLo))); c.put("clipHi", new JSONArray(toD(s.clipHi)));
            c.put("ka", s.ka); c.put("kd", s.kd); c.put("ks", s.ks); c.put("shin", s.shin); c.put("surface", s.surface);
            c.put("winC", s.winC); c.put("winW", s.winW); c.put("stepScale", 1.0);
            c.put("tf", "tf_" + name + ".bin");
            try (FileOutputStream o = new FileOutputStream(new File(out, "tf_" + name + ".bin"))) { o.write(s.table(v.huMin, v.huMax)); }
            int nonBg = 0; for (int px : img) if ((px & 0xFFFFFF) != 0) nonBg++;
            check("CPU render " + name + " shows the anatomy", nonBg > rw * rh / 10, nonBg + " lit pixels, " + rms + " ms");
            cases.put(c);
        }
        JSONObject meta = new JSONObject();
        meta.put("nx", NX); meta.put("ny", NY); meta.put("nz", NZ);
        meta.put("half", new JSONArray(half)); meta.put("huMin", v.huMin); meta.put("huMax", v.huMax);
        meta.put("cases", cases);
        try (FileOutputStream o = new FileOutputStream(new File(out, "meta.json"))) { o.write(meta.toString(1).getBytes("UTF-8")); }
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(out, "vol.bin"))))) {
            ByteBuffer bb = ByteBuffer.allocate(v.n() * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : v.hu) bb.putShort(s);
            o.write(bb.array());
        }
        try (FileOutputStream o = new FileOutputStream(new File(out, "lab.bin"))) { o.write(v.labels); }

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }

    static double[] toD(float[] f) { double[] d = new double[f.length]; for (int i = 0; i < f.length; i++) d[i] = f[i]; return d; }
}
