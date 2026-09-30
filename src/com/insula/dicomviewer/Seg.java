/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based tissue separation for 3D rendering, plus the manual edits users make afterwards.
 * CT: thresholds are estimated from each study's histogram. Bone is cortical bone plus what it encloses in the axial
 * cross-section (marrow), so vessels pressed against bone stay vessels; small isolated dense spots are calcium; remaining
 * contrast-dense tissue is vessel.
 * Other modalities use intensity bands. It separates tissue types, not individual organs.
 */
final class Seg {
    static final int BG = 0, SKIN = 1, ORGAN = 2, VESSEL = 3, BONE = 4, LUNG = 5, CALCIUM = 6, SELECT = 7;
    static final int REMOVED = 0x80;
    // Temporary codes used only while segmenting
    static final byte OUT = 100, DENSE = 101, CORE = 102;
    /** Layers (through dense tissue) around bone that go to bone unless a vessel reaches them first. */
    static int BAND = 3;

    static final class Params {
        double air = -400, fat = -40, vessel = 200, boneSeed = 650, calcium = 800;
        double softPeak = Double.NaN, bloodPeak = Double.NaN;
        boolean contrast, ct = true;
        String summary = "";
    }

    interface Progress { void update(String msg, int pct); }

    // ---------------- threshold estimation ----------------
    static Params estimate(Vol3D v) {
        Params p = new Params();
        p.ct = v.ct;
        int n = v.n(), stride = Math.max(1, n / 2_000_000);
        if (!v.ct) {
            // Intensity bands for MR and other modalities: background by Otsu, bright structures at the 97th percentile.
            int lo = v.huMin, hi = v.huMax, bins = 512;
            long[] h = new long[bins];
            for (int i = 0; i < n; i += stride) h[Math.min(bins - 1, Math.max(0, (int) ((v.hu[i] - lo) * (long) bins / Math.max(1, hi - lo + 1))))]++;
            int otsu = otsu(h);
            double tOtsu = lo + (otsu + 0.5) * (hi - lo) / bins;
            long above = 0; for (int b = otsu; b < bins; b++) above += h[b];
            long acc = 0; int p97 = bins - 1;
            for (int b = bins - 1; b >= otsu; b--) { acc += h[b]; if (acc >= above * 0.03) { p97 = b; break; } }
            p.air = tOtsu;
            p.fat = tOtsu;
            p.vessel = lo + (p97 + 0.5) * (hi - lo) / bins;
            p.boneSeed = Double.MAX_VALUE;
            p.calcium = Double.MAX_VALUE;
            p.summary = String.format("Intensity bands: tissue above %.0f, bright structures above %.0f", p.air, p.vessel);
            return p;
        }
        // CT histogram of body tissue, 5 HU bins from -200 to 1300
        int base = -200, bins = 300;
        long[] h = new long[bins];
        long total = 0;
        for (int i = 0; i < n; i += stride) {
            int x = v.hu[i];
            if (x < base || x >= base + bins * 5) continue;
            h[(x - base) / 5]++; total++;
        }
        long[] s = smooth(h);
        int soft = argmax(s, bin(-20), bin(100)), blood = argmax(s, bin(150), bin(650));
        int valley = argmin(s, soft, blood);
        p.softPeak = base + soft * 5 + 2.5;
        p.bloodPeak = base + blood * 5 + 2.5;
        p.contrast = s[blood] > 1.6 * Math.max(1, s[valley]) && s[blood] > total * 0.0015 && blood > soft;
        if (p.contrast) {
            // Between the peaks (not at the valley, which partial-volume blur fills in): 45% of the way to the blood pool.
            p.vessel = clamp(p.softPeak + 0.45 * (p.bloodPeak - p.softPeak), 130, 300);
            p.boneSeed = Math.max(650, p.bloodPeak + 250);
            p.calcium = Math.max(p.boneSeed, p.bloodPeak + 350);
            p.summary = String.format("Contrast detected: blood pool about %.0f HU, soft tissue about %.0f HU. Vessels from %.0f HU, bone cores from %.0f HU.", p.bloodPeak, p.softPeak, p.vessel, p.boneSeed);
        } else {
            p.vessel = 300;
            p.boneSeed = 500;
            p.calcium = 600;
            p.summary = "No contrast enhancement detected: dense structures are treated as bone. Adjust the thresholds if this is a contrast study.";
        }
        return p;
    }

