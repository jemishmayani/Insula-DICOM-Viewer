/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Windowing correctness and speed, preview rendering, stride reslicing, and concurrent slice loading. */
public class SmoothViewingTest {
    static int fails = 0;
    static void check(String n, boolean ok, String d) { System.out.println((ok ? "PASS " : "FAIL ") + n + "  " + d); if (!ok) fails++; }

    static RawImage ramp(int w, int h) {
        RawImage r = new RawImage(); r.w = w; r.h = h; r.pix = new int[w * h];
        Random rnd = new Random(3);
        for (int i = 0; i < r.pix.length; i++) r.pix[i] = -1024 + rnd.nextInt(4096);
        r.slope = 1; r.intercept = 0; r.computeRange();
        return r;
    }

    /** The single-threaded reference implementation the app used before. */
    static void reference(RawImage im, int[] out, double c, double w) {
        double ww2 = Math.max(1, w), lo = c - 0.5 - (ww2 - 1) / 2, hi = c - 0.5 + (ww2 - 1) / 2;
        for (int i = 0; i < im.pix.length; i++) out[i] = RawImage.gray(im.pix[i] * im.slope + im.intercept, c, ww2, lo, hi, im.mono1);
    }

    /** What version 1.6 did: a lookup table, then one thread mapping every pixel. */
    static void previous(RawImage im, int[] out, double c, double w) {
        double ww2 = Math.max(1, w), lo = c - 0.5 - (ww2 - 1) / 2, hi = c - 0.5 + (ww2 - 1) / 2;
        int[] lut = new int[im.max - im.min + 1];
        for (int i = 0; i < lut.length; i++) lut[i] = RawImage.gray((im.min + i) * im.slope + im.intercept, c, ww2, lo, hi, im.mono1);
        for (int i = 0; i < im.pix.length; i++) out[i] = lut[im.pix[i] - im.min];
    }

    public static void main(String[] a) throws Exception {
        // 1. Windowing: parallel output identical to the reference, and timing
        RawImage ct = ramp(512, 512), cr = ramp(3000, 3000);
        int[] o1 = new int[ct.pix.length], o2 = new int[ct.pix.length];
        reference(ct, o1, 40, 400); ct.render(o2, 40, 400, false);
        check("windowing matches the reference (512x512)", Arrays.equals(o1, o2), "");
        int[] big1 = new int[cr.pix.length], big2 = new int[cr.pix.length];
        reference(cr, big1, 300, 1500); cr.render(big2, 300, 1500, false);
        check("windowing matches the reference (3000x3000)", Arrays.equals(big1, big2), "");
        long t0 = System.nanoTime(); for (int i = 0; i < 20; i++) previous(cr, big1, 300 + i, 1500); long ref = (System.nanoTime() - t0) / 20;
        t0 = System.nanoTime(); for (int i = 0; i < 20; i++) cr.render(big2, 300 + i, 1500, false); long par = (System.nanoTime() - t0) / 20;
        int pw = 3000 / 2; int[] prev = new int[pw * pw];
        t0 = System.nanoTime(); for (int i = 0; i < 20; i++) cr.render(prev, 300 + i, 1500, false, 2); long pre = (System.nanoTime() - t0) / 20;
        System.out.printf("     3000x3000 window update: v1.6 %.1f ms, now %.1f ms, while dragging %.1f ms (JVM reports %d cores)%n", ref / 1e6, par / 1e6, pre / 1e6, Runtime.getRuntime().availableProcessors());
        boolean sampled = true;
        for (int y = 0; y < pw && sampled; y += 97) for (int x = 0; x < pw; x += 89) if (prev[y * pw + x] != big2[(y * 2) * 3000 + x * 2]) { sampled = false; break; }
        check("drag preview samples the full-resolution result", sampled, "");

        // 2. Stride reslice keeps size and matches full quality at sampled points
        Volume v = MprPhantomTest.make(new double[]{-100, -100, -40}, new double[]{1, 0, 0}, new double[]{0, 1, 0}, new double[]{0, 0, 2.5}, 201, 201, 41);
        Volume.Plane pl = v.planeThrough(new double[]{10, -5, 15}, new double[]{1, 0, 0}, new double[]{0, 0, -1}, 512);
        RawImage full = v.reslice(pl, 0, Volume.THIN, 1), fast = v.reslice(pl, 0, Volume.THIN, 2);
        boolean same = full.w == fast.w && full.h == fast.h && full.rowSp == fast.rowSp;
        for (int y = 0; y < full.h && same; y += 2) for (int x = 0; x < full.w; x += 2) if (full.pix[y * full.w + x] != fast.pix[y * full.w + x]) { same = false; break; }
        check("interactive MPR preview keeps size, spacing, and sampled values", same, full.w + "x" + full.h);
        // Best of several runs: the minimum reflects the work done, not scheduling noise on a busy machine.
        long tFull = Long.MAX_VALUE, tFast = Long.MAX_VALUE;
        for (int i = 0; i < 12; i++) {
            t0 = System.nanoTime(); v.reslice(pl, 20, Volume.MIP, 1); tFull = Math.min(tFull, System.nanoTime() - t0);
            t0 = System.nanoTime(); v.reslice(pl, 20, Volume.MIP, 2); tFast = Math.min(tFast, System.nanoTime() - t0);
        }
        System.out.printf("     20 mm MIP reslice: full %.1f ms, while dragging %.1f ms%n", tFull / 1e6, tFast / 1e6);
        check("MPR preview is at least 2.5x faster", tFull > 2.5 * tFast, "");

        // 3. Concurrent loads of one slice decode it once and return the same image
        Library.dir = new File(System.getProperty("work", "tests/.work") + "/smooth-lib");
        Library.dir.mkdirs();
        DemoStudy.create(null);
        Library.Series se = Library.study(DemoStudy.studyUid(1)).series.get(0);
        final Library.SliceRef slice = se.slices().get(20);
        Library.clearCache();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<RawImage>> fs = new ArrayList<>();
        for (int i = 0; i < 8; i++) fs.add(pool.submit(new Callable<RawImage>() { public RawImage call() throws Exception { return Library.load(slice); } }));
        Set<RawImage> distinct = Collections.newSetFromMap(new IdentityHashMap<RawImage, Boolean>());
        for (Future<RawImage> f : fs) distinct.add(f.get());
        pool.shutdown();
        check("8 threads loading one slice share one decode", distinct.size() == 1, "distinct=" + distinct.size());
        check("peek finds a loaded slice without blocking", Library.peek(slice) != null, "");
        final CountDownLatch done = new CountDownLatch(1);
        final RawImage[] got = new RawImage[1];
        Library.loadAsync(se.slices().get(30), new Library.Done() { public void done(RawImage r, Throwable e) { got[0] = r; done.countDown(); } });
        check("background load delivers the image", done.await(10, TimeUnit.SECONDS) && got[0] != null && got[0].w == DemoStudy.SIZE, "");
        Library.clearCache();
        t0 = System.nanoTime();
        Library.prefetch(se.slices(), 10, 1);
        int warm = 0;
        for (int i = 0; i < 200 && warm < 16; i++) { Thread.sleep(10); warm = 0; for (int k = 11; k <= 26; k++) if (Library.peek(se.slices().get(k)) != null) warm++; }
        check("prefetch warms the next 16 slices in the scroll direction", warm == 16, warm + "/16 after " + (System.nanoTime() - t0) / 1000000 + " ms");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