    static int bin(int hu) { return (hu + 200) / 5; }
    static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
    static long[] smooth(long[] h) {
        long[] s = new long[h.length];
        for (int i = 0; i < h.length; i++) { long a = 0; int c = 0; for (int k = -3; k <= 3; k++) if (i + k >= 0 && i + k < h.length) { a += h[i + k]; c++; } s[i] = a / c; }
        return s;
    }
    static int argmax(long[] s, int a, int b) { int m = a; for (int i = a; i <= b && i < s.length; i++) if (s[i] > s[m]) m = i; return m; }
    static int argmin(long[] s, int a, int b) { int m = a; for (int i = a; i <= b && i < s.length; i++) if (s[i] < s[m]) m = i; return m; }

    static int otsu(long[] h) {
        long total = 0; double sum = 0;
        for (int i = 0; i < h.length; i++) { total += h[i]; sum += i * (double) h[i]; }
        double sumB = 0, best = -1; long wB = 0; int t = 0;
        for (int i = 0; i < h.length; i++) {
            wB += h[i]; if (wB == 0) continue;
            long wF = total - wB; if (wF == 0) break;
            sumB += i * (double) h[i];
            double mB = sumB / wB, mF = (sum - sumB) / wF, between = (double) wB * wF * (mB - mF) * (mB - mF);
            if (between > best) { best = between; t = i; }
        }
        return t;
    }

    // ---------------- automatic separation ----------------
    static void segment(Vol3D v, Params p, Progress prog) {
        final byte[] L = v.labels;
        final short[] H = v.hu;
        final int nx = v.nx, ny = v.ny, nz = v.nz, n = v.n(), sxy = nx * ny;
        java.util.Arrays.fill(L, (byte) 0);
        if (prog != null) prog.update("Finding the body outline", 5);
        // 1. Air outside the body: flood from the volume's faces through voxels below the air threshold.
        IntQueue q = new IntQueue(1 << 16);
        for (int k = 0; k < nz; k++) for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            // Side faces only: lungs and airways often reach the top or bottom of a chest scan.
            if (i != 0 && j != 0 && i != nx - 1 && j != ny - 1) continue;
            int x = (k * ny + j) * nx + i;
            if (H[x] < p.air && L[x] == 0) { L[x] = OUT; q.add(x); }
        }
        while (!q.isEmpty()) {
            int x = q.poll(), i = x % nx, j = (x / nx) % ny, k = x / sxy;
            if (i > 0) visitOut(L, H, x - 1, p.air, q);
            if (i < nx - 1) visitOut(L, H, x + 1, p.air, q);
            if (j > 0) visitOut(L, H, x - nx, p.air, q);
            if (j < ny - 1) visitOut(L, H, x + nx, p.air, q);
            if (k > 0) visitOut(L, H, x - sxy, p.air, q);
            if (k < nz - 1) visitOut(L, H, x + sxy, p.air, q);
        }
        if (prog != null) prog.update("Separating lungs, fat, and soft tissue", 25);
        // 2. Simple classes inside the body
        for (int x = 0; x < n; x++) {
            if (L[x] == OUT) continue;
            int hv = H[x];
            if (hv < p.air) L[x] = (byte) (p.ct ? LUNG : BG);
            else if (hv < p.fat) L[x] = (byte) SKIN;
            else if (hv < p.vessel) L[x] = (byte) ORGAN;
            else L[x] = DENSE;
        }
        if (prog != null) prog.update("Separating bone from vessels", 45);
        // 3. Cortical bone: very dense voxels. Small isolated dense spots are calcifications, not bone.
        double voxMm3 = v.sx * v.sy * v.sz;
        IntList comp = new IntList(1 << 12);
        for (int start = 0; start < n; start++) {
            if (L[start] != DENSE || H[start] < p.boneSeed) continue;
            comp.clear();
            L[start] = CORE; q.add(start);
            while (!q.isEmpty()) {
                int x = q.poll();
                comp.add(x);
                int i = x % nx, j = (x / nx) % ny, k = x / sxy;
                int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
                for (int y : nb) if (y >= 0 && L[y] == DENSE && H[y] >= p.boneSeed) { L[y] = CORE; q.add(y); }
            }
            // Calcifications are compact (under 15 mm across and 1 cm3); bones are extended.
            int[] lo = {nx, ny, nz}, hi = {-1, -1, -1};
            for (int c = 0; c < comp.size; c++) {
                int x = comp.a[c], ci = x % nx, cj = (x / nx) % ny, ck = x / sxy;
                lo[0] = Math.min(lo[0], ci); hi[0] = Math.max(hi[0], ci);
                lo[1] = Math.min(lo[1], cj); hi[1] = Math.max(hi[1], cj);
                lo[2] = Math.min(lo[2], ck); hi[2] = Math.max(hi[2], ck);
            }
            double extent = Math.max((hi[0] - lo[0] + 1) * v.sx, Math.max((hi[1] - lo[1] + 1) * v.sy, (hi[2] - lo[2] + 1) * v.sz));
            double mm3 = comp.size * voxMm3;
            byte cls = (byte) (mm3 < 60 || (mm3 < 1000 && extent < 15) ? CALCIUM : BONE);
            for (int c = 0; c < comp.size; c++) L[comp.a[c]] = cls;
        }
        if (prog != null) prog.update("Filling bone marrow", 60);
        // 4. Marrow: dense voxels enclosed by cortical bone in the axial cross-section. The cortex is thickened by one
        //    voxel first so small gaps don't open the ring. A vessel pressed against bone is not enclosed, so it stays a vessel.
        byte[] ring = new byte[sxy];
        IntQueue q2 = new IntQueue(1 << 12);
        for (int k = 0; k < nz; k++) {
            int off = k * sxy;
            java.util.Arrays.fill(ring, (byte) 0);
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                if (L[off + j * nx + i] != BONE) continue;
                for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) {
                    int ii = i + di, jj = j + dj;
                    if (ii >= 0 && jj >= 0 && ii < nx && jj < ny) ring[jj * nx + ii] = 1;
                }
            }
            // flood the slice from its border through everything that isn't (thickened) cortex: unreached = enclosed
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                if (i != 0 && j != 0 && i != nx - 1 && j != ny - 1) continue;
                int x = j * nx + i;
                if (ring[x] == 0) { ring[x] = 2; q2.add(x); }
            }
            while (!q2.isEmpty()) {
                int x = q2.poll(), i = x % nx, j = x / nx;
                if (i > 0 && ring[x - 1] == 0) { ring[x - 1] = 2; q2.add(x - 1); }
                if (i < nx - 1 && ring[x + 1] == 0) { ring[x + 1] = 2; q2.add(x + 1); }
                if (j > 0 && ring[x - nx] == 0) { ring[x - nx] = 2; q2.add(x - nx); }
                if (j < ny - 1 && ring[x + nx] == 0) { ring[x + nx] = 2; q2.add(x + nx); }
            }
            for (int x = 0; x < sxy; x++) {
                if (L[off + x] != DENSE || ring[x] == 2) continue;
                // Enclosed marrow, or a thickened-cortex voxel with no contact to the outside region.
                int i = x % nx, j = x / nx;
                boolean outside = ring[x] == 1 && ((i > 0 && ring[x - 1] == 2) || (i < nx - 1 && ring[x + 1] == 2) || (j > 0 && ring[x - nx] == 2) || (j < ny - 1 && ring[x + nx] == 2));
                if (!outside) L[off + x] = BONE;
            }
        }
        if (prog != null) prog.update("Finding vessels", 80);
        // 5. Remaining dense tissue: bone's blurred edge or contrast-filled vessel. Competitive region growing decides:
        //    bone grows from the cortex, vessel grows from dense tissue more than 2 voxels (through dense tissue) from
        //    any bone, one layer at a time, and each voxel joins whichever front reaches it first. Thin bone edges are
        //    reached by bone first; a vessel pressed against bone loses only its single contact layer.
        byte[] dist = new byte[n];                         // geodesic layers from bone through dense tissue (0 = not reached)
        IntQueue fb = new IntQueue(1 << 14);
        for (int x = 0; x < n; x++) if (L[x] == BONE) fb.add(x);
        for (int layer = 1; layer <= BAND && !fb.isEmpty(); layer++) {
            IntQueue next = new IntQueue(1 << 14);
            while (!fb.isEmpty()) {
                int x = fb.poll(), i = x % nx, j = (x / nx) % ny, k = x / sxy;
                int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
                for (int y : nb) if (y >= 0 && L[y] == DENSE && dist[y] == 0) { dist[y] = (byte) layer; next.add(y); }
            }
            fb = next;
        }
        // Seeds: all bone, and dense voxels beyond the 2-layer band. Multi-source FIFO growth = nearest seed wins.
        IntQueue grow = new IntQueue(1 << 16);
        for (int x = 0; x < n; x++) {
            if (L[x] == BONE) grow.add(x);
            else if (L[x] == DENSE && dist[x] == 0) { L[x] = VESSEL; grow.add(x); }
        }
        while (!grow.isEmpty()) {
            int x = grow.poll(), i = x % nx, j = (x / nx) % ny, k = x / sxy;
            byte c = L[x];
            int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
            for (int y : nb) if (y >= 0 && L[y] == DENSE) { L[y] = c; grow.add(y); }
        }
        // Dense specks under 8 mm3 inside soft tissue are noise, not vessels.
        for (int start = 0; start < n; start++) {
            if (L[start] != VESSEL) continue;
            comp.clear();
            L[start] = CORE; q.add(start);
            while (!q.isEmpty()) {
                int x = q.poll();
                comp.add(x);
                int i = x % nx, j = (x / nx) % ny, k = x / sxy;
                int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
                for (int y : nb) if (y >= 0 && L[y] == VESSEL) { L[y] = CORE; q.add(y); }
            }
            byte cls = (byte) (comp.size * voxMm3 < 8 ? ORGAN : 0x70);   // 0x70: confirmed vessel (restored below)
            for (int c = 0; c < comp.size; c++) L[comp.a[c]] = cls;
        }
        for (int x = 0; x < n; x++) if (L[x] == 0x70) L[x] = VESSEL;
        for (int x = 0; x < n; x++) if (L[x] == OUT) L[x] = BG;
        if (prog != null) prog.update("Done", 100);
    }

    static boolean isDense(byte b) { return b == DENSE || b == CORE; }

    static void visitOut(byte[] L, short[] H, int y, double air, IntQueue q) {
        if (L[y] == 0 && H[y] < air) { L[y] = OUT; q.add(y); }
    }

    // ---------------- manual editing ----------------
    /** One undoable change: the voxels touched and their previous labels. */
    static final class Edit {
        final String what;
        final int[] idx;
        final byte[] old;
        Edit(String what, int[] idx, byte[] old) { this.what = what; this.idx = idx; this.old = old; }
        long bytes() { return idx.length * 5L; }
    }

    static void undo(Vol3D v, Edit e) { for (int c = 0; c < e.idx.length; c++) v.labels[e.idx[c]] = e.old[c]; }

    static Edit set(Vol3D v, String what, IntList idx, boolean remove, int cls) {
        int[] a = java.util.Arrays.copyOf(idx.a, idx.size);
        byte[] old = new byte[a.length];
        for (int c = 0; c < a.length; c++) {
            int x = a[c];
            old[c] = v.labels[x];
            v.labels[x] = (byte) (remove ? (v.labels[x] | REMOVED) : ((v.labels[x] & REMOVED) | cls));
        }
        return new Edit(what, a, old);
    }

    /** Connected voxels (6-neighbour) of the same visible class as the seed. */
    static IntList component(Vol3D v, int seed) {
        IntList out = new IntList(1 << 12);
        if (seed < 0 || (v.labels[seed] & REMOVED) != 0) return out;
        int cls = v.labels[seed], nx = v.nx, ny = v.ny, nz = v.nz, sxy = nx * ny;
        java.util.BitSet seen = new java.util.BitSet(v.n());
        IntQueue q = new IntQueue(1 << 14);
        q.add(seed); seen.set(seed);
        while (!q.isEmpty()) {
            int x = q.poll();
            out.add(x);
            int i = x % nx, j = (x / nx) % ny, k = x / sxy;
            int[] nb = {i > 0 ? x - 1 : -1, i < nx - 1 ? x + 1 : -1, j > 0 ? x - nx : -1, j < ny - 1 ? x + nx : -1, k > 0 ? x - sxy : -1, k < nz - 1 ? x + sxy : -1};
            for (int y : nb) if (y >= 0 && !seen.get(y) && v.labels[y] == cls) { seen.set(y); q.add(y); }
        }
        return out;
    }

    /** Everything of the component's class except the component itself (for "show only this"). */
    static IntList othersOfClass(Vol3D v, IntList comp, int cls) {
        java.util.BitSet in = new java.util.BitSet(v.n());
        for (int c = 0; c < comp.size; c++) in.set(comp.a[c]);
        IntList out = new IntList(1 << 12);
        for (int x = 0; x < v.n(); x++) if (v.labels[x] == cls && !in.get(x)) out.add(x);
        return out;
    }

    /** Voxels whose centre projects inside (or outside) a screen polygon, for the scalpel. */
    static IntList cut(final Vol3D v, final VrtState.Camera cam, final float[] poly, final int w, final int h, final boolean inside) {
        final IntList[] parts = new IntList[v.nz];
        final double[] half = v.half();
        Volume.parallelRows(v.nz, new Volume.RowTask() {
            public void rows(int k0, int k1) {
                float[] s = new float[2];
                for (int k = k0; k < k1; k++) {
                    IntList l = new IntList(256);
                    double z = ((k + 0.5) / v.nz) * 2 * half[2] - half[2];
                    for (int j = 0; j < v.ny; j++) {
                        double y = ((j + 0.5) / v.ny) * 2 * half[1] - half[1];
                        int base = (k * v.ny + j) * v.nx;
                        for (int i = 0; i < v.nx; i++) {
                            int x = base + i;
                            int lab = v.labels[x];
                            if ((lab & REMOVED) != 0 || lab == BG) continue;
                            double xx = ((i + 0.5) / v.nx) * 2 * half[0] - half[0];
                            cam.toScreen(xx, y, z, w, h, s);
                            if (pointInPolygon(s[0], s[1], poly) == inside) l.add(x);
                        }
                    }
                    parts[k] = l;
                }
            }
        });
        IntList all = new IntList(1 << 12);
        for (IntList l : parts) if (l != null) for (int c = 0; c < l.size; c++) all.add(l.a[c]);
        return all;
    }

    static boolean pointInPolygon(float x, float y, float[] p) {
        boolean in = false;
        int n = p.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = p[2 * i], yi = p[2 * i + 1], xj = p[2 * j], yj = p[2 * j + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi + 1e-12f) + xi) in = !in;
        }
        return in;
    }

    // ---------------- small collections without boxing ----------------
    static final class IntList {
        int[] a; int size;
        IntList(int cap) { a = new int[Math.max(4, cap)]; }
        void add(int x) { if (size == a.length) a = java.util.Arrays.copyOf(a, a.length * 2); a[size++] = x; }
        void clear() { size = 0; }
    }

    /** FIFO ring buffer that only grows to the size of the flood-fill frontier. */
    static final class IntQueue {
        int[] a; int head, tail, count;
        IntQueue(int cap) { a = new int[cap]; }
        boolean isEmpty() { return count == 0; }
        void add(int x) {
            if (count == a.length) {
                int[] b = new int[a.length * 2];
                for (int i = 0; i < count; i++) b[i] = a[(head + i) % a.length];
                a = b; head = 0; tail = count;
            }
            a[tail] = x; tail = (tail + 1) % a.length; count++;
        }
        int poll() { int x = a[head]; head = (head + 1) % a.length; count--; return x; }
    }

    static int classCount(Vol3D v, int cls) { int c = 0; for (byte b : v.labels) if (b == cls) c++; return c; }
}
